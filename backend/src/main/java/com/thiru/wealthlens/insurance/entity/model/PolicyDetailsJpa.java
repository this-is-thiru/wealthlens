package com.thiru.wealthlens.insurance.entity.model;

import com.thiru.wealthlens.shared.protection.EncryptedColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.Data;

/**
 * {@link PolicyDetails}'s PostgreSQL counterpart (W-337a, D-937): embedded rather than its own entity, matching Mongo's
 * embedded-list form, since it has no identity of its own outside {@code InsuranceJpa}.
 */
@Embeddable
@Data
public class PolicyDetailsJpa {

    @Column(name = "policy_id")
    @Convert(converter = EncryptedColumn.class)
    private String policyId;

    @Column(name = "premium")
    private double premium;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "policy_name")
    private String policyName;

    @Column(name = "broker_name")
    private String broker;

    @Column(name = "agent_name")
    @Convert(converter = EncryptedColumn.class)
    private String agentName;

    @Column(name = "agent_email")
    @Convert(converter = EncryptedColumn.class)
    private String agentEmail;

    @Column(name = "agent_contact")
    @Convert(converter = EncryptedColumn.class)
    private String agentContact;

    @Column(name = "agent_address")
    @Convert(converter = EncryptedColumn.class)
    private String agentAddress;

    @Column(name = "insurance_status")
    private String insuranceStatus;

    @Column(name = "notes")
    @Convert(converter = EncryptedColumn.class)
    private String notes;

    @Column(name = "policy_description")
    @Convert(converter = EncryptedColumn.class)
    private String policyDescription;
}
