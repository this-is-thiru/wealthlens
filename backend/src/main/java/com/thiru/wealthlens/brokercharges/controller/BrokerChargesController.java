package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.dto.request.AssetManagementDetailsRequest;
import com.thiru.wealthlens.brokercharges.dto.request.BrokerChargesRequest;
import com.thiru.wealthlens.brokercharges.entity.BrokerCharges;
import com.thiru.wealthlens.brokercharges.service.BrokerChargeService;
import com.thiru.wealthlens.portfolio.entity.AssetManagementDetails;
import com.thiru.wealthlens.portfolio.service.AssetManagementService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>Superseded.</b> The HTTP surface of the superseded implementation.
 *
 * <p>Replaced by {@code ChargeScheduleController} and {@code ChargeAccountController}.
 *
 * <p><b>Do not delete yet.</b> Still reached by live code, and removal waits on human testing of
 * the charges engine — see {@code docs/charges-engine/implementation-checklist.md}, Chunk 11.
 *
 * <p>Plain {@code @Deprecated} rather than {@code forRemoval = true} on purpose. A removal warning
 * is <em>not</em> suppressed at a deprecated use site, so seventeen interlinked classes would warn
 * about each other and bury the only signal worth having. An ordinary deprecation warning is
 * suppressed inside deprecated code, which leaves the build reporting exactly the <b>live</b>
 * callers still to be migrated — and that list reaching zero is the precondition for deleting any
 * of this.
 */
@Deprecated
@RequiredArgsConstructor
@RequestMapping("/broker-charges/")
@RestController
public class BrokerChargesController {

    private final BrokerChargeService brokerChargeService;
    private final AssetManagementService assetManagementService;

    @PostMapping("/add")
    public String addBrokerCharge(@RequestBody BrokerChargesRequest brokerChargesRequest) {
        return brokerChargeService.addBrokerCharge(brokerChargesRequest);
    }

    @GetMapping("/{id}")
    public BrokerCharges getBrokerCharge(@PathVariable String id) {
        return brokerChargeService.getBrokerCharges(id);
    }

    @PostMapping("/user/{email}/add/asset-management-detail")
    public void addAssetManagementDetailsEntry(@PathVariable String email, @RequestBody AssetManagementDetailsRequest request) {
        assetManagementService.addAssetManagementEntry(UserMail.from(email), request);
    }

    @GetMapping("/user/{email}/asset-management-details")
    public List<AssetManagementDetails> getAssetManagementDetails(@PathVariable String email) {
        return assetManagementService.getAssetManagementDetails(UserMail.from(email));
    }

    @PostMapping("/amc/impose")
    public void imposeAmcCharges() {
        assetManagementService.imposeAmcCharges();
    }
}
