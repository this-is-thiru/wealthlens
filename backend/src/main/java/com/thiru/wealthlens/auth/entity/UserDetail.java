package com.thiru.wealthlens.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.ToString;

/**
 * A registered user's login (W-337c1, D-941). The id is caller-assigned (D-939 C2a), so the logins moved in W-337i
 * kept their ids; {@link #assignIdIfAbsent} generates a UUID for fresh rows.
 */
@Entity
@Table(name = "user_details")
@Data
@ToString
public class UserDetail {

    @JsonIgnore
    @Id
    private String id;

    @JsonIgnore
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "roles")
    private String roles;

    /**
     * UTC; the latest successful sign-in or the registration, from which the account's purpose lapses (W-249). For an account
     * registered before W-249 it is when counting began: the first {@code account-erasure} run after deploy.
     */
    @Column(name = "last_signed_in_at")
    private LocalDateTime lastSignedInAt;

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
