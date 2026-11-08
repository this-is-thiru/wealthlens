package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.exception.BadRequestException;
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
 * @param residual the code of the taxable component that funds every move
 * @param cashFloor the least cash take-home after tax, in rupees
 * @param verificationBreadth how many components the exhaustive check covers; the setting when absent
 */
public record DraftOptimisationRequest(List<String> movable, Optional<List<String>> movableFunds, String residual,
        String cashFloor, Optional<Integer> verificationBreadth) {

    public DraftOptimisationRequest {
        if (movable == null || residual == null || cashFloor == null) {
            throw new BadRequestException("An optimisation needs movable, residual and cashFloor");
        }
        movable = List.copyOf(movable);
        movableFunds = movableFunds == null ? Optional.empty() : movableFunds.map(List::copyOf);
        if (verificationBreadth == null) {
            verificationBreadth = Optional.empty();
        }
    }

    Set<ComponentCode> movableCodes() {
        return movable.stream().map(ComponentCode::new).collect(Collectors.toUnmodifiableSet());
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
