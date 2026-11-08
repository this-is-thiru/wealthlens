package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.api.ProvidentFundTerms;
import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the user lets the optimiser change on a draft (W-238b; {@code 03-advisory.md} A2.1, A2.2): the components the
 * employer will let move, the one that funds them, and the least cash after tax the user accepts.
 *
 * @param movable the codes of the components that may move
 * @param movableFunds the {@code EmployerFund} names of the employer contributions that may move; none when absent
 *     (W-238c)
 * @param contributions the codes of the employee's own contributions that may rise above what is entered, paid from
 *     take-home; none when absent (W-238k)
 * @param residual the code of the taxable component that funds every move
 * @param cashFloor the least cash take-home after tax, in rupees
 * @param verificationBreadth how many components the exhaustive check covers; the setting when absent
 * @param providentFund how the provident fund moves with basic (W-238i, W-238j); each share following basic, the base
 *     not stated, when absent
 */
public record DraftOptimisationRequest(List<String> movable, Optional<List<String>> movableFunds,
        Optional<List<String>> contributions, String residual, String cashFloor, Optional<Integer> verificationBreadth,
        Optional<DraftProvidentFundTerms> providentFund) {

    public DraftOptimisationRequest {
        if (movable == null || residual == null || cashFloor == null) {
            throw new BadRequestException("An optimisation needs movable, residual and cashFloor");
        }
        movable = List.copyOf(movable);
        movableFunds = movableFunds == null ? Optional.empty() : movableFunds.map(List::copyOf);
        contributions = contributions == null ? Optional.empty() : contributions.map(List::copyOf);
        if (verificationBreadth == null) {
            verificationBreadth = Optional.empty();
        }
        if (providentFund == null) {
            providentFund = Optional.empty();
        }
    }

    /** The provident fund terms, each share following basic where the request does not say otherwise (W-238i). */
    ProvidentFundTerms providentFundTerms() {
        return providentFund.map(DraftProvidentFundTerms::toTerms).orElse(ProvidentFundTerms.FOLLOW_BASIC);
    }

    /** What may move, as the engine's (W-238k C6a). */
    AllocationSpace.Moves moves() {
        return new AllocationSpace.Moves(movable.stream().map(ComponentCode::new).collect(Collectors.toUnmodifiableSet()),
                movableFundNames(), contributions.orElse(List.of()).stream().map(ComponentCode::new)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * The funds named, as the engine's.
     *
     * @throws BadRequestException if a name is not a fund
     */
    Set<EmployerFund> movableFundNames() {
        return movableFunds.orElse(List.of()).stream().map(DraftOptimisationRequest::fund)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static EmployerFund fund(String name) {
        return Arrays.stream(EmployerFund.values()).filter(fund -> fund.name().equals(name)).findFirst()
                .orElseThrow(() -> new BadRequestException("movableFunds names no fund \"" + name + "\"; the funds are "
                        + Arrays.toString(EmployerFund.values())));
    }

    ComponentCode residualCode() {
        return new ComponentCode(residual);
    }

    /**
     * The floor as money.
     *
     * @throws BadRequestException if it is not an amount in rupees
     */
    Money cashFloorRupees() {
        try {
            return Money.of(cashFloor);
        } catch (NumberFormatException notAnAmount) {
            throw new BadRequestException("cashFloor must be an amount in rupees, such as \"0\", not \"" + cashFloor + "\"");
        }
    }
}
