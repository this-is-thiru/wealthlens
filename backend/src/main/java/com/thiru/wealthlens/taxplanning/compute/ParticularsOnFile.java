package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxplanning.profile.Landlord;
import com.thiru.wealthlens.taxplanning.profile.LandlordRepository;
import com.thiru.wealthlens.taxplanning.profile.Lender;
import com.thiru.wealthlens.taxplanning.profile.LenderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether the third parties a draft names have the name, address and PAN r.205(2) asks for on file (Sl. 1 for
 * landlords, Sl. 3 for lenders): one test for the integrity panel and the checklist (W-120). None named is none on
 * file, and a draft's parties are said together, since a tenancy or loan does not say which is its own.
 */
@Component
@RequiredArgsConstructor
class ParticularsOnFile {

    private final LandlordRepository landlords;
    private final LenderRepository lenders;

    boolean landlords(List<String> landlordIds) {
        List<Landlord> named = landlords.findAllById(landlordIds);
        return !landlordIds.isEmpty() && named.size() == landlordIds.size() && named.stream().allMatch(landlord ->
                isGiven(landlord.getName()) && isGiven(landlord.getAddress()) && isGiven(landlord.getPan()));
    }

    boolean lenders(List<String> lenderIds) {
        List<Lender> named = lenders.findAllById(lenderIds);
        return !lenderIds.isEmpty() && named.size() == lenderIds.size() && named.stream().allMatch(lender ->
                isGiven(lender.getName()) && isGiven(lender.getAddress()) && isGiven(lender.getPan()));
    }

    private static boolean isGiven(String value) {
        return value != null && !value.isBlank();
    }
}
