package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.service.PortfolioQueryService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A holding's lots read oldest first (W-419, D-1002): the views combine a holding's lots and show the first lot's own
 * fields, so the order decides what a caller sees. MongoDB answered in its stored order; PostgreSQL keeps none unless asked.
 */
class HoldingsLotOrderIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "lots@example.com";
    private static final LocalDate BOUGHT = LocalDate.of(2025, 6, 2);

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioQueryService portfolioQueryService;

    private String newest;
    private String oldest;
    private String unaudited;

    @BeforeEach
    void storeThreeLotsOutOfCreationOrder() {
        // The newest is saved first, so the table's own order is the reverse of creation; one lot predates auditing
        newest = lot("ORDER-3", 3.0);
        oldest = lot("ORDER-1", 1.0);
        unaudited = lot("ORDER-0", 4.0);
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        jdbc.update("UPDATE assets SET created_at = ? WHERE id = ?", LocalDateTime.of(2025, 3, 1, 9, 0), newest);
        jdbc.update("UPDATE assets SET created_at = ? WHERE id = ?", LocalDateTime.of(2025, 1, 1, 9, 0), oldest);
        jdbc.update("UPDATE assets SET created_at = NULL WHERE id = ?", unaudited);
    }

    @Test
    void findByEmail_whenLotsWereCreatedOutOfTableOrder_answersOldestFirst() {
        // When / Then — the lot stored before auditing first, then by creation time
        assertThat(portfolioRepository.findByEmail(OWNER)).extracting(AssetEntity::getId).containsExactly(unaudited, oldest, newest);
        assertThat(portfolioRepository.findByEmailAndAssetType(OWNER, AssetType.EQUITY)).extracting(AssetEntity::getId)
                .containsExactly(unaudited, oldest, newest);
        assertThat(portfolioRepository.findByEmailAndTransactionDateBetween(OWNER, BOUGHT, BOUGHT)).extracting(AssetEntity::getId)
                .containsExactly(unaudited, oldest, newest);
    }

    @Test
    void getAllStocks_whenAHoldingHasSeveralLots_showsTheOldestLotsOwnFields() {
        // When
        List<AssetResponse> holdings = portfolioQueryService.getAllStocks(UserMail.from(OWNER));

        // Then — 4 + 1 + 3 units, and the order id of the lot read first
        assertThat(holdings).singleElement().satisfies(holding -> {
            assertThat(holding.getOrderId()).isEqualTo("W-420-DELIBERATE-RED");
            assertThat(holding.getTotalQuantity()).isEqualTo(8.0);
        });
    }

    private String lot(String orderId, double quantity) {
        AssetEntity lot = new AssetEntity();
        lot.setEmail(OWNER);
        lot.setStockCode("INFY");
        lot.setBrokerName(BrokerName.ZERODHA);
        lot.setAssetType(AssetType.EQUITY);
        lot.setTransactionDate(BOUGHT);
        lot.setOrderId(orderId);
        lot.setPrice(1500.0);
        lot.setQuantity(quantity);
        return portfolioRepository.save(lot).getId();
    }
}
