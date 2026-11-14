package com.thiru.wealthlens.taxplanning.jobs;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/** Something a job refused to do, and why ({@code DR 19} E8's dead letter): kept for the owner to act on. Never a value. */
@Data
@NoArgsConstructor
@Document("tax_job_failures")
public class JobFailure {

    @MongoId
    private String id;

    @Field("job")
    private String job;

    @Field("subject")
    private String subject;

    @Field("reason")
    private String reason;

    @Field("failed_at")
    private LocalDateTime failedAt;
}
