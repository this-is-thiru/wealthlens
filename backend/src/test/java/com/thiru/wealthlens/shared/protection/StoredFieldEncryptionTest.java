package com.thiru.wealthlens.shared.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

@ExtendWith(MockitoExtension.class)
class StoredFieldEncryptionTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void encrypt_whenARowChangesUnderneathTheRun_conditionsTheUpdateOnThePlainValueAndCountsItUnchanged() {
        // Given — the row read holds "Wrong address shown"; by the update it holds something else, so nothing matches
        StoredFieldEncryption encryption = new StoredFieldEncryption(mongoTemplate, new FieldCipher(new PersonalDataKeys(KEY, KEY)));
        when(mongoTemplate.stream(any(Query.class), eq(Document.class), anyString())).thenAnswer(call ->
                "privacy_grievances".equals(call.getArgument(2))
                        ? Stream.of(new Document("_id", "g1").append("text", "Wrong address shown")) : Stream.empty());
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq("privacy_grievances")))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        // When
        StoredFieldEncryptionReport report = encryption.encrypt(false, "owner@example.com");

        // Then
        ArgumentCaptor<Query> condition = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(condition.capture(), any(UpdateDefinition.class), eq("privacy_grievances"));
        assertThat(condition.getValue().getQueryObject())
                .isEqualTo(new Document("_id", "g1").append("text", "Wrong address shown"));
        assertThat(report.collections().getFirst())
                .isEqualTo(new StoredFieldEncryptionReport.CollectionCount("privacy_grievances", 1, 0, 0));
    }
}
