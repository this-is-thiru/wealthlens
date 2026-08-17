package com.thiru.wealthlens.insurance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.insurance.dto.enums.InsuranceType;
import com.thiru.wealthlens.insurance.dto.enums.PolicyType;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetails;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(value = "insurances")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class InsuranceEntity implements AuditableEntity {

	@JsonIgnore
	@MongoId
	private String id;

	@Field("email")
	private String email;
	@Field("port_from")
	private String portFrom;
	@Field("port_to")
	private String portTo;
	@Field("insurance_id")
	private String insuranceId;
	@Field("insurance_type")
	private InsuranceType insuranceType;
	@Field("policy_type")
	private PolicyType policyType;
	@Field("premium")
	private double premium;
	@Field("start_date")
	private String startDate;
	@Field("end_date")
	private String endDate;
	@Field("maturity_date")
	private String maturityDate;
	@Field("renewal_date")
	private String renewalDate;
	@Field("maturity_amount")
	private String maturityAmount;
	@Field("broker")
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

	private List<PolicyDetails> policyDetails;

	@Field("audit_metadata")
	@Setter(value = AccessLevel.NONE)
	private AuditMetadata auditMetadata = new AuditMetadata();
}
