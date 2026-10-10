package com.example.demo.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

import com.example.demo.validation.DataQualityLog;

/**
 * Al terminar cada step persiste las correcciones acumuladas y registra un resumen de conteos
 * (leídos, escritos, rechazados por fase) y duración, usado para comparar configuraciones.
 */
public class StepSummaryListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(StepSummaryListener.class);

    private final DataQualityLog dataQualityLog;

    public StepSummaryListener(DataQualityLog dataQualityLog) {
        this.dataQualityLog = dataQualityLog;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        int corrections = dataQualityLog.flushCorrections(stepExecution.getStepName());
        LocalDateTime start = stepExecution.getStartTime();
        long millis = start == null ? -1 : Duration.between(start, LocalDateTime.now()).toMillis();
        log.info("RESUMEN {} status={} leidos={} escritos={} rechazados(lectura/proceso/escritura)={}/{}/{} "
                        + "correcciones={} commits={} rollbacks={} duracionMs={}",
                stepExecution.getStepName(), stepExecution.getStatus(), stepExecution.getReadCount(),
                stepExecution.getWriteCount(), stepExecution.getReadSkipCount(), stepExecution.getProcessSkipCount(),
                stepExecution.getWriteSkipCount(), corrections, stepExecution.getCommitCount(),
                stepExecution.getRollbackCount(), millis);
        return null;
    }
}
