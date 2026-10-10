package com.example.demo.policies;

import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Componente que establece las políticas de resiliencia y tolerancia a fallos del sistema batch
 * Actúa como filtro para determinar qué excepciones justifican saltar un registro en lugar de abortar el Job
 */
@Component
public class CustomSkipPolicies implements SkipPolicy {
    
    /** 
     * Umbral de errores permitidos. Aumentamos el límite a 1000 para soportar la alta corrupción de los CSV legacy 
     */
    private static final int MAX_SKIP_COUNT = 1000;

    /**
     * Evalúa el tipo de excepción y el conteo de errores actuales para autorizar el salto de un registro
     * 
     * @param t Excepción detectada en lectura, procesamiento o escritura[cite: 5].
     * @param skipCount Cantidad de registros ya saltados durante la ejecución[cite: 5].
     * @return true si la excepción está en la lista de errores tolerables, false en caso contrario
     * @throws SkipLimitExceededException Si el número de errores excede el límite máximo configurado
     */
    @Override
    public boolean shouldSkip(Throwable t, long skipCount) throws SkipLimitExceededException {
        
        if (skipCount >= MAX_SKIP_COUNT) {
            throw new SkipLimitExceededException(MAX_SKIP_COUNT, t); 
        }

        // Permitimos saltar errores de lectura, matemáticas y de conversión (BindException)
        // Se incluyen también excepciones de persistencia en bloque y violaciones de integridad
        if (t instanceof FlatFileParseException || 
            t instanceof NumberFormatException || 
            t instanceof org.springframework.validation.BindException ||
            t instanceof DataIntegrityViolationException ||
            t instanceof java.sql.BatchUpdateException) { 
            return true; 
        }
        
        return false; 
    }
}