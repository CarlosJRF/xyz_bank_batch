package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Parámetros externos del proceso batch (prefijo {@code xyz.batch} en application.properties).
 * Permiten ajustar el tamaño de chunk, los hilos y las reglas de validación sin recompilar,
 * lo que es necesario para comparar configuraciones de rendimiento (ver README).
 *
 * @param input             Ubicación de los tres archivos CSV del dataset oficial.
 * @param chunkSize         Registros por transacción (commit) en cada step.
 * @param stepThreads       Hilos de trabajo por step. Spring Batch 6 limita internamente a 4 los
 *                          chunks concurrentes de un step multihilo (throttle fijo), por lo que
 *                          valores mayores a 4 no aportan paralelismo adicional.
 * @param skipLimit         Máximo de registros rechazados por step antes de abortar el step.
 * @param retryLimit        Reintentos ante errores transitorios de base de datos.
 * @param resetTables       Si es true, vacía las tablas de destino al inicio del masterJob para que
 *                          cada ejecución sea una recarga completa e idempotente de los archivos.
 * @param minAge            Edad mínima aceptable del titular (inclusive).
 * @param maxAge            Edad máxima aceptable del titular (inclusive).
 */
@ConfigurationProperties(prefix = "xyz.batch")
public record XyzBatchProperties(
        @DefaultValue Input input,
        @DefaultValue("100") int chunkSize,
        @DefaultValue("2") int stepThreads,
        @DefaultValue("1000") int skipLimit,
        @DefaultValue("3") int retryLimit,
        @DefaultValue("true") boolean resetTables,
        @DefaultValue("18") int minAge,
        @DefaultValue("99") int maxAge) {

    public record Input(
            @DefaultValue("classpath:data/movimientos_financieros_diarios.csv") Resource transactions,
            @DefaultValue("classpath:data/intereses_trimestrales.csv") Resource interests,
            @DefaultValue("classpath:data/estados_financieros_anuales.csv") Resource statements) {
    }
}
