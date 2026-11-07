package com.thiru.wealthlens.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(value = "user_details")
@Data
@ToString
public class UserDetail {
    @JsonIgnore
    @MongoId
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
