package com.thiru.wealthlens.portfolio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.exception.TradeCollisionException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TradeCollisionRetryTest {

    private static final UserMail USER = UserMail.from("retry@example.com");

    @Mock
    private PortfolioService portfolioService;

    @InjectMocks
    private TradeCollisionRetry tradeCollisionRetry;

    @Test
    void addTransactionV2_whenTheFirstAttemptCollides_recordsOnTheSecond() {
        // Given — one collision, then success
        when(portfolioService.addTransactionV2(eq(USER), any(), anyList()))
                .thenThrow(collision())
                .thenReturn("Stock buy added to portfolio");

        // When
        String answer = tradeCollisionRetry.addTransactionV2(USER, request());

        // Then
        assertEquals("Stock buy added to portfolio", answer);
        verify(portfolioService, times(2)).addTransactionV2(eq(USER), any(), anyList());
    }

    @Test
    void addTransaction_whenEveryAttemptCollides_answersThatTheTradeWasNotRecorded() {
        // Given — every attempt collides: the first and five more
        DataIntegrityViolationException collision = collision();
        when(portfolioService.addTransaction(eq(USER), any(), anyList())).thenThrow(collision);

        // When
        TradeCollisionException refused =
                assertThrows(TradeCollisionException.class, () -> tradeCollisionRetry.addTransaction(USER, request()));

        // Then
        assertEquals(TradeCollisionRetry.NOT_RECORDED, refused.getMessage());
        verify(portfolioService, times(6)).addTransaction(eq(USER), any(), anyList());
    }

    @Test
    void addTransactionV2_whenTheFailureIsNotACollision_answersItAtOnce() {
        // Given — the caller's mistake
        BadRequestException mistake = new BadRequestException("Quantity must be greater than zero, but was: 0.0");
        when(portfolioService.addTransactionV2(eq(USER), any(), anyList())).thenThrow(mistake);

        // When
        BadRequestException answered =
                assertThrows(BadRequestException.class, () -> tradeCollisionRetry.addTransactionV2(USER, request()));

        // Then — the same failure, one attempt
        assertSame(mistake, answered);
        verify(portfolioService, times(1)).addTransactionV2(eq(USER), any(), anyList());
    }

    @Test
    void addTransactionV2_whenRetried_givesEachAttemptItsOwnCopyOfTheRequest() {
        // Given — the first attempt changes the request it is given, then collides
        List<AssetRequest> seen = new ArrayList<>();
        when(portfolioService.addTransactionV2(eq(USER), any(), anyList())).thenAnswer(call -> {
            AssetRequest given = call.getArgument(1);
            seen.add(given);
            if (seen.size() == 1) {
                given.setTempTransactionId("changed by the first attempt");
                throw collision();
            }
            return "Stock buy added to portfolio";
        });
        AssetRequest original = request();

        // When
        tradeCollisionRetry.addTransactionV2(USER, original);

        // Then — two distinct copies; the second, and the original, unchanged
        assertEquals(2, seen.size());
        assertNotSame(original, seen.get(0));
        assertNotSame(seen.get(0), seen.get(1));
        assertEquals(null, seen.get(1).getTempTransactionId());
        assertEquals(null, original.getTempTransactionId());
        assertEquals(10.0, seen.get(1).getQuantity());
    }

    @Test
    void isCollision_whenNoCauseCarriesTheLabel_isFalse() {
        // Given — a Mongo failure without the transient label, inside Spring's wrapper
        DataIntegrityViolationException unlabelled =
                new DataIntegrityViolationException("duplicate key", new MongoException(11000, "E11000 duplicate key"));

        // When / Then
        assertTrue(TradeCollisionRetry.isCollision(collision()));
        assertEquals(false, TradeCollisionRetry.isCollision(unlabelled));
    }

    @Test
    void addTransactionV2_whenTheTradeThrowsAnError_letsItThrough() {
        // Given -- not an exception a trade has, but a JVM error (W-282)
        StackOverflowError error = new StackOverflowError("deep");
        when(portfolioService.addTransactionV2(eq(USER), any(), anyList())).thenThrow(error);

        // When / Then -- the same error, one attempt
        assertSame(error, assertThrows(StackOverflowError.class, () -> tradeCollisionRetry.addTransactionV2(USER, request())));
        verify(portfolioService, times(1)).addTransactionV2(eq(USER), any(), anyList());
    }

    @Test
    void addTransactionV2_whenTheTradeThrowsACheckedException_wrapsIt() {
        // Given -- no trade path declares one, so reaching here is a defect (W-282)
        Exception checked = new IOException("unexpected");
        when(portfolioService.addTransactionV2(eq(USER), any(), anyList())).thenAnswer(call -> {
            throw checked;
        });

        // When
        IllegalStateException wrapped = assertThrows(IllegalStateException.class,
                () -> tradeCollisionRetry.addTransactionV2(USER, request()));

        // Then -- wrapped once, the cause kept
        assertSame(checked, wrapped.getCause());
        verify(portfolioService, times(1)).addTransactionV2(eq(USER), any(), anyList());
    }

    private static DataIntegrityViolationException collision() {
        MongoException conflict = new MongoException(112, "WriteConflict");
        conflict.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        return new DataIntegrityViolationException("Command failed with error 112 (WriteConflict)", conflict);
    }

    private static AssetRequest request() {
        AssetRequest request = new AssetRequest();
        request.setStockCode("INFY");
        request.setQuantity(10.0);
        request.setPrice(100.0);
        return request;
    }
}
