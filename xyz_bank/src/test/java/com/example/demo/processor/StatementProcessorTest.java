package com.example.demo.processor;

import static com.example.demo.processor.ProcessorTestSupport.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.DTOs.StatementDTO;
import com.example.demo.input.StatementInput;
import com.example.demo.validation.DataQualityLog;
import com.example.demo.validation.ErrorCode;

class StatementProcessorTest {

    private final DataQualityLog log = mock(DataQualityLog.class);
    private final StatementProcessor processor = new StatementProcessor(log);

    private static StatementInput row(String id, String date, String tx, String amount, String description) {
        return new StatementInput(2, String.join(",", id, date, tx, amount, description),
                id, date, tx, amount, description);
    }

    @Test
    void normalizesAccentDateAndEmptyDescription() {
        StatementDTO result = processor.process(row("103", "2024/02/08", "depósito", "3000", ""));

        assertThat(result.getTransaction()).isEqualTo("DEPOSITO");
        assertThat(result.getStatementDate()).isEqualTo(LocalDate.of(2024, 2, 8));
        assertThat(result.getDescription()).isEqualTo("SIN DESCRIPCION");
        verify(log).corrected(eq(StatementProcessor.STEP_NAME), anyLong(),
                eq(List.of(ErrorCode.DATE_FORMAT_NORMALIZED, ErrorCode.TRANSACTION_TYPE_NORMALIZED,
                        ErrorCode.MISSING_DESCRIPTION)),
                anyString());
    }

    @Test
    void acceptsValidMovementTypes() {
        for (String type : List.of("deposito", "retiro", "compra", "pago")) {
            assertThat(processor.process(row("101", "2024-01-01", type, "100", "Pago " + type)).getTransaction())
                    .isEqualTo(type.toUpperCase());
        }
    }

    @Test
    void rejectsInvalidAmounts() {
        assertRejected(() -> processor.process(row("101", "2024-03-15", "retiro", "-500", "Retiro parcial")),
                ErrorCode.NEGATIVE_AMOUNT);
        assertRejected(() -> processor.process(row("107", "2024-12-25", "deposito", "0", "Ingreso navideño")),
                ErrorCode.ZERO_AMOUNT);
        assertRejected(() -> processor.process(row("107", "2024-12-25", "deposito", "", "Ingreso navideño")),
                ErrorCode.MISSING_AMOUNT);
    }

    @Test
    void rejectsUnknownMovementTypeAndBadDate() {
        assertRejected(() -> processor.process(row("101", "2024-01-01", "transferencia", "100", "x")),
                ErrorCode.INVALID_TRANSACTION_TYPE);
        assertRejected(() -> processor.process(row("101", "2024-02-30", "compra", "100", "x")),
                ErrorCode.INVALID_DATE);
    }

    @Test
    void rejectsDuplicateEvenWhenWrittenDifferently() {
        processor.process(row("110", "2024-07-24", "deposito", "1500", "Ingreso extra"));
        assertRejected(() -> processor.process(row("110", "24-07-2024", "depósito", "1500", "Ingreso extra")),
                ErrorCode.DUPLICATE_RECORD);
    }
}
