package com.thiru.wealthlens.taxplanning.profile.controller;

import com.thiru.wealthlens.taxplanning.profile.BankAccountRequest;
import com.thiru.wealthlens.taxplanning.profile.BankAccountResponse;
import com.thiru.wealthlens.taxplanning.profile.EmployerRequest;
import com.thiru.wealthlens.taxplanning.profile.EmployerResponse;
import com.thiru.wealthlens.taxplanning.profile.FamilyMemberRequest;
import com.thiru.wealthlens.taxplanning.profile.FamilyMemberResponse;
import com.thiru.wealthlens.taxplanning.profile.LandlordRequest;
import com.thiru.wealthlens.taxplanning.profile.LandlordResponse;
import com.thiru.wealthlens.taxplanning.profile.LenderRequest;
import com.thiru.wealthlens.taxplanning.profile.LenderResponse;
import com.thiru.wealthlens.taxplanning.profile.TaxProfileReferenceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The people and accounts a profile refers to. {@code AuthFilter} refuses a caller whose token is not the path's email ({@code DR 14} O2). */
@RequiredArgsConstructor
@RequestMapping("/tax/user/{email}/profiles/{profileId}")
@RestController
public class TaxProfileReferenceController {

    private final TaxProfileReferenceService service;

    @PostMapping("/family-members")
    public FamilyMemberResponse createFamilyMember(@PathVariable String email, @PathVariable String profileId, @RequestBody FamilyMemberRequest request) {
        return FamilyMemberResponse.of(service.createFamilyMember(email, profileId, request));
    }

    @GetMapping("/family-members")
    public List<FamilyMemberResponse> familyMembers(@PathVariable String email, @PathVariable String profileId) {
        return service.familyMembers(email, profileId).stream().map(FamilyMemberResponse::of).toList();
    }

    @PutMapping("/family-members/{id}")
    public FamilyMemberResponse replaceFamilyMember(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody FamilyMemberRequest request) {
        return FamilyMemberResponse.of(service.replaceFamilyMember(email, profileId, id, request));
    }

    @PostMapping("/employers")
    public EmployerResponse createEmployer(@PathVariable String email, @PathVariable String profileId, @RequestBody EmployerRequest request) {
        return EmployerResponse.of(service.createEmployer(email, profileId, request));
    }

    @GetMapping("/employers")
    public List<EmployerResponse> employers(@PathVariable String email, @PathVariable String profileId) {
        return service.employers(email, profileId).stream().map(EmployerResponse::of).toList();
    }

    @PutMapping("/employers/{id}")
    public EmployerResponse replaceEmployer(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody EmployerRequest request) {
        return EmployerResponse.of(service.replaceEmployer(email, profileId, id, request));
    }

    @PostMapping("/landlords")
    public LandlordResponse createLandlord(@PathVariable String email, @PathVariable String profileId, @RequestBody LandlordRequest request) {
        return LandlordResponse.of(service.createLandlord(email, profileId, request));
    }

    @GetMapping("/landlords")
    public List<LandlordResponse> landlords(@PathVariable String email, @PathVariable String profileId) {
        return service.landlords(email, profileId).stream().map(LandlordResponse::of).toList();
    }

    @PutMapping("/landlords/{id}")
    public LandlordResponse replaceLandlord(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody LandlordRequest request) {
        return LandlordResponse.of(service.replaceLandlord(email, profileId, id, request));
    }

    @PostMapping("/lenders")
    public LenderResponse createLender(@PathVariable String email, @PathVariable String profileId, @RequestBody LenderRequest request) {
        return LenderResponse.of(service.createLender(email, profileId, request));
    }

    @GetMapping("/lenders")
    public List<LenderResponse> lenders(@PathVariable String email, @PathVariable String profileId) {
        return service.lenders(email, profileId).stream().map(LenderResponse::of).toList();
    }

    @PutMapping("/lenders/{id}")
    public LenderResponse replaceLender(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody LenderRequest request) {
        return LenderResponse.of(service.replaceLender(email, profileId, id, request));
    }

    @PostMapping("/bank-accounts")
    public BankAccountResponse createBankAccount(@PathVariable String email, @PathVariable String profileId, @RequestBody BankAccountRequest request) {
        return BankAccountResponse.of(service.createBankAccount(email, profileId, request));
    }

    @GetMapping("/bank-accounts")
    public List<BankAccountResponse> bankAccounts(@PathVariable String email, @PathVariable String profileId) {
        return service.bankAccounts(email, profileId).stream().map(BankAccountResponse::of).toList();
    }

    @PutMapping("/bank-accounts/{id}")
    public BankAccountResponse replaceBankAccount(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody BankAccountRequest request) {
        return BankAccountResponse.of(service.replaceBankAccount(email, profileId, id, request));
    }
}
