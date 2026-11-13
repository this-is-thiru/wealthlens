package com.thiru.wealthlens.taxplanning.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A draft save that collides with another save of its revision is run again, and nothing else is (W-310). */
@ExtendWith(MockitoExtension.class)
class DraftSaveRetryTest {

    private static final String EMAIL = "drafter@example.com";
    private static final String DRAFT = "draft-1";
    private static final JsonNode PATCH = JsonMapper.builder().build().readTree("[]");

    @Mock
    private DraftService draftService;

    @InjectMocks
    private DraftSaveRetry draftSaveRetry;

    @Test
    void patch_whenTheFirstAttemptCollides_savesAgainAndReturnsTheSecond() {
        // Given
        DraftView saved = mock(DraftView.class);
        when(draftService.patch(EMAIL, DRAFT, 0, PATCH))
                .thenThrow(new DataIntegrityViolationException("write conflict"))
                .thenReturn(saved);

        // When
        DraftView result = draftSaveRetry.patch(EMAIL, DRAFT, 0, PATCH);

        // Then
        assertThat(result).isSameAs(saved);
        verify(draftService, times(2)).patch(EMAIL, DRAFT, 0, PATCH);
    }

    @Test
    void patch_whenEveryAttemptCollides_answersThatTheOtherSaveHadNotFinished() {
        // Given — the first attempt and the policy's five re-runs all collide
        DraftConflictException unfinished = new DraftConflictException("had not finished", null, List.of());
        when(draftService.patch(EMAIL, DRAFT, 0, PATCH)).thenThrow(new DataIntegrityViolationException("write conflict"));
        when(draftService.conflictAfterRace(EMAIL, DRAFT)).thenReturn(unfinished);

        // When
        Throwable thrown = catchThrowable(() -> draftSaveRetry.patch(EMAIL, DRAFT, 0, PATCH));

        // Then
        assertThat(thrown).isSameAs(unfinished);
        verify(draftService, times(6)).patch(EMAIL, DRAFT, 0, PATCH);
    }

    @Test
    void patch_whenTheDraftHasMovedOn_answersAtOnceWithoutSavingAgain() {
        // Given
        DraftConflictException stale = new DraftConflictException("moved on", null, List.of("/entered/employments/-"));
        when(draftService.patch(EMAIL, DRAFT, 0, PATCH)).thenThrow(stale);

        // When
        Throwable thrown = catchThrowable(() -> draftSaveRetry.patch(EMAIL, DRAFT, 0, PATCH));

        // Then
        assertThat(thrown).isSameAs(stale);
        verify(draftService, times(1)).patch(EMAIL, DRAFT, 0, PATCH);
    }

    @Test
    void patch_whenTheResultWouldNotBind_answersAtOnceWithoutSavingAgain() {
        // Given
        BadRequestException refused = new BadRequestException("does not bind");
        when(draftService.patch(EMAIL, DRAFT, 0, PATCH)).thenThrow(refused);

        // When
        Throwable thrown = catchThrowable(() -> draftSaveRetry.patch(EMAIL, DRAFT, 0, PATCH));

        // Then
        assertThat(thrown).isSameAs(refused);
        verify(draftService, times(1)).patch(EMAIL, DRAFT, 0, PATCH);
    }
}
