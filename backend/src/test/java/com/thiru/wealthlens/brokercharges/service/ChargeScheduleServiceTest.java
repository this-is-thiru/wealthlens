package com.thiru.wealthlens.brokercharges.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeSide;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.engine.ChargeScheduleResolver;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Publishing a rate card.
 *
 * <p>The behaviour that matters is auto-supersede (D6). The superseded implementation set an end
 * date a hundred years out and then rejected any overlap, so publishing a new card meant remembering
 * to close the old one first or watching the write throw — operationally booby-trapped, and the kind
 * of thing that gets worked around by editing the database.
 *
 * <p>Closing the incumbent rather than deactivating it is deliberate and load-bearing: a transaction
 * backdated into the closed window must still find the card that was in force then (ADR-12).
 */
@ExtendWith(MockitoExtension.class)
class ChargeScheduleServiceTest {

    private static final LocalDate APRIL = LocalDate.of(2025, 4, 1);
    private static final LocalDate JULY = LocalDate.of(2025, 7, 1);

    @Mock
    private ChargeScheduleRepository chargeScheduleRepository;

    @Mock
    private ChargeScheduleValidator chargeScheduleValidator;

    @Mock
    private ChargeScheduleResolver chargeScheduleResolver;

    @InjectMocks
    private ChargeScheduleService service;

    // ---------------------------------------------------------------- publish

    @Test
    void publish_validatesBeforeWritingAnything() {
        // Given — a card that would price trades wrongly must not reach the database at all
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_2025_07", JULY);
        doThrow(new BadRequestException("rule STT declares no rate"))
                .when(chargeScheduleValidator).validate(schedule);

        // When / Then
        assertThatThrownBy(() -> service.publish(schedule))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("STT");
        verifyNoInteractions(chargeScheduleRepository);
        verifyNoInteractions(chargeScheduleResolver);
    }

    @Test
    void publish_savesTheCardAndReturnsIt() {
        // Given
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_2025_07", JULY);
        givenNoIncumbent();
        when(chargeScheduleRepository.save(schedule)).thenReturn(schedule);

        // When / Then
        assertThat(service.publish(schedule)).isSameAs(schedule);
    }

