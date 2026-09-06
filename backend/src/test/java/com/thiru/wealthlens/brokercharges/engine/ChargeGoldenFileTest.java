package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contract notes, frozen.
 *
 * <p>Each fixture is a trade and the charges it should attract, computed by hand from the shipped
 * rate card and checked line by line. Every other test in this package exercises one decision in
 * isolation; these price a whole trade through the resolver, the engine and all seven calculators
 * against the cards this application actually ships — so a change that is locally reasonable and
 * globally wrong shows up as a wrong contract note.
 *
 * <p>Lines are asserted individually as well as in total. A total can be right while two components
 * are compensating errors, and that is precisely the kind of defect a broker's customer notices
 * before we do.
 *
 * <p>The rates behind these numbers are placeholders (ADR-18), so the fixtures pin the arithmetic
 * rather than reality. When real rates arrive the expected figures change and this suite is the
 * thing that says which trades were affected.
 */
class ChargeGoldenFileTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFiles")
    @DisplayName("the shipped rate cards produce the expected contract note")
    void goldenContractNote(String name, JsonNode fixture) {
        // Given
        ChargeContext context = contextFrom(fixture.get("context"));

        // When
        ChargeComputation computation = engineFor(fixture).compute(context);

        // Then — every line, then the total. A right total can hide two wrong components.
        JsonNode expectedLines = fixture.get("expected").get("lines");
        Map<String, Double> actual = computation.amountByCode();

        expectedLines.propertyStream().forEach(entry ->
                assertMoney(name + " / " + entry.getKey(),
                        entry.getValue().asDouble(),
                        actual.getOrDefault(entry.getKey(), 0.0)));

        assertThat(actual.keySet())
                .as("%s charges exactly the codes the contract note lists", name)
                .containsExactlyInAnyOrderElementsOf(expectedLines.propertyStream()
                        .map(Map.Entry::getKey).toList());

        assertMoney(name + " / total", fixture.get("expected").get("total").asDouble(), computation.total());
    }

    // ---------------------------------------------------------------- harness

    /** The real resolver over the real shipped cards, the real engine, the real calculators. */
    private ChargeEngine engineFor(JsonNode fixture) {
        ChargeScheduleRepository scheduleRepository = mock(ChargeScheduleRepository.class);
        when(scheduleRepository.findCandidates(any(), any())).thenAnswer(call ->
                shippedCards().stream()
                        .filter(card -> card.getBrokerName() == call.getArgument(0))
                        .filter(card -> !card.getStartDate().isAfter(call.getArgument(1)))
                        .toList());

        UserChargeRepository userChargeRepository = mock(UserChargeRepository.class);
        List<String> alreadyCharged = new ArrayList<>();
        if (fixture.has("alreadyChargedToday")) {
            fixture.get("alreadyChargedToday").forEach(node -> alreadyCharged.add(node.asString()));
        }
        when(userChargeRepository.existsChargeForScripOnDate(
                anyString(), anyString(), any(), anyString(), any(), anyString()))
                .thenAnswer(call -> alreadyCharged.contains(call.getArgument(5)));

        ChargeInstrumentResolver instrumentResolver = mock(ChargeInstrumentResolver.class);
        when(instrumentResolver.resolve(any())).thenReturn(instrumentFrom(fixture));

        ChargeFormulaEvaluator evaluator = new ChargeFormulaEvaluator();
        ChargeCalculatorRegistry registry = new ChargeCalculatorRegistry(List.of(
                new TurnoverChargeCalculator(),
                new FlatChargeCalculator(),
                new PerUnitChargeCalculator(),
                new SlabChargeCalculator(),
                new ScopedFlatChargeCalculator(userChargeRepository),
                new DerivedChargeCalculator(),
                new FormulaChargeCalculator(evaluator)));

        return new ChargeEngine(new ChargeScheduleResolver(scheduleRepository),
                instrumentResolver, registry, evaluator);
    }

    private static Optional<ChargeInstrumentEntity> instrumentFrom(JsonNode fixture) {
        if (!fixture.has("instrument")) {
            return Optional.empty();
        }
        ChargeInstrumentEntity instrument = new ChargeInstrumentEntity();
        instrument.setId("golden-profile");
        instrument.setStockCode("GOLDEN");
        instrument.setEquityOriented(fixture.get("instrument").get("equityOriented").asBoolean());
        instrument.setRules(new ArrayList<>());
        return Optional.of(instrument);
    }

    private static ChargeContext contextFrom(JsonNode node) {
        double price = node.get("price").asDouble();
        double quantity = node.get("quantity").asDouble();

        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, price * quantity);

        return new ChargeContext(
                "investor@example.com", "txn-golden", "ord-golden", "GOLDEN", "self",
                BrokerName.valueOf(node.get("brokerName").asString()),
                AssetType.valueOf(node.get("assetType").asString()),
                optionalEnum(node, "segment"),
                text(node, "exchange"),
                null,
                ChargeEvent.valueOf(node.get("event").asString()),
                LocalDate.parse(node.get("transactionDate").asString()),
                null, quantity, price, 1, baseAmounts, List.of(), new HashMap<>());
    }

    private static TradeSegment optionalEnum(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : TradeSegment.valueOf(value);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static List<ChargeScheduleEntity> shippedCards() {
        return read("classpath*:data/charges/*.json").stream()
                .filter(resource -> !"charge-catalogue.json".equals(resource.getFilename()))
                .map(resource -> parse(resource, ChargeScheduleEntity.class))
                .toList();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> goldenFiles() {
        return read("classpath*:charges/golden/*.json").stream()
                .map(resource -> parse(resource, JsonNode.class))
                .map(fixture -> org.junit.jupiter.params.provider.Arguments.of(
                        fixture.get("name").asString(), fixture));
    }

    private static List<Resource> read(String pattern) {
        try {
            return List.of(new PathMatchingResourcePatternResolver().getResources(pattern));
        } catch (IOException e) {
            throw new IllegalStateException("Could not list " + pattern, e);
        }
    }

    private static <T> T parse(Resource resource, Class<T> type) {
        try (InputStream stream = resource.getInputStream()) {
            return MAPPER.readValue(stream, type);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read " + resource.getFilename(), e);
        }
    }
}
