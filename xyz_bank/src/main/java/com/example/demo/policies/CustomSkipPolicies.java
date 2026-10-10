package com.example.demo.policies;

import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.example.demo.config.XyzBatchProperties;
import com.example.demo.validation.InvalidRecordException;

/**
 * Componente que establece las políticas de resiliencia y tolerancia a fallos del sistema batch.
 * Distingue explícitamente entre:
 * <ul>
 *   <li><b>Errores de datos (omitibles)</b>: el registro se salta, se cuenta y queda en data_quality_log.
 *     <ul>
 *       <li>{@link InvalidRecordException}: validación de negocio de los procesadores
 *           (montos, fechas, tipos, edades, saldos, duplicados...).</li>
 *       <li>{@link FlatFileParseException}: línea con estructura incorrecta en el CSV.</li>
 *       <li>{@link DataIntegrityViolationException}: restricción de la BD (defensa en profundidad,
 *           p. ej. id de transacción repetido).</li>
 *     </ul>
 *   </li>
 *   <li><b>Errores transitorios de BD</b>: se reintentan (configurado en el step), no se saltan.</li>
 *   <li><b>Cualquier otro error</b> (bug, BD caída, archivo inexistente): NO se salta y el step falla,
 *       para no ocultar fallos de infraestructura como si fueran datos malos.</li>
 * </ul>
 */
@Component
public class CustomSkipPolicies implements SkipPolicy {

    /** Umbral de rechazos permitidos por step; sobre este valor el step se aborta. */
    private final int maxSkipCount;

    public CustomSkipPolicies(XyzBatchProperties properties) {
        this.maxSkipCount = properties.skipLimit();
    }

    /**
     * Evalúa el tipo de excepción y el conteo de errores actuales para autorizar el salto de un registro
     * 
     * @param t Excepción detectada en lectura, procesamiento o escritura.
     * @param skipCount Cantidad de registros ya saltados en el step.
     * @return true si la excepción es un error de datos omitible y no se superó el límite.
     * @throws SkipLimitExceededException Si un error omitible excede el límite configurado.
     */
    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        if (!isDataError(t)) {
            return false;
        }
        if (skipCount >= maxSkipCount) {
            throw new SkipLimitExceededException(maxSkipCount, t);
        }
        return true;
    }

    private static boolean isDataError(Throwable t) {
        return t instanceof InvalidRecordException
                || t instanceof FlatFileParseException
                || t instanceof DataIntegrityViolationException;
    }
}
