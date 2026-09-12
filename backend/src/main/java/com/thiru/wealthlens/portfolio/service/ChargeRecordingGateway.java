package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.Optional;

public interface ChargeRecordingGateway {

    Optional<ChargeComputation> record(UserMail userMail, ProfitLossContext profitLossContext);
}
