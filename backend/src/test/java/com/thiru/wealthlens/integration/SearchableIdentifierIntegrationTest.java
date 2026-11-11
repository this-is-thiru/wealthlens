package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import java.time.LocalDate;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Identifiers stored encrypted and still found by every query shape the code uses (W-253, D-485 C1a): a derived method, an
 * {@code @Query} string and a {@code Criteria} against the entity, each naming the property by its Java name. A stored name that
 * differs ({@code account_holder}) is not converted and finds nothing; {@code StoredIdentifierQueryTest} keeps it out of query strings.
 */
class SearchableIdentifierIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "holder@example.com";
    private static final String HOLDER = "Lakshmi Devi";
    private static final LocalDate BOUGHT = LocalDate.of(2026, 4, 2);

    @Autowired
    private PortfolioRepository portfolioRepository;

    @BeforeEach
    void storeOneAsset() {
        AssetEntity asset = new AssetEntity();
        asset.setEmail(USER);
        asset.setAccountHolder(HOLDER);
        asset.setStockCode("INFY");
        asset.setBrokerName(BrokerName.ZERODHA);
        asset.setTransactionDate(BOUGHT);
        portfolioRepository.save(asset);
    }

    @Test
    void save_whenAnAssetIsStored_holdsItsIdentifiersEncrypted() {
        // When
        Document stored = mongoTemplate.getCollection("assets").find().first();

        // Then
        assertThat(stored).isNotNull();
        assertThat(stored.getString("email")).startsWith("k1:").doesNotContain("holder");
        assertThat(stored.getString("account_holder")).startsWith("k1:").doesNotContain("Lakshmi");
    }

    @Test
    void findByEmail_whenStoredEncrypted_findsTheAssetAndReadsItsIdentifiersBack() {
        // When / Then
        assertThat(portfolioRepository.findByEmail(USER)).singleElement()
                .satisfies(asset -> assertThat(asset.getAccountHolder()).isEqualTo(HOLDER));
        assertThat(portfolioRepository.findFirstByEmailAndStockCodeAndBrokerNameAndAccountHolderAndTransactionDateOrderByIdAsc(USER, "INFY",
                BrokerName.ZERODHA, HOLDER, BOUGHT)).isPresent();
    }

    @Test
    void findEligibleHoldingsForSell_whenTheQueryStringNamesTheJavaName_findsTheAsset() {
        // When / Then
        assertThat(portfolioRepository.findEligibleHoldingsForSell(USER, "INFY", BrokerName.ZERODHA, HOLDER, BOUGHT)).hasSize(1);
    }

    @Test
    void find_whenCriteriaNameTheJavaField_findsTheAsset() {
        // When / Then
        assertThat(mongoTemplate.find(query(where("email").is(USER).and("accountHolder").is(HOLDER)), AssetEntity.class)).hasSize(1);
    }
}
