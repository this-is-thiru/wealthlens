package com.thiru.wealthlens.taxplanning.jobs;

import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * Which run of a job holds it ({@code DR 19} E8): keyed by the job's name, so at most one exists; a lease past {@code expiresAt}
 * may be taken over, and Mongo removes it once expired. {@code holder} is unique per run, so a run overtaken while it was slow
 * sees that it no longer holds the lease and stops. An {@link Instant}, not a local time, so the TTL index removes it at the
 * moment it expires whatever the server's zone.
 */
@Data
@NoArgsConstructor
@Document("tax_job_leases")
public class JobLease {

    @MongoId
    private String job;

    @Field("holder")
    private String holder;

    @Indexed(expireAfter = "0s")
    @Field("expires_at")
    private Instant expiresAt;
}
