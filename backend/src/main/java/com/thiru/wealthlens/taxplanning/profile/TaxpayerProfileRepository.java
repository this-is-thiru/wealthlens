package com.thiru.wealthlens.taxplanning.profile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaxpayerProfileRepository extends MongoRepository<TaxpayerProfile, String> {

    /** Every profile of an account. */
    List<TaxpayerProfile> findByEmail(String email);

    /** The account's profile that holds its portfolio, if any. */
    List<TaxpayerProfile> findByEmailAndHoldsPortfolioTrue(String email);

    /** Profiles held for erasure whose request was made at or before the given time (UTC). */
    List<TaxpayerProfile> findByErasureRequestedAtLessThanEqual(LocalDateTime requestedBy);

    /** Every profile held for erasure. */
    List<TaxpayerProfile> findByErasureRequestedAtIsNotNull();

    /** The ids of the profiles held for erasure: nothing may process them (W-235). */
    default Set<String> idsHeldForErasure() {
        return findByErasureRequestedAtIsNotNull().stream().map(TaxpayerProfile::getId).collect(Collectors.toSet());
    }
}
