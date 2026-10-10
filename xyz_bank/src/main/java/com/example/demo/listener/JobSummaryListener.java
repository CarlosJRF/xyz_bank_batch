package com.example.demo.listener;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.demo.config.XyzBatchProperties;

/**
 * Registra el tiempo total del masterJob junto con la configuración usada (chunk e hilos) y el
 * desglose de rechazos/correcciones por código, de modo que cada ejecución sirva como medición.
 */
@Component
public class JobSummaryListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobSummaryListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final XyzBatchProperties properties;

    public JobSummaryListener(JdbcTemplate jdbcTemplate, XyzBatchProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        jdbcTemplate.query(
                "SELECT step_name, action, error_code, COUNT(*) AS total FROM data_quality_log "
                        + "GROUP BY step_name, action, error_code ORDER BY step_name, action, total DESC",
                rs -> {
                    log.info("CALIDAD {} {} {}={}", rs.getString("step_name"), rs.getString("action"),
                            rs.getString("error_code"), rs.getLong("total"));
                });
        LocalDateTime start = jobExecution.getStartTime();
        long millis = start == null ? -1 : Duration.between(start, LocalDateTime.now()).toMillis();
        log.info("BENCHMARK job={} status={} chunkSize={} stepThreads={} totalMs={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getStatus(),
                properties.chunkSize(), properties.stepThreads(), millis);
    }
}
