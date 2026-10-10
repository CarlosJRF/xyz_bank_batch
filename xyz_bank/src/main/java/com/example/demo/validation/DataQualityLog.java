package com.example.demo.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Trazabilidad de calidad de datos: guarda en la tabla data_quality_log cada registro rechazado
 * (action = RECHAZADO) y cada corrección aplicada a un registro aceptado (action = CORREGIDO).
 *
 * Los rechazos se insertan desde los SkipListener, dentro de la transacción del chunk.
 * Las correcciones se acumulan en memoria (clave idempotente step+línea+código, segura ante
 * reprocesamientos de un chunk) y se insertan en lote al finalizar cada step.
 */
@Component
public class DataQualityLog {

    public static final String REJECTED = "RECHAZADO";
    public static final String CORRECTED = "CORREGIDO";

    private static final String INSERT_SQL =
            "INSERT INTO data_quality_log (step_name, line_number, action, error_code, error_detail, raw_record) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private record Correction(String stepName, long lineNumber, ErrorCode code, String rawRecord) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Correction> pendingCorrections = new ConcurrentHashMap<>();

    public DataQualityLog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void rejected(String stepName, Long lineNumber, ErrorCode primaryCode, String detail, String rawRecord) {
        jdbcTemplate.update(INSERT_SQL, stepName, lineNumber, REJECTED, primaryCode.name(), detail, rawRecord);
    }

    public void corrected(String stepName, long lineNumber, List<ErrorCode> codes, String rawRecord) {
        for (ErrorCode code : codes) {
            pendingCorrections.putIfAbsent(stepName + '|' + lineNumber + '|' + code,
                    new Correction(stepName, lineNumber, code, rawRecord));
        }
    }

    /** Persiste en lote las correcciones acumuladas del step indicado. */
    public int flushCorrections(String stepName) {
        List<Object[]> rows = new ArrayList<>();
        pendingCorrections.values().removeIf(c -> {
            if (!c.stepName().equals(stepName)) {
                return false;
            }
            rows.add(new Object[] {c.stepName(), c.lineNumber(), CORRECTED, c.code().name(),
                    c.code().getDescription(), c.rawRecord()});
            return true;
        });
        if (!rows.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_SQL, rows);
        }
        return rows.size();
    }
}