    @Test
    void publish_whenAnOpenCardCoversTheSameScope_closesItTheDayBeforeTheNewOneStarts() {
        // Given — AC-8. No manual close step, and no rejection.
        ChargeScheduleEntity incumbent = card("ZERODHA_EQ_2025_04", APRIL);
        ChargeScheduleEntity replacement = card("ZERODHA_EQ_2025_07", JULY);
        givenIncumbent(incumbent);
        when(chargeScheduleRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When
        service.publish(replacement);

        // Then — the windows meet without overlapping and without leaving a day uncovered
        assertThat(incumbent.getEndDate()).isEqualTo(JULY.minusDays(1));
        assertThat(replacement.getEndDate()).isNull();
    }

    @Test
    void publish_whenSuperseding_leavesTheIncumbentsStatusAlone() {
        // Given — ADR-12. Deactivating the old card would make a transaction backdated into its own
        // window resolve nothing and silently accrue no charge, which is exactly what happens when a
        // past quarter is uploaded long after the rates changed.
        ChargeScheduleEntity incumbent = card("ZERODHA_EQ_2025_04", APRIL);
        givenIncumbent(incumbent);
        when(chargeScheduleRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When
        service.publish(card("ZERODHA_EQ_2025_07", JULY));

        // Then
        assertThat(incumbent.getStatus()).isEqualTo(EntityStatus.ACTIVE);
    }

    @Test
    void publish_whenNoOpenCardCoversTheScope_closesNothing() {
        // Given — the first card for a broker
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_2025_04", APRIL);
        givenNoIncumbent();
        when(chargeScheduleRepository.save(schedule)).thenReturn(schedule);

        // When
        service.publish(schedule);

        // Then — saved once, and that once is the new card
        verify(chargeScheduleRepository).save(schedule);
    }

    @Test
    void publish_whenTheOpenCardIsThisSameCard_doesNotCloseItAgainstItself() {
        // Given — correcting a card that is already published. Superseding it would close it the day
        // before its own start and then collide on its unique code.
        ChargeScheduleEntity existing = card("ZERODHA_EQ_2025_04", APRIL);
        ChargeScheduleEntity corrected = card("ZERODHA_EQ_2025_04", APRIL);
        givenIncumbent(existing);
        when(chargeScheduleRepository.save(corrected)).thenReturn(corrected);

        // When
        service.publish(corrected);

        // Then — one save, and it is the correction. Asserted by count rather than by argument:
        // ChargeScheduleEntity is a @Data type, so the two cards are equal and Mockito cannot tell
        // save(existing) from save(corrected).
        assertThat(existing.getEndDate()).isNull();
        verify(chargeScheduleRepository, times(1)).save(any());
    }

    @Test
    void publish_whenTheNewCardWouldStartBeforeTheIncumbent_isRejected() {
        // Given — closing the incumbent the day before this start would end it before it began, and
        // leave a window that resolves nothing
        givenIncumbent(card("ZERODHA_EQ_2025_07", JULY));

        // When / Then
        assertThatThrownBy(() -> service.publish(card("ZERODHA_EQ_2025_04", APRIL)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ZERODHA_EQ_2025_07");
    }

    @Test
    void publish_evictsTheResolverCache() {
        // Given — the resolver holds its answers, misses included, so a newly published card is
        // invisible to the next trade until the cache is cleared
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_2025_07", JULY);
        givenNoIncumbent();
        when(chargeScheduleRepository.save(schedule)).thenReturn(schedule);

        // When
        service.publish(schedule);

        // Then
        verify(chargeScheduleResolver).evictAll();
    }

    @Test
    void publish_whenTheCardCarriesNoStatus_marksItActive() {
        // Given — status says whether a record is legitimate, not whether it is current. An unset
        // one would be ambiguous to every later reader.
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_2025_07", JULY);
        schedule.setStatus(null);
        givenNoIncumbent();
        when(chargeScheduleRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When
        service.publish(schedule);

        // Then
        assertThat(schedule.getStatus()).isEqualTo(EntityStatus.ACTIVE);
    }

    @Test
    void publish_looksForTheIncumbentOnEveryScopeDimension() {
        // Given — a card scoped to one segment must not supersede the card for another
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_INTRADAY_2025_07", JULY);
        schedule.setSegment(TradeSegment.INTRADAY);
        schedule.setExchange("NSE");
        schedule.setPlanCode("PLAN_A");
        givenNoIncumbent();
        when(chargeScheduleRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When
        service.publish(schedule);

        // Then
        verify(chargeScheduleRepository).findOpenScheduleForScope(
                BrokerName.ZERODHA, "EQUITY", "INTRADAY", "NSE", "PLAN_A");
    }

    @Test
    void publish_whenScopeDimensionsAreUnset_looksForACardThatIsAlsoUnscoped() {
        // Given — null means "applies to all", and it has to be carried through as null rather than
        // stringified, or the lookup would match nothing and every publish would create a duplicate
        ChargeScheduleEntity schedule = card("ZERODHA_ALL_2025_07", JULY);
        schedule.setAssetType(null);
        givenNoIncumbent();
        when(chargeScheduleRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When
        service.publish(schedule);

        // Then
        verify(chargeScheduleRepository).findOpenScheduleForScope(
                BrokerName.ZERODHA, null, null, null, null);
    }

    // ---------------------------------------------------------------- read and close

    @Test
    void findByCode_returnsTheCard() {
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_2025_04", APRIL);
        when(chargeScheduleRepository.findByScheduleCode("ZERODHA_EQ_2025_04"))
                .thenReturn(Optional.of(schedule));

        assertThat(service.findByCode("ZERODHA_EQ_2025_04")).isSameAs(schedule);
    }

    @Test
    void findByCode_whenThereIsNoSuchCard_isRejectedByName() {
        when(chargeScheduleRepository.findByScheduleCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("NOPE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void close_setsTheEndDateAndEvictsTheCache() {
        // Given — a broker withdrawing a card without replacing it
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_2025_04", APRIL);
        when(chargeScheduleRepository.findByScheduleCode("ZERODHA_EQ_2025_04"))
                .thenReturn(Optional.of(schedule));
        when(chargeScheduleRepository.save(schedule)).thenReturn(schedule);

        // When
        ChargeScheduleEntity closed = service.close("ZERODHA_EQ_2025_04", JULY);

        // Then
        assertThat(closed).isSameAs(schedule);
        assertThat(closed.getEndDate()).isEqualTo(JULY);
        assertThat(schedule.getStatus()).isEqualTo(EntityStatus.ACTIVE);
        verify(chargeScheduleResolver).evictAll();
    }

    @Test
    void close_whenTheEndDatePrecedesTheStart_isRejected() {
        ChargeScheduleEntity schedule = card("ZERODHA_EQ_2025_07", JULY);
        when(chargeScheduleRepository.findByScheduleCode("ZERODHA_EQ_2025_07"))
                .thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.close("ZERODHA_EQ_2025_07", APRIL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("end date");
    }

    @Test
    void findByBroker_listsEveryCardOnFileForThatBroker() {
        // Given
        List<ChargeScheduleEntity> cards = List.of(card("A", APRIL), card("B", JULY));
        when(chargeScheduleRepository.findByBrokerNameOrderByStartDateDesc(BrokerName.ZERODHA))
                .thenReturn(cards);

        // When / Then
        assertThat(service.findByBroker(BrokerName.ZERODHA)).isEqualTo(cards);
    }

    @Test
    void findUnverified_listsCardsWhoseRatesNobodyHasChecked() {
        // Given — seeded cards carry placeholder rates until a human compares them against the
        // broker's published page, and an unverified card is a known gap rather than a defect
        List<ChargeScheduleEntity> cards = List.of(card("A", APRIL));
        when(chargeScheduleRepository.findByVerifiedOnIsNull()).thenReturn(cards);

        // When / Then
        assertThat(service.findUnverified()).isEqualTo(cards);
    }

    // ---------------------------------------------------------------- fixtures

    private void givenNoIncumbent() {
        when(chargeScheduleRepository.findOpenScheduleForScope(any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    private void givenIncumbent(ChargeScheduleEntity incumbent) {
        when(chargeScheduleRepository.findOpenScheduleForScope(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(incumbent));
    }

    private static ChargeScheduleEntity card(String code, LocalDate startDate) {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setScheduleCode(code);
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setAssetType(AssetType.EQUITY);
        schedule.setStartDate(startDate);
        schedule.setStatus(EntityStatus.ACTIVE);
        schedule.setRules(new ArrayList<>(List.of(brokerage())));
        return schedule;
    }

    private static ChargeRule brokerage() {
        ChargeRule rule = new ChargeRule();
        rule.setCode("BROKERAGE");
        rule.setBasis(ChargeBasis.FLAT);
        rule.setCategory(ChargeCategory.BROKERAGE);
        rule.setSide(ChargeSide.BOTH);
        rule.setEvents(Set.of(ChargeEvent.BUY, ChargeEvent.SELL));
        rule.setFlatAmount(20.0);
        rule.setOrder(10);
        rule.setActive(true);
        return rule;
    }
}
