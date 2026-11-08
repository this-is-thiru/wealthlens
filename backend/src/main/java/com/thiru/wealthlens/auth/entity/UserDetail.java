package com.thiru.wealthlens.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(value = "user_details")
@Data
@ToString
public class UserDetail {

    /**
     * Generated, because Spring Data writes an id without its value converter, so the email cannot be the id and be stored encrypted
     * (W-253 C2a). A row from before W-253 has its email as the id until the stored-field encryption re-keys it (runbook §1.r).
     */
    @JsonIgnore
    @MongoId
    private String id;

    /** Unique; sparse, so the rows not yet re-keyed, which hold no {@code email} field, do not collide while the index is built. */
    @JsonIgnore
    @Field("email")
    @Indexed(name = "user_email_idx", unique = true, sparse = true)
    @ValueConverter(SearchableIdentifier.class)
    private String email;
    private String password;
    private String roles;

    /**
     * UTC; the latest successful sign-in or the registration, from which the account's purpose lapses (W-249). For an account
     * registered before W-249 it is when counting began: the first {@code account-erasure} run after deploy.
     */
    @Field("last_signed_in_at")
    private LocalDateTime lastSignedInAt;
}
