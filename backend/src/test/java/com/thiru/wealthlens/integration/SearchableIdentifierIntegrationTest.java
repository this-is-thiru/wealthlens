package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryBuilder;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Identifiers stored encrypted and still found by every query shape the code uses (W-253, D-485 C1a; re-based to
 * JPA in W-337e, D-949): a derived method, an {@code @Query} string and a {@link QueryBuilder} {@code Specification}
 * against the entity, each naming the property by its Java name.
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
        Map<String, Object> stored = new JdbcTemplate(postgresDataSource)
                .queryForMap("SELECT email, account_holder FROM assets LIMIT 1");

        // Then
        assertThat(stored.get("email").toString()).startsWith("k1:").doesNotContain("holder");
        assertThat(stored.get("account_holder").toString()).startsWith("k1:").doesNotContain("Lakshmi");
    }

    @Test
    void findByEmail_whenStoredEncrypted_findsTheAssetAndReadsItsIdentifiersBack() {
        // When / Then
        assertThat(portfolioRepository.findByEmail(USER)).singleElement()
                .satisfies(asset -> assertThat(asset.getAccountHolder()).isEqualTo(HOLDER));
        assertThat(portfolioRepository.findFirstByEmailAndStockCodeAndBrokerNameAndAccountHolderAndSegmentAndTransactionDateOrderByAuditMetadataCreatedAtAsc(
                USER, "INFY", BrokerName.ZERODHA, HOLDER, TradeSegment.DELIVERY, BOUGHT)).isPresent();
    }

    @Test
    void findEligibleHoldingsForSell_whenTheQueryStringNamesTheJavaName_findsTheAsset() {
        // When / Then
        assertThat(portfolioRepository.findEligibleHoldingsForSell(USER, "INFY", BrokerName.ZERODHA, HOLDER, BOUGHT)).hasSize(1);
    }

    @Test
    void find_whenAnAdHocSpecificationNamesTheJavaField_findsTheAsset() {
        // Given — the admin search path's QueryFilter shape: account_holder, remapped to its Java name (accountHolder)
        List<QueryFilter> filters = new ArrayList<>(List.of(
                QueryFilter.builder().filterKey("account_holder").operation(QueryFilter.FilterOperation.EQUALS)
                        .value(HOLDER).isDateField(false).build()));
        QueryFilter.validateAndSanitizationOfQueryFilters(filters, UserMail.from(USER));
        Specification<AssetEntity> specification = QueryBuilder.build(filters);

        // When / Then
        assertThat(portfolioRepository.findAll(specification)).hasSize(1);
    }
}
