package com.example.demo.processor;

import static com.example.demo.processor.ProcessorTestSupport.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.demo.DTOs.AccountDTO;
import com.example.demo.input.AccountInput;
import com.example.demo.validation.ErrorCode;

class InterestProcessorTest {

    private final InterestProcessor processor = new InterestProcessor(ProcessorTestSupport.properties());

    private static AccountInput row(String id, String name, String balance, String age, String type) {
        return new AccountInput(2, String.join(",", id, name, balance, age, type), id, name, balance, age, type);
    }

    @Test
    void appliesRateByAccountType() {
        AccountDTO savings = processor.process(row("101", "John Doe", "5000", "30", "ahorro"));
        AccountDTO loan = processor.process(row("102", "Jane Smith", "8000", "25", "prestamo"));
        AccountDTO mortgage = processor.process(row("105", "Charlie Green", "7000", "35", "hipoteca"));

        assertThat(savings.getInterest()).isEqualByComparingTo("25.00");
        assertThat(savings.getBalance()).isEqualByComparingTo("5025.00");
        assertThat(savings.getAccountType()).isEqualTo("AHORRO");
        assertThat(loan.getBalance()).isEqualByComparingTo("8160.00");
        assertThat(mortgage.getBalance()).isEqualByComparingTo("7210.00");
        assertThat(mortgage.getAge()).isEqualTo(35);
    }

    @Test
    void zeroBalanceIsValidAndEarnsNoInterest() {
        AccountDTO account = processor.process(row("104", "Alice Brown", "0", "45", "ahorro"));
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsIneligibleAccountTypes() {
        assertRejected(() -> processor.process(row("103", "Bob Johnson", "12000", "30", "-1")),
                ErrorCode.INELIGIBLE_ACCOUNT_TYPE);
        assertRejected(() -> processor.process(row("103", "Bob Johnson", "12000", "30", "unknown")),
                ErrorCode.INELIGIBLE_ACCOUNT_TYPE);
        assertRejected(() -> processor.process(row("103", "Bob Johnson", "12000", "30", "")),
                ErrorCode.INELIGIBLE_ACCOUNT_TYPE);
    }

    @Test
    void rejectsAgesOutsideAcceptableRange() {
        assertRejected(() -> processor.process(row("108", "Steve Rogers", "10000", "100", "ahorro")),
                ErrorCode.AGE_OUT_OF_RANGE);
        assertRejected(() -> processor.process(row("108", "Steve Rogers", "10000", "150", "ahorro")),
                ErrorCode.AGE_OUT_OF_RANGE);
        assertRejected(() -> processor.process(row("108", "Steve Rogers", "10000", "17", "ahorro")),
                ErrorCode.AGE_OUT_OF_RANGE);
        assertRejected(() -> processor.process(row("108", "Steve Rogers", "10000", "", "ahorro")),
                ErrorCode.MISSING_AGE);
        assertRejected(() -> processor.process(row("108", "Steve Rogers", "10000", "treinta", "ahorro")),
                ErrorCode.INVALID_AGE);
        assertThat(processor.process(row("108", "Steve Rogers", "10000", "99", "ahorro"))).isNotNull();
        assertThat(processor.process(row("109", "Steve Rogers", "10000", "18", "ahorro"))).isNotNull();
    }

    @Test
    void rejectsInvalidBalances() {
        assertRejected(() -> processor.process(row("104", "Alice Brown", "", "45", "ahorro")),
                ErrorCode.MISSING_BALANCE);
        assertRejected(() -> processor.process(row("104", "Alice Brown", "-10", "45", "ahorro")),
                ErrorCode.NEGATIVE_BALANCE);
        assertRejected(() -> processor.process(row("104", "Alice Brown", "mil", "45", "ahorro")),
                ErrorCode.INVALID_BALANCE);
    }

    @Test
    void rejectsUnknownClientAndInvalidAccountId() {
        assertRejected(() -> processor.process(row("114", "Unknown", "5000", "30", "ahorro")),
                ErrorCode.INVALID_CLIENT_NAME);
        assertRejected(() -> processor.process(row("", "John Doe", "5000", "30", "ahorro")),
                ErrorCode.INVALID_ID);
    }

    @Test
    void rejectsExactDuplicateOfAcceptedRecord() {
        processor.process(row("101", "John Doe", "5000", "30", "ahorro"));
        assertRejected(() -> processor.process(row("101", "John Doe", "5000", "30", "ahorro")),
                ErrorCode.DUPLICATE_RECORD);
        // misma cuenta con otros datos (otro trimestre) no es duplicado
        assertThat(processor.process(row("101", "John Doe", "7000", "30", "ahorro"))).isNotNull();
    }
}
