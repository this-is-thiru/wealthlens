package com.thiru.wealthlens.insurance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.insurance.dto.enums.InsuranceType;
import com.thiru.wealthlens.insurance.dto.enums.PolicyType;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetails;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Insurance policies on PostgreSQL (W-337c1, D-941). {@code policyDetails} is stored in {@code insurance_policy_details}
 * as an eager element collection matching Mongo's embedded list.
 */
@Entity
@Table(name = "insurances")
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class InsuranceEntity {

    @JsonIgnore
    @Id
    private String id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "port_from")
    private String portFrom;

    @Column(name = "port_to")
    private String portTo;

    @Column(name = "insurance_id")
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
    private String agentName;

    @Column(name = "agent_email")
    private String agentEmail;

    @Column(name = "agent_contact")
    private String agentContact;

    @Column(name = "agent_address")
    private String agentAddress;

    @Column(name = "insurance_status")
    private String insuranceStatus;

    @Column(name = "notes")
    private String notes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "insurance_policy_details", joinColumns = @JoinColumn(name = "insurance_id_fk"))
    private List<PolicyDetails> policyDetails;

    @Embedded
    @Setter(value = AccessLevel.NONE)
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
