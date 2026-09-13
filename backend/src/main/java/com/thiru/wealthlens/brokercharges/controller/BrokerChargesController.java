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
