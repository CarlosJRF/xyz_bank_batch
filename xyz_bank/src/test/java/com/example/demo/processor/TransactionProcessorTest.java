package com.example.demo.processor;

import static com.example.demo.processor.ProcessorTestSupport.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.DTOs.TransactionDTO;
import com.example.demo.input.TransactionInput;
import com.example.demo.validation.DataQualityLog;
import com.example.demo.validation.ErrorCode;

class TransactionProcessorTest {

    private final DataQualityLog log = mock(DataQualityLog.class);
    private final TransactionProcessor processor = new TransactionProcessor(log);

    private static TransactionInput row(String id, String date, String amount, String type) {
        return new TransactionInput(2, String.join(",", id, date, amount, type), id, date, amount, type);
    }

    @Test
    void acceptsValidTransactionAndNormalizesLegacyDate() {
        TransactionDTO result = processor.process(row("7", "24/10/2024", "1500", "credito"));

        assertThat(result.getId()).isEqualTo(7);
        assertThat(result.getTransactionDate()).isEqualTo(LocalDate.of(2024, 10, 24));
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.getType()).isEqualTo("CREDITO");
        verify(log).corrected(eq(TransactionProcessor.STEP_NAME), anyLong(),
                eq(List.of(ErrorCode.DATE_FORMAT_NORMALIZED)), anyString());
    }

    @Test
    void acceptsAllKnownDateFormats() {
        assertThat(processor.process(row("1", "2024-03-05", "1", "debito")).getTransactionDate())
                .isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(processor.process(row("2", "2024/03/05", "1", "debito")).getTransactionDate())
                .isEqualTo(LocalDate.of(2024, 3, 5));
        assertThat(processor.process(row("3", "05-03-2024", "1", "debito")).getTransactionDate())
                .isEqualTo(LocalDate.of(2024, 3, 5));
    }

    @Test
    void rejectsImpossibleDate() {
        assertRejected(() -> processor.process(row("1", "2024-13-01", "700", "debito")), ErrorCode.INVALID_DATE);
        assertRejected(() -> processor.process(row("2", "30/02/2024", "700", "debito")), ErrorCode.INVALID_DATE);
        assertRejected(() -> processor.process(row("3", "", "700", "debito")), ErrorCode.MISSING_DATE);
    }

    @Test
    void rejectsMissingNegativeZeroAndNonNumericAmounts() {
        assertRejected(() -> processor.process(row("1", "2024-01-01", "", "debito")), ErrorCode.MISSING_AMOUNT);
        assertRejected(() -> processor.process(row("2", "2024-01-01", "-200", "debito")), ErrorCode.NEGATIVE_AMOUNT);
        assertRejected(() -> processor.process(row("3", "2024-01-01", "0", "debito")), ErrorCode.ZERO_AMOUNT);
        assertRejected(() -> processor.process(row("4", "2024-01-01", "abc", "debito")), ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void rejectsUnknownTransactionTypes() {
        assertRejected(() -> processor.process(row("1", "2024-01-01", "100", "invalido")),
                ErrorCode.INVALID_TRANSACTION_TYPE);
        assertRejected(() -> processor.process(row("2", "2024-01-01", "100", "desconocido")),
                ErrorCode.INVALID_TRANSACTION_TYPE);
    }

    @Test
    void reportsEveryErrorOfTheRecord() {
        assertRejected(() -> processor.process(row("x", "2024-13-01", "", "invalido")),
                ErrorCode.INVALID_ID, ErrorCode.INVALID_DATE, ErrorCode.MISSING_AMOUNT,
                ErrorCode.INVALID_TRANSACTION_TYPE);
    }

    @Test
    void rejectsRepeatedIdButNotTheSameItemReprocessed() {
        TransactionInput first = row("9", "2024-01-01", "100", "debito");
        processor.process(first);
        processor.process(first); // reprocesamiento del mismo ítem tras un rollback: no es duplicado

        assertRejected(() -> processor.process(row("9", "2024-01-02", "200", "credito")),
                ErrorCode.DUPLICATE_RECORD);

        processor.beforeStep(null);
        assertThat(processor.process(row("9", "2024-01-02", "200", "credito"))).isNotNull();
    }
}
