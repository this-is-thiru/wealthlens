package com.thiru.wealthlens.brokercharges.controller;

import com.thiru.wealthlens.brokercharges.entity.UserBrokerCharges;
import com.thiru.wealthlens.brokercharges.service.UserBrokerChargeService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/user-broker-charges/user/{email}")
@RestController
public class UserBrokerChargesController {

    private final UserBrokerChargeService userBrokerChargesService;

    @GetMapping("/all")
    public List<UserBrokerCharges> getUserBrokerCharges(@PathVariable String email) {
        return userBrokerChargesService.getUserBrokerCharges(UserMail.from(email));
    }
}
