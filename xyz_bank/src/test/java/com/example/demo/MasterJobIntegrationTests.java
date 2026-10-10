package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ejecuta el masterJob completo sobre el dataset oficial (fin_legacy_data, semana_3) contra
 * PostgreSQL y verifica que cada tipo de error quede rechazado o corregido con su código.
 * Los valores esperados se obtuvieron con un script independiente que aplica las mismas reglas.
 * Requiere la base de datos configurada en application.properties.
 */
@SpringBootTest
class MasterJobIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private Map<String, Integer> codes(String step, String action) {
        Map<String, Integer> result = new HashMap<>();
        jdbcTemplate.query("SELECT error_code, COUNT(*) FROM data_quality_log WHERE step_name = ? AND action = ? "
                + "GROUP BY error_code", rs -> {
                    result.put(rs.getString(1), rs.getInt(2));
                }, step, action);
        return result;
    }

    @Test
    void transactionsRejectEveryErrorTypeOfTheDataset() {
        assertThat(count("SELECT COUNT(*) FROM transaction_report")).isEqualTo(392);
        assertThat(codes("transactionStep", "RECHAZADO")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "INVALID_TRANSACTION_TYPE", 256,
                "MISSING_AMOUNT", 162,
                "NEGATIVE_AMOUNT", 121,
                "INVALID_DATE", 49,
                "ZERO_AMOUNT", 20));
        assertThat(count("SELECT COUNT(*) FROM transaction_report WHERE amount <= 0 "
                + "OR operation_type NOT IN ('DEBITO','CREDITO')")).isZero();
    }

    @Test
    void interestRejectsIneligibleAccountsAgesAndBalances() {
        assertThat(count("SELECT COUNT(*) FROM account")).isEqualTo(320);
        assertThat(codes("interestStep", "RECHAZADO")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "MISSING_BALANCE", 197,
                "INELIGIBLE_ACCOUNT_TYPE", 163,
                "AGE_OUT_OF_RANGE", 146,
                "MISSING_AGE", 126,
                "INVALID_CLIENT_NAME", 48));
        assertThat(count("SELECT COUNT(*) FROM account WHERE age NOT BETWEEN 18 AND 99 "
                + "OR account_type NOT IN ('AHORRO','PRESTAMO','HIPOTECA') OR client_name = 'Unknown'")).isZero();
    }

    @Test
    void statementsRejectInvalidAmountsAndNormalizeTheRest() {
        assertThat(count("SELECT COUNT(*) FROM annual_statement")).isEqualTo(603);
        assertThat(codes("statementStep", "RECHAZADO")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "NEGATIVE_AMOUNT", 232,
                "ZERO_AMOUNT", 116,
                "MISSING_AMOUNT", 48,
                "DUPLICATE_RECORD", 1));
        assertThat(codes("statementStep", "CORREGIDO")).containsKeys(
                "DATE_FORMAT_NORMALIZED", "TRANSACTION_TYPE_NORMALIZED", "MISSING_DESCRIPTION");
        assertThat(count("SELECT COUNT(*) FROM annual_statement WHERE transaction = 'DEPÓSITO'")).isZero();
    }

    @Test
    void everyInputLineIsEitherPersistedOrRejected() {
        int rejected = count("SELECT COUNT(*) FROM data_quality_log WHERE action = 'RECHAZADO'");
        int persisted = count("SELECT (SELECT COUNT(*) FROM transaction_report) + (SELECT COUNT(*) FROM account) "
                + "+ (SELECT COUNT(*) FROM annual_statement)");
        assertThat(rejected + persisted).isEqualTo(3000);
    }
}
