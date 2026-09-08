package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeInstrumentRepository;
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

        // Why nothing was charged, where a fixture says so. A contract note costing zero is
        // otherwise vacuous: a scheme profile that failed to load and a holding period that puts the
        // lot outside the load are the same number and completely different facts, and only one of
        // them is the behaviour being pinned.
        JsonNode expectedResolution = fixture.get("expected").get("resolution");
        if (expectedResolution != null && !expectedResolution.isNull()) {
            assertThat(computation.resolution())
                    .as("%s resolution", name)
                    .isEqualTo(ChargeResolution.valueOf(expectedResolution.asString()));
        }
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

        // The real instrument resolver over the shipped profiles, so a fixture naming a scheme is
        // priced by the exit load this application actually ships rather than by a stub of one.
        ChargeInstrumentRepository instrumentRepository = mock(ChargeInstrumentRepository.class);
        when(instrumentRepository.findCandidates(anyString(), any())).thenAnswer(call ->
                instrumentProfiles(fixture).stream()
                        .filter(profile -> profile.getStockCode().equals(call.getArgument(0)))
                        .filter(profile -> !profile.getStartDate().isAfter(call.getArgument(1)))
                        .toList());
        ChargeInstrumentResolver instrumentResolver = new ChargeInstrumentResolver(instrumentRepository);

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

    /**
     * Every shipped scheme profile, plus the one a fixture declares inline.
     *
     * <p>An inline profile carries an attribute and no rules — it exists to vary one scheme fact and
     * watch a broker-owned rule react. A fixture naming a {@code stockCode} instead is priced by the
     * shipped profile of that name, which is what makes the exit-load fixtures end to end.
     */
    private static List<ChargeInstrumentEntity> instrumentProfiles(JsonNode fixture) {
        List<ChargeInstrumentEntity> profiles = new ArrayList<>(shippedProfiles());
        if (fixture.has("instrument")) {
            ChargeInstrumentEntity inline = new ChargeInstrumentEntity();
            inline.setId("golden-profile");
            inline.setStockCode(stockCode(fixture.get("context")));
            inline.setStartDate(LocalDate.MIN);
            inline.setEquityOriented(fixture.get("instrument").get("equityOriented").asBoolean());
            inline.setRules(new ArrayList<>());
            profiles.add(inline);
        }
        return profiles;
    }

    private static ChargeContext contextFrom(JsonNode node) {
        double price = node.get("price").asDouble();
        double quantity = node.get("quantity").asDouble();

        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, price * quantity);

        return new ChargeContext(
                "investor@example.com", "txn-golden", "ord-golden", stockCode(node), "self",
                BrokerName.valueOf(node.get("brokerName").asString()),
                assetType(node),
                optionalEnum(node, "segment"),
                text(node, "exchange"),
                null,
                ChargeEvent.valueOf(node.get("event").asString()),
                LocalDate.parse(node.get("transactionDate").asString()),
                null, quantity, price, 1, baseAmounts, lotsFrom(node), new HashMap<>());
    }

    /** {@code GOLDEN} unless the fixture names a scheme, which is how it reaches a shipped profile. */
    private static String stockCode(JsonNode node) {
        String value = text(node, "stockCode");
        return value == null ? "GOLDEN" : value;
    }

    /**
     * The FIFO lots a disposal consumed. Empty for a purchase, and for a sale of something whose
     * charges do not depend on how long it was held.
     */
    private static List<LotSlice> lotsFrom(JsonNode node) {
        JsonNode lots = node.get("lots");
        if (lots == null || lots.isNull()) {
            return List.of();
        }
        return lots.valueStream()
                .map(lot -> new LotSlice(
                        lot.get("quantity").asDouble(),
                        LocalDate.parse(lot.get("acquisitionDate").asString()),
                        lot.get("price").asDouble()))
                .toList();
    }

    /** Null for an account-level event such as a maintenance cycle, which names no asset type. */
    private static AssetType assetType(JsonNode node) {
        String value = text(node, "assetType");
        return value == null ? null : AssetType.valueOf(value);
    }

    private static TradeSegment optionalEnum(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : TradeSegment.valueOf(value);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static List<ChargeInstrumentEntity> shippedProfiles() {
        return read("classpath*:data/charges/instruments/*.json").stream()
                .map(resource -> parse(resource, ChargeInstrumentEntity.class))
                .toList();
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
