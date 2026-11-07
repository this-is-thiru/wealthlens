package com.thiru.wealthlens.shared.protection;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Encrypts, in rows stored before W-248, the fields it put under {@link EncryptedField}, which would otherwise refuse
 * those rows on read (D-479 C3a). Works on raw documents, so the converter never meets a plain value. Each update is
 * conditioned on the plain values it read still being there, so a second run, or one after a run that failed part-way,
 * encrypts only what is left. Asked for by a {@code SUPER_USER}, never at startup (D-280). One-time: removed once its
 * run is logged for every database (W-254).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class StoredFieldEncryption {

    /** A string not yet under a key; a regex matches only strings, so absent and non-text values are left alone. */
    private static final String PLAIN = "^(?!" + FieldCipher.KEY_ID + ")";

    /** {@code policyDetails} carries no {@code @Field} on {@code InsuranceEntity}, so it is stored under its Java name. */
    private static final List<StoredFields> FIELDS = List.of(
            new StoredFields("privacy_grievances", List.of("text", "answer"), Map.of()),
            new StoredFields("privacy_processing_log", List.of("remote_address"), Map.of()),
            new StoredFields("insurances",
                    List.of("insurance_id", "agent_name", "agent_email", "agent_contact", "agent_address", "notes"),
                    Map.of("policyDetails", List.of("policy_id", "agent_name", "agent_email", "agent_contact",
                            "agent_address", "notes", "policy_description"))));

    private final MongoTemplate mongoTemplate;
    private final FieldCipher cipher;

    public StoredFieldEncryptionReport encrypt(boolean dryRun, String caller) {
        List<StoredFieldEncryptionReport.CollectionCount> counts = FIELDS.stream().map(fields -> encrypt(fields, dryRun)).toList();
        log.info("Stored-field encryption by {}, dry run {}: {}", caller, dryRun, counts);
        return new StoredFieldEncryptionReport(dryRun, caller, counts);
    }

    private StoredFieldEncryptionReport.CollectionCount encrypt(StoredFields fields, boolean dryRun) {
        int plain = 0;
        int encrypted = 0;
        try (Stream<Document> rows = mongoTemplate.stream(holdingPlainText(fields), Document.class, fields.collection())) {
            for (Document row : (Iterable<Document>) rows::iterator) {
                Query unchanged = query(where("_id").is(row.get("_id")));
                Update update = new Update();
                if (!seal(fields, row, unchanged, update)) {
                    continue;
                }
                plain++;
                if (!dryRun && mongoTemplate.updateFirst(unchanged, update, fields.collection()).getModifiedCount() == 1) {
                    encrypted++;
                }
            }
        }
        return new StoredFieldEncryptionReport.CollectionCount(fields.collection(), plain, encrypted);
    }

    private static Query holdingPlainText(StoredFields fields) {
        List<Criteria> any = new ArrayList<>(fields.fields().stream().map(field -> where(field).regex(PLAIN)).toList());
        fields.listFields().forEach((list, inner) -> any.add(where(list).elemMatch(
                new Criteria().orOperator(inner.stream().map(field -> where(field).regex(PLAIN)).toList()))));
        return query(new Criteria().orOperator(any));
    }

    /** Fills {@code update} with the encrypted values and {@code unchanged} with the plain ones; false when none is plain. */
    private boolean seal(StoredFields fields, Document row, Query unchanged, Update update) {
        boolean sealed = false;
        for (String field : fields.fields()) {
            if (row.get(field) instanceof String text && !text.startsWith(FieldCipher.KEY_ID)) {
                unchanged.addCriteria(where(field).is(text));
                update.set(field, cipher.encrypt(text));
                sealed = true;
            }
        }
        for (Map.Entry<String, List<String>> list : fields.listFields().entrySet()) {
            List<Document> items = row.getList(list.getKey(), Document.class, List.of());
            List<Document> sealedItems = items.stream().map(item -> seal(item, list.getValue())).toList();
            if (!sealedItems.equals(items)) {
                unchanged.addCriteria(where(list.getKey()).is(items));
                update.set(list.getKey(), sealedItems);
                sealed = true;
            }
        }
        return sealed;
    }

    private Document seal(Document item, List<String> fields) {
        Document sealed = new Document(item);
        for (String field : fields) {
            if (item.get(field) instanceof String text && !text.startsWith(FieldCipher.KEY_ID)) {
                sealed.put(field, cipher.encrypt(text));
            }
        }
        return sealed;
    }

    /** A collection's encrypted fields, top-level and inside each element of a list of sub-documents. */
    private record StoredFields(String collection, List<String> fields, Map<String, List<String>> listFields) {
    }
}
