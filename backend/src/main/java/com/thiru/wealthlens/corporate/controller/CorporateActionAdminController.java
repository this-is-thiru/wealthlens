package com.thiru.wealthlens.corporate.controller;

import com.thiru.wealthlens.corporate.dto.CorporateActionDto;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.web.AdminApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Recording, ordering, removing and performing corporate actions: an admin's (split from {@code CorporateActionController}, D-855 C6a). */
@RequiredArgsConstructor
@AdminApi
@RestController
public class CorporateActionAdminController {

    private final CorporateActionService corporateActionService;
    private final TemporaryTransactionService temporaryTransactionService;

    @PreAuthorize("hasRole('SUPER_USER')")
    @PostMapping("/add")
    public ResponseEntity<String> addCorporateAction(@RequestBody CorporateActionDto corporateActionRequest) {

        String message = corporateActionService.addCorporateAction(corporateActionRequest);
        return ResponseEntity.ok(message);
    }

    @PreAuthorize("hasRole('SUPER_USER')")
    @PutMapping("/update/priority/{id}/{priority}")
    public ResponseEntity<String> updateCorporateActionPriority(@PathVariable String id, @PathVariable int priority) {

        String message = corporateActionService.updateCorporateActionPriority(id, priority);
        return ResponseEntity.ok(message);
    }

    @PreAuthorize("hasAnyRole('SUPER_USER')")
    @DeleteMapping("/delete/{id}")
    public void deleteCorporateActions(@PathVariable String id) {
        corporateActionService.deleteCorporateActions(id);
    }

    @PreAuthorize("hasRole('SUPER_USER')")
    @PutMapping("/perform")
    public ResponseEntity<String> updateCorporateAction(@RequestBody CorporateActionDto corporateActionRequest) {

        String message = corporateActionService.performCorporateAction(corporateActionRequest);
        return ResponseEntity.ok(message);
    }

    @PreAuthorize("hasRole('SUPER_USER')")
    @PutMapping("/perform/test")
    public ResponseEntity<Boolean> anyCorporateActionToPerform(@RequestBody CorporateActionDto request) {

        boolean message = false;
        for (BrokerName brokerName : BrokerName.values()) {
            message = temporaryTransactionService.anyCorporateActionToPerform(UserMail.from("test"), request.getStockCode(), request.getRecordDate(), brokerName);
        }
        return ResponseEntity.ok(message);
    }
}
