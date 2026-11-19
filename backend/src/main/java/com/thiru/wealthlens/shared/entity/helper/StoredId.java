package com.thiru.wealthlens.shared.entity.helper;

import org.bson.BsonString;
import org.bson.Document;

/**
 * A raw row's {@code _id} as the database holds it, for a query against a collection name (W-338). Spring Data turns a
 * {@code String} id of 24 hex digits into an {@code ObjectId} when the query names no entity, and the rows the build before
 * W-253 wrote keep such ids as strings, so the query matched nothing; a {@link BsonString} is left as it is.
 */
public final class StoredId {

    private StoredId() {
    }

    public static Object of(Document row) {
        Object id = row.get("_id");
        return id instanceof String text ? new BsonString(text) : id;
    }
}
