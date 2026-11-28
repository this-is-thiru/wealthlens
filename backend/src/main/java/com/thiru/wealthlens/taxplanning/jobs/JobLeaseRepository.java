package com.thiru.wealthlens.taxplanning.jobs;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** The job leases; each write is its own transaction, since {@link TaxJobRunner} takes and releases a lease outside the job's. */
public interface JobLeaseRepository extends JpaRepository<JobLease, String> {

    /**
     * Acquires {@code job}'s lease for {@code holder} if no lease exists for it, or the existing one's
     * {@code expires_at} is before {@code now} — the Postgres equivalent of Mongo's upsert-with-filter acquire
     * (W-337f, D-951 C6a). An atomic statement, not a read-then-write: two runs racing for the same job can both
     * try this at once and at most one gets a row back. The guard compares the OLD row's expiry against {@code now},
     * never against {@code newExpiresAt} — comparing against the new expiry would always be true and let a run
     * steal a lease that is still live. A native query bypasses Hibernate's lifecycle entirely, so this leaves
     * {@code auditMetadata}'s four columns null, same as any other system write with no caller context.
     *
     * @return 1 if acquired, 0 if another run holds a lease that has not yet expired
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO tax_job_leases (job, holder, expires_at) VALUES (:job, :holder, :newExpiresAt) "
            + "ON CONFLICT (job) DO UPDATE SET holder = EXCLUDED.holder, expires_at = EXCLUDED.expires_at "
            + "WHERE tax_job_leases.expires_at < :now", nativeQuery = true)
    int acquire(@Param("job") String job, @Param("holder") String holder, @Param("newExpiresAt") Instant newExpiresAt,
            @Param("now") Instant now);

    boolean existsByJobAndHolderAndExpiresAtAfter(String job, String holder, Instant now);

    @Transactional
    void deleteByJobAndHolder(String job, String holder);
}
