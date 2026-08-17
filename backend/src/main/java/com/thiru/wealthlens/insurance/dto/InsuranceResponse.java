package com.thiru.wealthlens.insurance.dto;

import com.thiru.wealthlens.insurance.dto.enums.InsuranceType;
import com.thiru.wealthlens.insurance.dto.enums.PolicyType;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetails;
import java.util.List;
import lombok.Data;

@Data
public class InsuranceResponse {

	private String email;
	private String portFrom;
	private String portTo;
	private String insuranceId;
	private String insurancePolicy;
	private InsuranceType insuranceType;
	private PolicyType policyType;
	private double premium;
	private String startDate;
	private String endDate;
	private String maturityDate;
	private String renewalDate;
	private String maturityAmount;
	private String broker;
	private String agentName;
	private String agentEmail;
	private String agentContact;
	private String agentAddress;
	private String insuranceStatus;
	private String notes;

	private List<PolicyDetails> policyDetails;
}
