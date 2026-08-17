package com.thiru.wealthlens.insurance.entity.model;

import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
public class PolicyDetails {

	@Field("policy_id")
	private String policyId;

	@Field("premium")
	private double premium;

	@Field("start_date")
	private LocalDate startDate;

	@Field("end_date")
	private LocalDate endDate;

	@Field("policy_name")
	private String policyName;

	@Field("broker_name")
	private String broker;

	@Field("agent_name")
	private String agentName;

	@Field("agent_email")
	private String agentEmail;

	@Field("agent_contact")
	private String agentContact;

	@Field("agent_address")
	private String agentAddress;

	@Field("insurance_status")
	private String insuranceStatus;

	@Field("notes")
	private String notes;

	@Field("policy_description")
	private String policyDescription;
}
