package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica escenarios de tolerancia a fallos que el dataset oficial semana_3 no contiene pero que
 * pueden aparecer en otras entregas legacy: líneas con columnas faltantes o sobrantes (error de
 * lectura), identificadores repetidos y filas idénticas. El job debe completarse y trazar cada caso.
 */
@SpringBootTest(properties = {
        "xyz.batch.input.transactions=classpath:fixtures/movimientos.csv",
        "xyz.batch.input.interests=classpath:fixtures/intereses.csv",
        "xyz.batch.input.statements=classpath:fixtures/estados.csv",
        "xyz.batch.chunk-size=2"})
class FaultToleranceIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<String> rejections(String step) {
        return jdbcTemplate.queryForList("SELECT line_number || ':' || error_code FROM data_quality_log "
                + "WHERE step_name = ? AND action = 'RECHAZADO' ORDER BY line_number", String.class, step);
    }

    @Test
    void malformedLinesAndDuplicatesAreSkippedAndTraced() {
        assertThat(rejections("transactionStep")).containsExactly(
                "3:STRUCTURAL_ERROR", "4:STRUCTURAL_ERROR", "5:DUPLICATE_RECORD", "7:NEGATIVE_AMOUNT");
        assertThat(jdbcTemplate.queryForList("SELECT source_id FROM transaction_report ORDER BY source_id",
                Integer.class)).containsExactly(1, 4);

        assertThat(rejections("interestStep")).containsExactly("3:DUPLICATE_RECORD", "4:STRUCTURAL_ERROR");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Integer.class)).isEqualTo(2);

        assertThat(rejections("statementStep")).containsExactly("3:INVALID_ID");
    }
}
