package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.entity.LastlyPerformedCorporateAction;
import com.thiru.wealthlens.corporate.repository.CorporateActionRepository;
import com.thiru.wealthlens.corporate.repository.LastlyPerformedCorporateActionRepo;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which splits and demergers a user's lots have been through, per stock and broker — the corporate actions the
 * {@code assets} rebuild replays (W-283a2 C2a of D-577). Bonuses and name changes are not here: they rewrote
 * {@code transactions} too, so the replay meets them there.
 *
 * <p>An action is performed when the user's last-performed date for its stock, asset type, type and broker is on or
 * after its record date — the live test ({@code TemporaryTransactionService.isCorporateActionToPerform}) read the
 * other way round. Actions are performed quarter by quarter, so every one up to that date was.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PerformedCorporateActions {

    private static final List<CorporateActionType> LOT_ACTIONS =
            List.of(CorporateActionType.STOCK_SPLIT, CorporateActionType.DEMERGER);

    private final CorporateActionRepository corporateActionRepository;
    private final LastlyPerformedCorporateActionRepo lastlyPerformedCorporateActionRepo;

    /** An action and the broker whose lots it reaches. */
    public record BrokerAction(CorporateActionEntity action, BrokerName brokerName) {

        String label() {
            return action.getType() + " " + action.getStockCode() + " " + action.getRecordDate() + " " + brokerName;
        }
    }

    /** The actions to replay, and those left pending. */
    public record Replay(List<BrokerAction> performed, List<BrokerAction> pending) {

        public Replay {
            performed = List.copyOf(performed);
            pending = List.copyOf(pending);
        }
    }

    /** @param brokersByStock the stocks the user traded, each with the brokers they traded it with */
    public Replay of(String email, Map<String, Set<BrokerName>> brokersByStock) {
        List<BrokerAction> performed = new ArrayList<>();
        List<BrokerAction> pending = new ArrayList<>();
        brokersByStock.forEach((stockCode, brokers) -> {
            for (CorporateActionEntity action : corporateActionRepository.findByStockCodeAndTypeIn(stockCode, LOT_ACTIONS)) {
                for (BrokerName brokerName : brokers) {
                    Optional<LastlyPerformedCorporateAction> last = lastlyPerformedCorporateActionRepo
                            .findLastPerformedCA(email, stockCode, action.getAssetType(), action.getType(), brokerName);
                    boolean wasPerformed = last.isPresent()
                            && !last.get().getActionDate().isBefore(action.getRecordDate());
                    (wasPerformed ? performed : pending).add(new BrokerAction(action, brokerName));
                }
            }
        });
        return new Replay(performed, pending);
    }
}
