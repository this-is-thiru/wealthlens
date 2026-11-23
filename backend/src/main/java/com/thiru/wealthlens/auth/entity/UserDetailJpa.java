package com.thiru.wealthlens.auth.entity;

import com.thiru.wealthlens.shared.protection.SearchableColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * {@link UserDetail}'s PostgreSQL counterpart (W-337a, D-937): a parallel JPA mapping proving the probe, written beside
 * the live MongoDB entity rather than replacing it (the real cutover is W-337c's). The id is assigned by the caller, as
 * {@code UserDetail}'s is by MongoDB today.
 */
@Entity
@Table(name = "user_details")
@Data
public class UserDetailJpa {

    @Id
    private String id;

    @Column(name = "email", unique = true, nullable = false)
    @Convert(converter = SearchableColumn.class)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "roles")
    private String roles;

    /** UTC by convention, not by column type (D-937: not {@link java.time.Instant}, so plain {@code TIMESTAMP}). */
    @Column(name = "last_signed_in_at")
    private LocalDateTime lastSignedInAt;
}
