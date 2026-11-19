package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.entity.LastlyPerformedCorporateAction;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import org.bson.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.query.Update;

/**
 * The corrections that update through an entity class (runbook §2.1 – §2.5, §2.9, §2.14) find a row the build before W-253
 * wrote, whose {@code _id} is a String of 24 hex digits (prod, W-334c2 row 6): each entity's {@code @MongoId String} keeps it a
 * string, unlike a query against a bare collection name (W-338).
 */
class StoredStringIdIntegrationTest extends AbstractIntegrationTest {

    private static final String ID = "65f0a1b2c3d4e5f6a7b8c9d2";

    @ParameterizedTest
    @ValueSource(classes = {TransactionEntity.class, AssetEntity.class, TradeOutcomeEntity.class, ProfitAndLossEntity.class,
            CorporateActionEntity.class, LastlyPerformedCorporateAction.class})
    void updateFirst_whenTheEntitysIdIsAStringShapedLikeAnObjectId_findsTheRow(Class<?> entity) {
        // Given
        String collection = mongoTemplate.getCollectionName(entity);
        mongoTemplate.getCollection(collection).insertOne(new Document("_id", ID));

        // When
        long modified = mongoTemplate.updateFirst(query(where("id").is(ID)), Update.update("w338_probe", 1), entity)
                .getModifiedCount();

        // Then
        assertThat(modified).isEqualTo(1);
        assertThat(mongoTemplate.getCollection(collection).find(new Document("_id", ID)).first().getInteger("w338_probe")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(classes = {TransactionEntity.class, AssetEntity.class, TradeOutcomeEntity.class, ProfitAndLossEntity.class,
            CorporateActionEntity.class, LastlyPerformedCorporateAction.class})
    void findById_whenTheEntitysIdIsAStringShapedLikeAnObjectId_findsTheRow(Class<?> entity) {
        // Given
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(entity)).insertOne(new Document("_id", ID));

        // When
        Object found = mongoTemplate.findById(ID, entity);

        // Then
        assertThat(found).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(classes = {TradeOutcomeEntity.class, TransactionEntity.class, AssetEntity.class})
    void bulkUpdateOne_whenTheEntitysIdIsAStringShapedLikeAnObjectId_findsTheRow(Class<?> entity) {
        // Given
        String collection = mongoTemplate.getCollectionName(entity);
        mongoTemplate.getCollection(collection).insertOne(new Document("_id", ID));

        // When
        int modified = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, entity)
                .updateOne(query(where("id").is(ID)), Update.update("w338_probe", 1)).execute().getModifiedCount();

        // Then
        assertThat(modified).isEqualTo(1);
    }
}
