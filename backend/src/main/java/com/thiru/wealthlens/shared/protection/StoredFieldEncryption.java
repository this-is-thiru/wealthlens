package com.thiru.wealthlens.shared.protection;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Encrypts, in rows stored before W-248, W-253 and W-256, the fields they put under {@link EncryptedField} and
 * {@link SearchableIdentifier}, which would otherwise refuse those rows on read (D-479 C3a, D-485 C3a, D-486 C3a). Works on raw documents, so a converter never meets a plain
 * value. Each update is conditioned on the plain values it read still being there, so a second run, or one after a run that failed
 * part-way, encrypts only what is left. A row whose encrypted identifier would duplicate a unique index entry — one written between
 * the deploy and the run — is counted as a conflict and left as it is. Asked for by a {@code SUPER_USER}, never at startup (D-280).
 * One-time: removed once its run is logged for every database (W-254).
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class StoredFieldEncryption {

    /** A string not yet under a key; a regex matches only strings, so absent and non-text values are left alone. */
    private static final String PLAIN = "^(?!" + FieldCipher.KEY_ID + ")";
    private static final String LOGINS = "user_details";
    private static final String EMAIL = "email";

    /**
     * {@code policyDetails} carries no {@code @Field} on {@code InsuranceEntity}, nor do a temporary transaction's embedded
     * {@code AssetRequest} fields, so each is stored under its Java name.
     */
    private static final List<StoredFields> FIELDS = List.of(
            new StoredFields("privacy_grievances", List.of("text", "answer"), Map.of(), List.of(EMAIL)),
            new StoredFields("privacy_processing_log", List.of("remote_address"), Map.of(), List.of("principal", "subject_email")),
            new StoredFields("insurances",
                    List.of("insurance_id", "agent_name", "agent_email", "agent_contact", "agent_address", "notes"),
                    Map.of("policyDetails", List.of("policy_id", "agent_name", "agent_email", "agent_contact",
                            "agent_address", "notes", "policy_description")), List.of(EMAIL)),
            identifiers("transactions", EMAIL, "account_holder", "asset_request.email", "asset_request.accountHolder"),
            identifiers("assets", EMAIL, "account_holder"),
            identifiers("profit_and_loss", EMAIL),
            identifiers("trade_outcomes", EMAIL, "account_holder"),
            identifiers("user_charges", EMAIL, "account_holder"),
            identifiers("charge_accounts", EMAIL, "account_holder", "demat_account_id"),
            identifiers("lastly_performed_corporate_action", EMAIL),
            identifiers("salary_profiles", EMAIL),
            identifiers("tax_computations", EMAIL),
            identifiers("tax_profiles", EMAIL),
            identifiers("tax_profile_erasures", EMAIL),
            identifiers("tax_draft_computations", EMAIL),
            identifiers("tax_access_log", "principal"),
            identifiers("privacy_consents", EMAIL),
            identifiers("privacy_erasure_requests", EMAIL),
            identifiers("privacy_breach_intimations", EMAIL),
            identifiers("privacy_breach_notices", "affected"));

    /** {@link AuditMetadata}'s actors carry no {@code @Field}, so each is stored under its Java name. */
    private static final List<String> AUDIT_ACTORS = List.of("createdBy", "updatedBy");

    /** The actors stored outside {@link AuditMetadata} (W-256). */
    private static final Map<String, String> OWN_ACTORS = Map.of("tax_draft_revisions", "changed_by", "tax_gazette_cursor", "updated_by");

    private final MongoTemplate mongoTemplate;
    private final FieldCipher cipher;

    public StoredFieldEncryptionReport encrypt(boolean dryRun, String caller) {
        List<StoredFieldEncryptionReport.CollectionCount> counts = new ArrayList<>(
                storedFields().stream().map(fields -> encrypt(fields, dryRun)).toList());
        counts.add(rekeyLogins(dryRun));
        log.info("Stored-field encryption by {}, dry run {}: {}", caller, dryRun, counts);
        return new StoredFieldEncryptionReport(dryRun, caller, counts);
    }

    /**
     * {@link #FIELDS} with each row's actor added (W-256). The audited collections are read from the mapping rather than listed, so an
     * entity given {@link AuditMetadata} later is covered without an edit here.
     */
    private List<StoredFields> storedFields() {
        Map<String, List<String>> actors = new TreeMap<>();
        OWN_ACTORS.forEach((collection, field) -> actors.computeIfAbsent(collection, any -> new ArrayList<>()).add(field));
        for (MongoPersistentEntity<?> entity : mongoTemplate.getConverter().getMappingContext().getPersistentEntities()) {
            if (entity.isAnnotationPresent(org.springframework.data.mongodb.core.mapping.Document.class)) {
                entity.doWithProperties((PropertyHandler<MongoPersistentProperty>) property -> {
                    if (property.getType() == AuditMetadata.class) {
                        AUDIT_ACTORS.forEach(actor -> actors.computeIfAbsent(entity.getCollection(), any -> new ArrayList<>())
                                .add(property.getFieldName() + "." + actor));
                    }
                });
            }
        }
        List<StoredFields> stored = new ArrayList<>();
        for (StoredFields fields : FIELDS) {
            List<String> own = actors.remove(fields.collection());
            stored.add(own == null ? fields : fields.withIdentifiers(own));
        }
        actors.forEach((collection, paths) -> stored.add(identifiers(collection, paths.toArray(String[]::new))));
        return stored;
    }

    private StoredFieldEncryptionReport.CollectionCount encrypt(StoredFields fields, boolean dryRun) {
        int plain = 0;
        int encrypted = 0;
        int conflicts = 0;
        try (Stream<Document> rows = mongoTemplate.stream(holdingPlainText(fields), Document.class, fields.collection())) {
            for (Document row : (Iterable<Document>) rows::iterator) {
                Query unchanged = query(where("_id").is(row.get("_id")));
                Update update = new Update();
                if (!seal(fields, row, unchanged, update)) {
                    continue;
                }
                plain++;
                if (dryRun) {
                    continue;
                }
                try {
                    if (mongoTemplate.updateFirst(unchanged, update, fields.collection()).getModifiedCount() == 1) {
                        encrypted++;
                    }
                } catch (DuplicateKeyException e) {
                    log.warn("Row {} in {} left plain: its encrypted identifier duplicates a unique index entry", row.get("_id"),
                            fields.collection());
                    conflicts++;
                }
            }
        }
        return new StoredFieldEncryptionReport.CollectionCount(fields.collection(), plain, encrypted, conflicts);
    }

    /**
     * A login stored before W-253 holds its email as {@code _id} and no {@code email} field (C2a). The re-keyed row is inserted
     * first and the old one deleted after, so a run stopped between the two finds the new row on the next run and only deletes.
     */
    private StoredFieldEncryptionReport.CollectionCount rekeyLogins(boolean dryRun) {
        int plain = 0;
        int encrypted = 0;
        Query unkeyed = query(where(EMAIL).exists(false));
        try (Stream<Document> rows = mongoTemplate.stream(unkeyed, Document.class, LOGINS)) {
            for (Document row : (Iterable<Document>) rows::iterator) {
                if (!(row.get("_id") instanceof String email)) {
                    continue;
                }
                plain++;
                if (dryRun) {
                    continue;
                }
                String sealed = cipher.encryptSearchable(email);
                if (!mongoTemplate.exists(query(where(EMAIL).is(sealed)), LOGINS)) {
                    Document rekeyed = new Document(row);
                    rekeyed.put("_id", new ObjectId());
                    rekeyed.put(EMAIL, sealed);
                    mongoTemplate.insert(rekeyed, LOGINS);
                }
                if (mongoTemplate.remove(query(where("_id").is(email).and(EMAIL).exists(false)), LOGINS).getDeletedCount() == 1) {
                    encrypted++;
                }
            }
        }
        return new StoredFieldEncryptionReport.CollectionCount(LOGINS, plain, encrypted, 0);
    }

    private static Query holdingPlainText(StoredFields fields) {
        List<Criteria> any = new ArrayList<>(Stream.concat(fields.fields().stream(), fields.identifiers().stream())
                .map(field -> where(field).regex(PLAIN)).toList());
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
        for (String path : fields.identifiers()) {
            Object value = row.getEmbedded(Arrays.asList(path.split("\\.")), Object.class);
            Object sealedValue = sealIdentifier(value);
            if (value != null && !sealedValue.equals(value)) {
                unchanged.addCriteria(where(path).is(value));
                update.set(path, sealedValue);
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

    /** A plain identifier, or each plain one in a list of them, in its searchable form; anything else as it is. */
    private Object sealIdentifier(Object value) {
        if (value instanceof String text && !text.startsWith(FieldCipher.KEY_ID)) {
            return cipher.encryptSearchable(text);
        }
        if (value instanceof List<?> values) {
            return values.stream().map(this::sealIdentifier).toList();
        }
        return value;
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

    private static StoredFields identifiers(String collection, String... paths) {
        return new StoredFields(collection, List.of(), Map.of(), List.of(paths));
    }

    /**
     * A collection's encrypted fields: top-level, inside each element of a list of sub-documents, and the identifiers, which take the
     * searchable form and may be dotted paths into a sub-document or name a list of identifiers.
     */
    private record StoredFields(String collection, List<String> fields, Map<String, List<String>> listFields, List<String> identifiers) {

        StoredFields withIdentifiers(List<String> more) {
            return new StoredFields(collection, fields, listFields, Stream.concat(identifiers.stream(), more.stream()).toList());
        }
    }
}
