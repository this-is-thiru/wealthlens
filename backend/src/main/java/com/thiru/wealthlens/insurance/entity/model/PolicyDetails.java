package com.thiru.wealthlens.insurance.entity.model;

import com.thiru.wealthlens.shared.protection.EncryptedField;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
public class PolicyDetails {

	@ValueConverter(EncryptedField.class)
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

	@ValueConverter(EncryptedField.class)
	@Field("agent_name")
	private String agentName;

	@ValueConverter(EncryptedField.class)
	@Field("agent_email")
	private String agentEmail;

	@ValueConverter(EncryptedField.class)
	@Field("agent_contact")
	private String agentContact;

	@ValueConverter(EncryptedField.class)
	@Field("agent_address")
	private String agentAddress;

	@Field("insurance_status")
	private String insuranceStatus;

	@ValueConverter(EncryptedField.class)
	@Field("notes")
	private String notes;

	@ValueConverter(EncryptedField.class)
	@Field("policy_description")
	private String policyDescription;
}
