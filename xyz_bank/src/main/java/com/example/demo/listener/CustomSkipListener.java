package com.example.demo.listener;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.core.NestedExceptionUtils;

import com.example.demo.input.SourceRecord;
import com.example.demo.validation.DataQualityLog;
import com.example.demo.validation.ErrorCode;
import com.example.demo.validation.InvalidRecordException;

/**
 * Intercepta los registros omitidos de un step y los persiste en data_quality_log con su
 * línea de origen, código de error principal, todos los códigos detectados y la fila original.
 * Se crea una instancia por step para conocer siempre a qué proceso pertenece el rechazo.
 */
public class CustomSkipListener implements SkipListener<Object, Object> {

    private static final Logger log = LoggerFactory.getLogger(CustomSkipListener.class);

    private final String stepName;
    private final DataQualityLog dataQualityLog;

    public CustomSkipListener(String stepName, DataQualityLog dataQualityLog) {
        this.stepName = stepName;
        this.dataQualityLog = dataQualityLog;
    }

    /** Línea del CSV que no pudo leerse (cantidad de columnas incorrecta). */
    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException parseError) {
            log.debug("[{}] SKIP lectura línea {}: {}", stepName, parseError.getLineNumber(), t.getMessage());
            dataQualityLog.rejected(stepName, (long) parseError.getLineNumber(), ErrorCode.STRUCTURAL_ERROR,
                    t.getMessage(), parseError.getInput());
        }
    }

    /** Registro que no superó las validaciones del procesador. */
    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        if (t instanceof InvalidRecordException invalid && item instanceof SourceRecord source) {
            String detail = invalid.getErrorCodes().stream().map(ErrorCode::name).collect(Collectors.joining(","));
            log.debug("[{}] SKIP validación línea {}: {}", stepName, source.lineNumber(), detail);
            dataQualityLog.rejected(stepName, source.lineNumber(), invalid.getPrimaryCode(), detail, source.rawLine());
        }
    }

    /** Registro rechazado por una restricción de la base de datos. */
    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        log.debug("[{}] SKIP escritura {}: {}", stepName, item, t.getMessage());
        String detail = NestedExceptionUtils.getMostSpecificCause(t).getMessage();
        dataQualityLog.rejected(stepName, null, ErrorCode.DB_CONSTRAINT_VIOLATION, detail, String.valueOf(item));
    }
}
