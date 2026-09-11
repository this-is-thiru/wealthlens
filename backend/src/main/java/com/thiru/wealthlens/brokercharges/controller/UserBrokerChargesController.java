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

/**
 * <b>Superseded.</b> The HTTP surface for reading superseded charges.
 *
 * <p>Replaced by {@code UserChargesController}.
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
@RequestMapping("/user-broker-charges/user/{email}")
@RestController
public class UserBrokerChargesController {

    private final UserBrokerChargeService userBrokerChargesService;

    @GetMapping("/all")
    public List<UserBrokerCharges> getUserBrokerCharges(@PathVariable String email) {
        return userBrokerChargesService.getUserBrokerCharges(UserMail.from(email));
    }
}
