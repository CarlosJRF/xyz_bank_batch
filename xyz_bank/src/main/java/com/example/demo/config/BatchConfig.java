package com.example.demo.config;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.FaultTolerantStepBuilder;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.demo.DTOs.AccountDTO;
import com.example.demo.DTOs.StatementDTO;
import com.example.demo.DTOs.TransactionDTO;
import com.example.demo.input.AccountInput;
import com.example.demo.input.CsvLineMapper;
import com.example.demo.input.SourceRecord;
import com.example.demo.input.StatementInput;
import com.example.demo.input.TransactionInput;
import com.example.demo.listener.CustomSkipListener;
import com.example.demo.listener.JobSummaryListener;
import com.example.demo.listener.StepSummaryListener;
import com.example.demo.policies.CustomSkipPolicies;
import com.example.demo.processor.InterestProcessor;
import com.example.demo.processor.StatementProcessor;
import com.example.demo.processor.TransactionProcessor;
import com.example.demo.validation.DataQualityLog;
import com.example.demo.validation.InvalidRecordException;


@Configuration
@EnableConfigurationProperties(XyzBatchProperties.class)
public class BatchConfig {

    /** Cantidad de flujos que el masterJob ejecuta en paralelo (transacciones, intereses, estados). */
    private static final int PARALLEL_FLOWS = 3;

    /**
     * Máximo de chunks concurrentes que Spring Batch 6 ejecuta por step multihilo
     * (TaskExecutorRepeatTemplate.DEFAULT_THROTTLE_LIMIT, ya no configurable).
     */
    private static final int CHUNKS_IN_FLIGHT_PER_STEP = 4;

    private final XyzBatchProperties properties;
    private final DataQualityLog dataQualityLog;
    private final CustomSkipPolicies skipPolicies;

    public BatchConfig(XyzBatchProperties properties, DataQualityLog dataQualityLog, CustomSkipPolicies skipPolicies) {
        this.properties = properties;
        this.dataQualityLog = dataQualityLog;
        this.skipPolicies = skipPolicies;
    }

    /* ----------------------------------------------------------------------
       SECCIÓN: Lectores. Leen cada CSV como texto crudo (sin convertir tipos);
       la conversión y validación con códigos de error explícitos ocurre en los procesadores.
       ---------------------------------------------------------------------- */

    @Bean
    public FlatFileItemReader<TransactionInput> transactionItemReader() {
        return csvReader("transactionItemReader", properties.input().transactions(),
                new CsvLineMapper<>((line, raw, f) -> new TransactionInput(line, raw, f[0], f[1], f[2], f[3]),
                        "id", "fecha", "monto", "tipo"));
    }

    @Bean
    public FlatFileItemReader<AccountInput> interestItemReader() {
        return csvReader("interestItemReader", properties.input().interests(),
                new CsvLineMapper<>((line, raw, f) -> new AccountInput(line, raw, f[0], f[1], f[2], f[3], f[4]),
                        "cuenta_id", "nombre", "saldo", "edad", "tipo"));
    }

    @Bean
    public FlatFileItemReader<StatementInput> statementItemReader() {
        return csvReader("statementItemReader", properties.input().statements(),
                new CsvLineMapper<>((line, raw, f) -> new StatementInput(line, raw, f[0], f[1], f[2], f[3], f[4]),
                        "cuenta_id", "fecha", "transaccion", "monto", "descripcion"));
    }

    private <T extends SourceRecord> FlatFileItemReader<T> csvReader(String name, Resource resource,
                                                                      CsvLineMapper<T> lineMapper) {
        return new FlatFileItemReaderBuilder<T>()
                .name(name)
                .resource(resource)
                .encoding("UTF-8")
                .linesToSkip(1) // encabezado del CSV
                .lineMapper(lineMapper)
                // Un step multihilo no es reiniciable desde la última posición: no se guarda estado
                .saveState(false)
                .build();
    }

    /* ----------------------------------------------------------------------
       SECCIÓN: Escritores JDBC batch hacia PostgreSQL
       ---------------------------------------------------------------------- */

    @Bean
    public JdbcBatchItemWriter<TransactionDTO> transactionItemWriter(DataSource dataSource) {
        return jdbcWriter(dataSource,
                "INSERT INTO transaction_report (source_id, transaction_date, amount, operation_type) "
                        + "VALUES (:id, :transactionDate, :amount, :type)");
    }

    @Bean
    public JdbcBatchItemWriter<AccountDTO> interestItemWriter(DataSource dataSource) {
        return jdbcWriter(dataSource,
                "INSERT INTO account (account_id, client_name, balance, interest, age, account_type) "
                        + "VALUES (:accountId, :clientName, :balance, :interest, :age, :accountType)");
    }

