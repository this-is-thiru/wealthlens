package com.thiru.wealthlens.shared.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Copies the MongoDB store into PostgreSQL once, for the move (W-337g, D-928 C3a, D-953): ciphertext and keyed digests carried
 * as they are, every source prepared and checked before anything is written, every row written by key so a second run
 * changes nothing it wrote before, and every copy proven three ways — the keys on both sides, each row read back against the
 * row mapped from the source, and each row loaded through the application's own entity. Not {@code @Transactional}: each
 * row is one statement and so its own transaction, which is what lets a run that failed part-way be finished by the next.
 * Removed with MongoDB (W-337j).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MongoCopier {

    private static final int PROBLEMS_SHOWN = 5;
    private static final String DOCUMENT_BYTES_TABLE = "tax_document_bytes";

    private final MongoTemplate mongoTemplate;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;

    /**
     * @throws IllegalStateException if a collection the copier knows nothing of holds a document: copying around it would
     *     leave its data behind without a word
     */
    public void requireKnownSource() {
        MongoDatabase database = mongoTemplate.getDb();
        Set<String> unknown = new TreeSet<>();
        for (String name : database.listCollectionNames()) {
            boolean known = name.startsWith("system.") || CollectionCopies.SOURCE.contains(name)
                    || CollectionCopies.DOCUMENT_BYTES_COLLECTIONS.contains(name);
            if (!known && database.getCollection(name).find().limit(1).first() != null) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("The source holds collections the copier does not know: " + unknown + "; nothing was copied");
        }
    }

    /** Copies every given collection and the document bytes, then checks each; a refused source stops it before any write. */
    public List<CopyReport> copyAll(List<CollectionCopy> copies, ZoneId writtenZone) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<PreparedCopy> prepared = new ArrayList<>();
        copies.forEach(copy -> prepared.add(prepare(jdbc, copy, writtenZone)));
        prepared.add(prepareDocumentBytes(jdbc));
        for (PreparedCopy each : prepared) {
            each.rows().forEach(row -> each.table().insert(jdbc, row));
            log.info("Copied {} rows of {} to {}", each.rows().size(), each.source(), each.table().name());
        }
        return prepared.stream().map(each -> verify(jdbc, each)).toList();
    }

    private PreparedCopy prepare(JdbcTemplate jdbc, CollectionCopy copy, ZoneId writtenZone) {
        TargetTable table = TargetTable.read(jdbc, copy.table());
        List<SourceRow> rows = new ArrayList<>();
        for (Document document : mongoTemplate.getCollection(copy.collection()).find()) {
            rows.add(RowMapping.of(document, copy, table, writtenZone));
        }
        return checked(jdbc, new PreparedCopy(copy.collection(), table, rows));
    }

    /** The GridFS bucket's files, each already sealed by {@code FieldCipher.encryptBytes}, as one row each. */
    private PreparedCopy prepareDocumentBytes(JdbcTemplate jdbc) {
        TargetTable table = TargetTable.read(jdbc, DOCUMENT_BYTES_TABLE);
        GridFSBucket bucket = GridFSBuckets.create(mongoTemplate.getDb(), CollectionCopies.DOCUMENT_BYTES_BUCKET);
        List<SourceRow> rows = new ArrayList<>();
        for (GridFSFile file : bucket.find()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bucket.downloadToStream(file.getObjectId(), bytes);
            rows.add(new SourceRow(file.getFilename(), Map.of("document_id", file.getFilename(), "bytes", bytes.toByteArray(),
                    "uploaded_at", LocalDateTime.ofInstant(file.getUploadDate().toInstant(), ZoneOffset.UTC))));
        }
        return checked(jdbc, new PreparedCopy(CollectionCopies.DOCUMENT_BYTES_BUCKET + " (GridFS)", table, rows));
    }

    /** Refuses a source with a key twice, and a target holding a row the source lacks (D-953 C3a: the target is fresh). */
    private static PreparedCopy checked(JdbcTemplate jdbc, PreparedCopy prepared) {
        Set<String> ids = new TreeSet<>();
        for (SourceRow row : prepared.rows()) {
            if (!ids.add(row.id())) {
                throw new IllegalStateException(prepared.source() + " holds the key " + row.id() + " twice; nothing was copied");
            }
        }
        Set<String> stray = new TreeSet<>(prepared.table().ids(jdbc));
        stray.removeAll(ids);
        if (!stray.isEmpty()) {
            throw new IllegalStateException(prepared.table().name() + " holds rows the source lacks: " + first(stray)
                    + "; nothing was copied");
        }
        return prepared;
    }

    CopyReport verify(JdbcTemplate jdbc, PreparedCopy prepared) {
        Map<String, Map<String, Object>> target = prepared.table().rows(jdbc);
        List<String> problems = new ArrayList<>();
        Set<String> missing = prepared.rows().stream().map(SourceRow::id).filter(id -> !target.containsKey(id))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) {
            problems.add("missing from the target: " + first(missing));
        }
        if (target.size() != prepared.rows().size()) {
            problems.add(prepared.rows().size() + " source rows but " + target.size() + " target rows");
        }
        String sourceDigest = digest(prepared.rows(), SourceRow::values);
        String targetDigest = digest(prepared.rows(), row -> target.getOrDefault(row.id(), Map.of()));
        if (!sourceDigest.equals(targetDigest)) {
            problems.add("rows differ from the source: " + first(differing(prepared.rows(), target)));
        }
                readBack(prepared.table().name(), target.size()).ifPresent(problems::add);
        return new CopyReport(prepared.source(), prepared.table().name(), prepared.rows().size(), target.size(), sourceDigest, problems);
    }

    private static Set<String> differing(List<SourceRow> rows, Map<String, Map<String, Object>> target) {
        return rows.stream().filter(row -> target.containsKey(row.id()))
                .filter(row -> !canonical(row.values(), row.values()).equals(canonical(row.values(), target.get(row.id()))))
                .map(SourceRow::id).collect(Collectors.toCollection(TreeSet::new));
    }

    /** SHA-256 over each row's mapped columns, rows ordered by key: only the columns the source filled are compared. */
    private static String digest(List<SourceRow> rows, Function<SourceRow, Map<String, Object>> side) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("Every Java platform ships SHA-256", absent);
        }
        rows.stream().sorted(Comparator.comparing(SourceRow::id))
                .forEach(row -> sha256.update((row.id() + canonical(row.values(), side.apply(row)) + "\n").getBytes(StandardCharsets.UTF_8)));
        return HexFormat.of().formatHex(sha256.digest());
    }

    private static String canonical(Map<String, Object> columns, Map<String, Object> values) {
        return new TreeSet<>(columns.keySet()).stream().map(column -> column + "=" + ColumnValue.canonical(values.get(column)))
                .collect(Collectors.joining("|", "{", "}"));
    }

    /**
     * Loads every row of the table through the application's entity for it: proves each encrypted column decrypts, each value
     * converts and no NULL sits where the entity holds a primitive.
     */
    private Optional<String> readBack(String table, int targetRows) {
        EntityType<?> entity = entityManagerFactory.getMetamodel().getEntities().stream()
                .filter(type -> type.getJavaType().isAnnotationPresent(Table.class)
                        && type.getJavaType().getAnnotation(Table.class).name().equals(table))
                .findFirst().orElseThrow(() -> new IllegalStateException("No entity is stored in " + table));
        try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
            int loaded = entityManager.createQuery("SELECT e FROM " + entity.getName() + " e", entity.getJavaType()).getResultList().size();
            return loaded == targetRows ? Optional.empty()
                    : Optional.of(entity.getName() + " loads " + loaded + " of " + targetRows + " rows");
        } catch (PersistenceException unreadable) {
            return Optional.of(entity.getName() + " cannot load the copied rows: " + unreadable.getMessage());
        }
    }

    private static String first(Set<String> ids) {
        return ids.stream().limit(PROBLEMS_SHOWN).collect(Collectors.joining(", ")) + (ids.size() > PROBLEMS_SHOWN ? ", …" : "");
    }
}
