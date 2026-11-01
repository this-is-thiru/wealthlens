package com.thiru.wealthlens.taxplanning.profile.controller;

import com.thiru.wealthlens.taxplanning.profile.BankAccount;
import com.thiru.wealthlens.taxplanning.profile.BankAccountRequest;
import com.thiru.wealthlens.taxplanning.profile.Employer;
import com.thiru.wealthlens.taxplanning.profile.EmployerRequest;
import com.thiru.wealthlens.taxplanning.profile.FamilyMember;
import com.thiru.wealthlens.taxplanning.profile.FamilyMemberRequest;
import com.thiru.wealthlens.taxplanning.profile.Landlord;
import com.thiru.wealthlens.taxplanning.profile.LandlordRequest;
import com.thiru.wealthlens.taxplanning.profile.Lender;
import com.thiru.wealthlens.taxplanning.profile.LenderRequest;
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
    public FamilyMember createFamilyMember(@PathVariable String email, @PathVariable String profileId, @RequestBody FamilyMemberRequest request) {
        return service.createFamilyMember(email, profileId, request);
    }

    @GetMapping("/family-members")
    public List<FamilyMember> familyMembers(@PathVariable String email, @PathVariable String profileId) {
        return service.familyMembers(email, profileId);
    }

    @PutMapping("/family-members/{id}")
    public FamilyMember replaceFamilyMember(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody FamilyMemberRequest request) {
        return service.replaceFamilyMember(email, profileId, id, request);
    }

    @PostMapping("/employers")
    public Employer createEmployer(@PathVariable String email, @PathVariable String profileId, @RequestBody EmployerRequest request) {
        return service.createEmployer(email, profileId, request);
    }

    @GetMapping("/employers")
    public List<Employer> employers(@PathVariable String email, @PathVariable String profileId) {
        return service.employers(email, profileId);
    }

    @PutMapping("/employers/{id}")
    public Employer replaceEmployer(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody EmployerRequest request) {
        return service.replaceEmployer(email, profileId, id, request);
    }

    @PostMapping("/landlords")
    public Landlord createLandlord(@PathVariable String email, @PathVariable String profileId, @RequestBody LandlordRequest request) {
        return service.createLandlord(email, profileId, request);
    }

    @GetMapping("/landlords")
    public List<Landlord> landlords(@PathVariable String email, @PathVariable String profileId) {
        return service.landlords(email, profileId);
    }

    @PutMapping("/landlords/{id}")
    public Landlord replaceLandlord(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody LandlordRequest request) {
        return service.replaceLandlord(email, profileId, id, request);
    }

    @PostMapping("/lenders")
    public Lender createLender(@PathVariable String email, @PathVariable String profileId, @RequestBody LenderRequest request) {
        return service.createLender(email, profileId, request);
    }

    @GetMapping("/lenders")
    public List<Lender> lenders(@PathVariable String email, @PathVariable String profileId) {
        return service.lenders(email, profileId);
    }

    @PutMapping("/lenders/{id}")
    public Lender replaceLender(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody LenderRequest request) {
        return service.replaceLender(email, profileId, id, request);
    }

    @PostMapping("/bank-accounts")
    public BankAccount createBankAccount(@PathVariable String email, @PathVariable String profileId, @RequestBody BankAccountRequest request) {
        return service.createBankAccount(email, profileId, request);
    }

    @GetMapping("/bank-accounts")
    public List<BankAccount> bankAccounts(@PathVariable String email, @PathVariable String profileId) {
        return service.bankAccounts(email, profileId);
    }

    @PutMapping("/bank-accounts/{id}")
    public BankAccount replaceBankAccount(@PathVariable String email, @PathVariable String profileId, @PathVariable String id,
            @RequestBody BankAccountRequest request) {
        return service.replaceBankAccount(email, profileId, id, request);
    }
}