    @Bean
    public JdbcBatchItemWriter<StatementDTO> statementItemWriter(DataSource dataSource) {
        return jdbcWriter(dataSource,
                "INSERT INTO annual_statement (account_id, statement_date, transaction, amount, description) "
                        + "VALUES (:accountId, :statementDate, :transaction, :amount, :description)");
    }

    private static <T> JdbcBatchItemWriter<T> jdbcWriter(DataSource dataSource, String sql) {
        return new JdbcBatchItemWriterBuilder<T>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql(sql)
                .dataSource(dataSource)
                .build();
    }

    /* ----------------------------------------------------------------------
       SECCIÓN: Steps tolerantes a fallos
       ---------------------------------------------------------------------- */

    @Bean
    public Step transactionStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                FlatFileItemReader<TransactionInput> transactionItemReader,
                                TransactionProcessor transactionProcessor,
                                JdbcBatchItemWriter<TransactionDTO> transactionItemWriter) {
        return chunkStep(TransactionProcessor.STEP_NAME, jobRepository, transactionManager,
                transactionItemReader, transactionProcessor, transactionItemWriter, transactionProcessor);
    }

    @Bean
    public Step interestStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                             FlatFileItemReader<AccountInput> interestItemReader,
                             InterestProcessor interestProcessor,
                             JdbcBatchItemWriter<AccountDTO> interestItemWriter) {
        return chunkStep(InterestProcessor.STEP_NAME, jobRepository, transactionManager,
                interestItemReader, interestProcessor, interestItemWriter, interestProcessor);
    }

    @Bean
    public Step statementStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                              FlatFileItemReader<StatementInput> statementItemReader,
                              StatementProcessor statementProcessor,
                              JdbcBatchItemWriter<StatementDTO> statementItemWriter) {
        return chunkStep(StatementProcessor.STEP_NAME, jobRepository, transactionManager,
                statementItemReader, statementProcessor, statementItemWriter, statementProcessor);
    }

    /**
     * Construye un step chunk-oriented con la misma estrategia de concurrencia y resiliencia para
     * los tres procesos:
     * <ol>
     *   <li>Lector sincronizado para compartirlo entre hilos.</li>
     *   <li>Pool de hilos de trabajo dimensionado por {@code xyz.batch.step-threads}
     *       (con 1 hilo el step es secuencial, sin pool).</li>
     *   <li>Salto de errores de datos según {@link CustomSkipPolicies}, trazados por {@link CustomSkipListener}.
     *       Los rechazos de validación no provocan rollback del chunk ({@code noRollback}), y el procesador
     *       no se re-ejecuta si el chunk se reintenta ({@code processorNonTransactional}).</li>
     *   <li>Reintentos con backoff exponencial ante errores transitorios de BD
     *       (bloqueos, deadlocks, timeouts: {@link TransientDataAccessException}).</li>
     * </ol>
     */
    private <I extends SourceRecord, O> Step chunkStep(String stepName, JobRepository jobRepository,
                                                       PlatformTransactionManager transactionManager,
                                                       FlatFileItemReader<I> reader,
                                                       ItemProcessor<I, O> processor, ItemWriter<O> writer,
                                                       StepExecutionListener processorListener) {
        SimpleStepBuilder<I, O> builder = new StepBuilder(stepName, jobRepository)
                .<I, O>chunk(properties.chunkSize(), transactionManager)
                // 1. Sincronización del lector
                .reader(new SynchronizedItemStreamReaderBuilder<I>().delegate(reader).build())
                .processor(processor)
                .writer(writer);

        // 2. Orquestación multihilo
        if (properties.stepThreads() > 1) {
            builder = builder.taskExecutor(stepTaskExecutor());
        }

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(100);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(1000);

        FaultTolerantStepBuilder<I, O> faultTolerant = builder.faultTolerant()
                // 3. Resiliencia ante datos inválidos
                .skipPolicy(skipPolicies)
                .noRollback(InvalidRecordException.class)
                .processorNonTransactional()
                .listener(new CustomSkipListener(stepName, dataQualityLog))
                // 4. Reintentos ante errores transitorios de PostgreSQL
                .retry(TransientDataAccessException.class)
                .retryLimit(properties.retryLimit())
                .backOffPolicy(backOff);

        faultTolerant.listener(processorListener);
        faultTolerant.listener(new StepSummaryListener(dataQualityLog));
        return faultTolerant.build();
    }

    /* ----------------------------------------------------------------------
       SECCIÓN: Pools de hilos
       ---------------------------------------------------------------------- */

    /**
     * Pool de los hilos de trabajo que procesan chunks dentro de los tres steps.
     * Tamaño = flujos paralelos (3) x hilos por step. Cada step puede tener a lo más
     * {@value #CHUNKS_IN_FLIGHT_PER_STEP} chunks en vuelo, por lo que la cola solo necesita
     * absorber esa cantidad por step cuando el pool es menor.
     * Ver README, sección "Dimensionamiento del pool de hilos", para la justificación y las mediciones.
     */
    @Bean
    public ThreadPoolTaskExecutor stepTaskExecutor() {
        int poolSize = PARALLEL_FLOWS * Math.max(1, properties.stepThreads());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(PARALLEL_FLOWS * CHUNKS_IN_FLIGHT_PER_STEP);
        executor.setThreadNamePrefix("StepWorker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /**
     * Pool dedicado al split del masterJob: un hilo por flujo paralelo. Se separa del pool de
     * los steps porque cada hilo de flujo queda bloqueado esperando su job anidado; si compartieran
     * pool, esos hilos bloqueados le quitarían capacidad a los chunks.
     */
    @Bean
    public ThreadPoolTaskExecutor flowTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(PARALLEL_FLOWS);
        executor.setMaxPoolSize(PARALLEL_FLOWS);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("Flow-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    /* ----------------------------------------------------------------------
       SECCIÓN: Jobs individuales y orquestación con masterJob
       ---------------------------------------------------------------------- */

    @Bean
    public Job transactionJob(JobRepository jobRepository, Step transactionStep) {
        return new JobBuilder("transactionJob", jobRepository).start(transactionStep).build();
    }

    @Bean
    public Job interestJob(JobRepository jobRepository, Step interestStep) {
        return new JobBuilder("interestJob", jobRepository).start(interestStep).build();
    }

    @Bean
    public Job statementJob(JobRepository jobRepository, Step statementStep) {
        return new JobBuilder("statementJob", jobRepository).start(statementStep).build();
    }

    @Bean
    public Step transactionJobStep(JobRepository jobRepository, JobOperator jobOperator,
                                   @Qualifier("transactionJob") Job transactionJob) {
        return new StepBuilder("transactionJobStep", jobRepository).job(transactionJob).operator(jobOperator).build();
    }

    @Bean
    public Step interestJobStep(JobRepository jobRepository, JobOperator jobOperator,
                                @Qualifier("interestJob") Job interestJob) {
        return new StepBuilder("interestJobStep", jobRepository).job(interestJob).operator(jobOperator).build();
    }

    @Bean
    public Step statementJobStep(JobRepository jobRepository, JobOperator jobOperator,
                                 @Qualifier("statementJob") Job statementJob) {
        return new StepBuilder("statementJobStep", jobRepository).job(statementJob).operator(jobOperator).build();
    }

    /**
     * Vacía las tablas de destino y la bitácora de calidad antes de procesar, para que cada
     * ejecución sea una recarga completa e idempotente de los archivos (xyz.batch.reset-tables).
     */
    @Bean
    public Step resetTablesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                JdbcTemplate jdbcTemplate) {
        return new StepBuilder("resetTablesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    if (properties.resetTables()) {
                        jdbcTemplate.execute("TRUNCATE TABLE transaction_report, account, annual_statement, "
                                + "data_quality_log RESTART IDENTITY");
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Job maestro: limpia las tablas y luego ejecuta en paralelo los tres jobs (transacciones,
     * intereses y estados de cuenta), cada uno en su propio hilo de {@link #flowTaskExecutor()}.
     * El RunIdIncrementer permite re-ejecutarlo; los jobs anidados heredan su run.id.
     */
    @Bean
    public Job masterJob(JobRepository jobRepository, Step resetTablesStep,
                         Step transactionJobStep, Step interestJobStep, Step statementJobStep,
                         JobSummaryListener jobSummaryListener) {
        Flow transactionFlow = new FlowBuilder<SimpleFlow>("transactionFlow").start(transactionJobStep).build();
        Flow interestFlow = new FlowBuilder<SimpleFlow>("interestFlow").start(interestJobStep).build();
        Flow statementFlow = new FlowBuilder<SimpleFlow>("statementFlow").start(statementJobStep).build();

        Flow parallelFlows = new FlowBuilder<SimpleFlow>("parallelFlows")
                .split((TaskExecutor) flowTaskExecutor())
                .add(transactionFlow, interestFlow, statementFlow)
                .build();

        return new JobBuilder("masterJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobSummaryListener)
                .flow(resetTablesStep)
                .next(parallelFlows)
                .end()
                .build();
    }

}
