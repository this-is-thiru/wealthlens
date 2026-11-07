package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One request that reached a person's data, app-wide: who asked, whose data, by which route, with what result, from where and
 * when — never a value from the path, the query or the body (W-237e C1a). It is the log the Digital Personal Data Protection
 * Rules, 2025 r.6(1)(c) ask for, so that unauthorised access can be detected and investigated. Appended only; erased a year
 * after it was written ({@link LogRetention}), or with its principal's account (W-237e C3a).
 */
@Data
@NoArgsConstructor
@Document("privacy_processing_log")
public class ProcessingRecord {

    @MongoId
    private String id;

    /** The signed-in user, or the email a login was attempted for. */
    @Indexed
    @Field("principal")
    private String principal;

    /** The person whose data the request reached: the path's {@code {email}}, else the principal. */
    @Field("subject_email")
    private String subjectEmail;

    @Field("method")
    private String method;

    /** The matched route pattern; absent when the request was refused before it reached a controller. */
    @Field("route")
    private String route;

    @Field("status")
    private int status;

    /** The address the request came from: r.8(3)'s "associated traffic data". */
    @Field("remote_address")
    private String remoteAddress;

    @Indexed
    @Field("recorded_at")
    private LocalDateTime recordedAt;
}
