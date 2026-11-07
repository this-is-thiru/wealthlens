package com.thiru.wealthlens.taxplanning.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.taxengine.input.FairMarketValueBasis;
import com.thiru.wealthlens.taxengine.input.MonthOfPay;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** A draft's inputs bind to the engine's {@code EnteredFacts}, on which storing them as one model stands (D-441 C1a). */
class EnteredFactsBindingTest {

    private static JsonNode withEmployment(String amount) {
        JsonNode blank = DraftInputsMapper.tree(DraftInputsMapper.blank());
        return JsonPatchApplier.apply(blank, DraftInputsMapper.tree("""
                [{"op": "add", "path": "/entered/employments/-", "value": {
                  "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": %s}}],
                  "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
                  "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}}]""".formatted(amount)));
    }

    @Test
    void blank_whenRead_bindsToNoEntries() {
        // When / Then
        DraftInputsMapper.requireBinds(DraftInputsMapper.tree(DraftInputsMapper.blank()));
    }

    @Test
    void requireBinds_whenAFieldIsMisspelt_namesWhereWithoutTheValue() {
        // Given
        JsonNode tree = JsonPatchApplier.apply(DraftInputsMapper.tree(DraftInputsMapper.blank()),
                DraftInputsMapper.tree("[{\"op\": \"add\", \"path\": \"/employmentz\", \"value\": \"ABCDE1234F\"}]"));

        // When / Then
        assertThatThrownBy(() -> DraftInputsMapper.requireBinds(tree))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("employmentz")
                .hasMessageNotContaining("ABCDE1234F");
    }

    @Test
    void requireBinds_whenAnEmploymentIsEntered_keepsItsMoneyExact() {
        // When
        JsonNode tree = withEmployment("175000.50");

        // Then
        DraftInputsMapper.requireBinds(tree);
        assertThat(DraftInputsMapper.tree(DraftInputsMapper.text(tree)).at("/entered/employments/0/components/0/annualAmount/amountInRupees")
                .decimalValue()).isEqualByComparingTo("175000.50");
    }

    @Test
    void mapper_whenAPayMonthIsKeyedByComponent_readsTheKeyAndTheMonth() {
        // When
        MonthOfPay month = DraftInputsMapper.MAPPER.readValue(
                "{\"month\": \"2026-01\", \"amounts\": {\"BASIC\": {\"amountInRupees\": 100}}}", MonthOfPay.class);

        // Then
        assertThat(month.amounts()).containsKey(new ComponentCode("BASIC"));
        assertThat(month.month()).isEqualTo(YearMonth.of(2026, 1));
    }

    /** Every abstract type reachable from the inputs has a mix-in, and every map a key JSON can state. */
    @Test
    void enteredFacts_whenWalked_holdsOnlyWhatJsonCanBind() {
        // When
        Set<Type> abstractTypes = new HashSet<>();
        Set<String> badMapKeys = new HashSet<>();
        walk(DraftInputs.class, new HashSet<>(), abstractTypes, badMapKeys);

        // Then
        assertThat(abstractTypes).containsExactly(FairMarketValueBasis.class);
        assertThat(badMapKeys).isEmpty();
    }

    private static void walk(Type type, Set<Type> seen, Set<Type> abstractTypes, Set<String> badMapKeys) {
        if (!seen.add(type)) {
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            checkMapKey(parameterized, badMapKeys);
            for (Type argument : parameterized.getActualTypeArguments()) {
                walk(argument, seen, abstractTypes, badMapKeys);
            }
        } else if (type instanceof Class<?> candidate && candidate.getName().startsWith("com.thiru.wealthlens")) {
            walkClass(candidate, seen, abstractTypes, badMapKeys);
        }
    }

    private static void walkClass(Class<?> candidate, Set<Type> seen, Set<Type> abstractTypes, Set<String> badMapKeys) {
        if (candidate.isInterface()) {
            abstractTypes.add(candidate);
        } else if (candidate.isRecord()) {
            for (RecordComponent component : candidate.getRecordComponents()) {
                walk(component.getGenericType(), seen, abstractTypes, badMapKeys);
            }
        }
    }

    private static void checkMapKey(ParameterizedType parameterized, Set<String> badMapKeys) {
        if (Map.class.isAssignableFrom((Class<?>) parameterized.getRawType())) {
            Type key = parameterized.getActualTypeArguments()[0];
            boolean statable = key == String.class || key == Integer.class || key == ComponentCode.class || key == TaxYearId.class
                    || (key instanceof Class<?> keyClass && keyClass.isEnum());
            if (!statable) {
                badMapKeys.add(parameterized.toString());
            }
        }
    }
}
