package com.thiru.wealthlens.insurance.entity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.Data;

@Embeddable
@Data
public class PolicyDetails {

    @Column(name = "policy_id")
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

    @Column(name = "policy_description")
    private String policyDescription;
}
