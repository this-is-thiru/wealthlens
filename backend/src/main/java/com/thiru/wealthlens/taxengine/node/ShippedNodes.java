package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import java.util.List;

/** Every node that runs, in one list: what a regime's pipeline names must be here (W-103, D-442). */
public final class ShippedNodes {

    private static final List<ComputationNode> ALL = List.of(
            new ComponentPartitionNode(),
            new EmployerContributionNode(),
            new GrossSalaryNode(),
            new SalaryBaseNode(),
            new ExemptionNode(),
            new MonetarySalaryIncomeNode(),
            new PerquisiteNode(),
            new RetirementNode(),
            new SalaryDeductionNode(),
            new SalaryIncomeBeforeTravelNode(),
            new MedicalTreatmentAbroadNode(),
            new SalaryIncomeNode(),
            new HousePropertyNode(),
            new OtherSourcesNode(),
            new CapitalGainsNode(),
            new SetOffNode(),
            new GrossTotalIncomeNode(),
            new ChapterEightNode(),
            new TotalIncomeNode(),
            new RoundTotalIncomeNode(),
            new TaxByBasisNode(),
            new RebateNode(),
            new SurchargeNode(),
            new CessNode(),
            new NetPayableNode(),
            new CreditsNode(),
            new BalanceNode(),
            new RoundNetPayableNode());

    private ShippedNodes() {
    }

    public static List<ComputationNode> all() {
        return ALL;
    }
}
