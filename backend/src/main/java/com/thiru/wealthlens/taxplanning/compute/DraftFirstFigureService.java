package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.RegimeComparison;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.DraftView;
import com.thiru.wealthlens.taxplanning.workspace.OnboardingStep;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The first figure (W-122c, D-722): a draft made from step 1's answer through the ordinary draft write — created, then
 * one patch setting the pay, the due date and step 1 answered — and computed under each regime the year compares. The
 * pay is one {@code SPECIAL_ALLOWANCE}, taxable as salary and in no salary base, so nothing about its split is assumed
 * (W-122c C3a).
 */
@Service
@Transactional("postgresTransactionManager")
@RequiredArgsConstructor
public class DraftFirstFigureService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String PAY_COMPONENT = "SPECIAL_ALLOWANCE";

    private final DraftService draftService;
    private final DraftComputationService computationService;

    /**
     * @throws BadRequestException if no label, no gross pay above nil or no due date is given, or the profile is not
     *     this account's
     */
    public DraftFirstFigure firstFigure(String email, String profileId, int taxYearStart, DraftFirstFigureRequest request) {
        requireComplete(request);
        DraftView created = draftService.createDraft(email, profileId, taxYearStart, request.label());
        DraftView answered = draftService.patch(email, created.id(), created.revision(), stepOne(request));
        DraftRegimeComparison regimes = computationService.compare(email, created.id());
        List<DraftComputationView> figures = Stream.concat(Stream.of(regimes.chosen()),
                        regimes.priced().stream().map(RegimeComparison.Priced::regime))
                .distinct()
                .map(regime -> computationService.compute(email, created.id(), regime))
                .toList();
        return new DraftFirstFigure(created.id(), answered.revision(), figures);
    }

    private static void requireComplete(DraftFirstFigureRequest request) {
        if (request == null || request.label() == null || request.label().isBlank()) {
            throw new BadRequestException("A first figure needs /label, the draft's label");
        }
        if (request.grossPay() == null || !request.grossPay().isGreaterThan(Money.ZERO)) {
            throw new BadRequestException("A first figure needs /grossPay, the year's gross pay, above nil");
        }
        if (request.dueDate() == null) {
            throw new BadRequestException("A first figure needs /dueDate, the day the return is due");
        }
    }

    /** Step 1's answer as a patch on a blank draft. */
    static JsonNode stepOne(DraftFirstFigureRequest request) {
        ArrayNode patch = MAPPER.createArrayNode();
        ObjectNode component = MAPPER.createObjectNode();
        component.putObject("code").put("value", PAY_COMPONENT);
        component.putObject("annualAmount").put("amountInRupees", request.grossPay().amountInRupees());
        ObjectNode employment = MAPPER.createObjectNode();
        employment.putArray("components").add(component);
        employment.put("termsIncludeDearnessAllowance", false);
        employment.put("employerKind", "OTHER");
        employment.put("dearnessAllowanceCountsForRetirement", false);
        employment.putNull("companyEmployer");
        patch.addObject().put("op", "add").put("path", "/entered/employments/-").set("value", employment);
        patch.addObject().put("op", "add").put("path", "/filing/dueDate").put("value", request.dueDate().toString());
        patch.addObject().put("op", "add").put("path", "/onboarding/answered/-").put("value", OnboardingStep.PAY.name());
        return patch;
    }
}
