package com.thiru.wealthlens.taxengine.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * How an allowance was used, as entered for its exemption (S4 r.280; Rules 1962 r.2BB; D-101): the amount actually
 * spent for a duty allowance, or the children and months covered for a prescribed one. Figures are fixtures.
 */
class AllowanceUseTest {

    private static final ComponentCode UNIFORM = new ComponentCode("UNIFORM_ALLOWANCE");
    private static final ComponentCode CHILDREN_EDUCATION = new ComponentCode("CHILDREN_EDUCATION_ALLOWANCE");

    @Test
    void construct_whenAnAmountIncurred_carriesIt() {
        // When — Sch. III Sl. 12: "to the extent actually incurred"
        AllowanceUse uniform = AllowanceUse.spent(UNIFORM, Money.of("8000"));

        // Then
        assertThat(uniform.amountIncurred()).contains(Money.of("8000"));
        assertThat(uniform.childrenCovered()).isEmpty();
    }

    @Test
    void construct_whenChildrenAndMonths_carriesThem() {
        // When — Sl. 13: per month per child
        AllowanceUse education = AllowanceUse.forChildren(CHILDREN_EDUCATION, 2, 12);

        // Then
        assertThat(education.childrenCovered()).contains(2);
        assertThat(education.monthsCovered()).contains(12);
        assertThat(education.amountIncurred()).isEmpty();
    }

    @Test
    void construct_whenNeitherAnAmountNorChildrenAndMonths_isRefused() {
        assertThatThrownBy(() -> new AllowanceUse(UNIFORM, Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNIFORM_ALLOWANCE");
    }

    @Test
    void construct_whenChildrenWithoutMonths_isRefused() {
        assertThatThrownBy(() -> new AllowanceUse(CHILDREN_EDUCATION, Optional.empty(), Optional.of(2),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("months");
    }

    @Test
    void construct_whenOutOfRange_isRefused() {
        assertThatThrownBy(() -> AllowanceUse.forChildren(CHILDREN_EDUCATION, 0, 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AllowanceUse.forChildren(CHILDREN_EDUCATION, 2, 13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("12");
        assertThatThrownBy(() -> AllowanceUse.forChildren(CHILDREN_EDUCATION, 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AllowanceUse.spent(UNIFORM, Money.of("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construct_whenNoComponent_isRefused() {
        assertThatThrownBy(() -> AllowanceUse.spent(null, Money.of("8000")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
