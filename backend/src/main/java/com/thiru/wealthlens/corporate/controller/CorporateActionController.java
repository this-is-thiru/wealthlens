package com.thiru.wealthlens.corporate.controller;
import com.thiru.wealthlens.corporate.dto.CorporateActionDto;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/corporate-action/")
@RestController
public class CorporateActionController {

    private final CorporateActionService corporateActionService;
    private final TemporaryTransactionService temporaryTransactionService;

    @PostMapping("/add")
    public ResponseEntity<String> addCorporateAction(@RequestBody CorporateActionDto corporateActionRequest) {

        String message = corporateActionService.addCorporateAction(corporateActionRequest);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/{id}")
    public CorporateActionEntity getCorporateAction(@PathVariable String id) {
        return corporateActionService.getCorporateActionDetails(id);
    }

    @PutMapping("/update/priority/{id}/{priority}")
    public ResponseEntity<String> updateCorporateActionPriority(@PathVariable String id, @PathVariable int priority) {

        String message = corporateActionService.updateCorporateActionPriority(id, priority);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/list")
    public ResponseEntity<List<CorporateActionDto>> getCorporateActions(@RequestParam List<String> ids) {

        List<CorporateActionDto> actions = corporateActionService.getCorporateActions(ids);
        return ResponseEntity.ok(actions);
    }

    @GetMapping("/all")
    public List<CorporateActionEntity> getAllCorporateActions() {
        return corporateActionService.getAllCorporateActions();
    }

    @PreAuthorize("hasAnyRole('SUPER_USER')")
    @DeleteMapping("/delete/{id}")
    public void deleteCorporateActions(@PathVariable String id) {
        corporateActionService.deleteCorporateActions(id);
    }

    @PutMapping("/perform")
    public ResponseEntity<String> updateCorporateAction(@RequestBody CorporateActionDto corporateActionRequest) {

        String message = corporateActionService.performCorporateAction(corporateActionRequest);
        return ResponseEntity.ok(message);
    }

    @PutMapping("/perform/test")
    public ResponseEntity<Boolean> anyCorporateActionToPerform(@RequestBody CorporateActionDto request) {

        boolean message = false;
        for (BrokerName brokerName : BrokerName.values()) {
            message = temporaryTransactionService.anyCorporateActionToPerform(UserMail.from("test"), request.getStockCode(), request.getRecordDate(), brokerName);
        }
        return ResponseEntity.ok(message);
    }
}
