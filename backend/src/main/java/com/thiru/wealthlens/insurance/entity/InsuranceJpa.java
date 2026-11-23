package com.thiru.wealthlens.insurance.entity;

import com.thiru.wealthlens.insurance.dto.enums.InsuranceType;
import com.thiru.wealthlens.insurance.dto.enums.PolicyType;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetailsJpa;
import com.thiru.wealthlens.shared.protection.EncryptedColumn;
import com.thiru.wealthlens.shared.protection.SearchableColumn;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.List;
import lombok.Data;

/**
 * {@link InsuranceEntity}'s PostgreSQL counterpart (W-337a, D-937): a parallel JPA mapping proving the probe, written
 * beside the live MongoDB entity rather than replacing it (the real cutover is W-337c's). {@code policyDetails} becomes
 * a child table ({@code insurance_policy_details}) of embedded rows, matching Mongo's embedded list.
 */
@Entity
@Table(name = "insurances")
@Data
public class InsuranceJpa {

    @Id
    private String id;

    @Column(name = "email", nullable = false)
    @Convert(converter = SearchableColumn.class)
    private String email;

    @Column(name = "port_from")
    private String portFrom;

    @Column(name = "port_to")
    private String portTo;

    @Column(name = "insurance_id")
    @Convert(converter = EncryptedColumn.class)
    private String insuranceId;

    @Column(name = "insurance_type")
    @Enumerated(EnumType.STRING)
    private InsuranceType insuranceType;

    @Column(name = "policy_type")
    @Enumerated(EnumType.STRING)
    private PolicyType policyType;

    @Column(name = "premium")
    private double premium;

    @Column(name = "start_date")
    private String startDate;

    @Column(name = "end_date")
    private String endDate;

    @Column(name = "maturity_date")
    private String maturityDate;

    @Column(name = "renewal_date")
    private String renewalDate;

    @Column(name = "maturity_amount")
    private String maturityAmount;

    @Column(name = "broker")
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

    /** Eager, unlike JPA's default for a collection: Mongo's embedded list always arrives with its document. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "insurance_policy_details", joinColumns = @JoinColumn(name = "insurance_id_fk"))
    private List<PolicyDetailsJpa> policyDetails;
}
