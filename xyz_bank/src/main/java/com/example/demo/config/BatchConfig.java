package com.example.demo.config;

import java.time.LocalDate;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import java.lang.Exception;
import java.math.BigDecimal;

import com.example.demo.DTOs.AccountDTO;
import com.example.demo.DTOs.StatementDTO;
import com.example.demo.DTOs.TransactionDTO;
import com.example.demo.listener.CustomSkipListener;
import com.example.demo.policies.CustomSkipPolicies;
import com.example.demo.processor.InterestProcessor;
import com.example.demo.processor.StatementProcessor;
import com.example.demo.processor.TransactionProcessor;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.core.convert.converter.Converter;

import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.batch.core.launch.JobOperator;

import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.beans.factory.annotation.Qualifier;


import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;


import org.springframework.batch.infrastructure.item.support.builder.SynchronizedItemStreamReaderBuilder;


@Configuration
public class BatchConfig {

        /* ----------------------------------------------------------------------
       SECCIÓN: Configuracion de chunk e incorporacion de ruta relatia de archivos .csv
       ---------------------------------------------------------------------- */


    //corte para el commit de registro cada 10
    private final static int CHUNK_SIZE = 5;

    //ubicacion del archivo para transacciones
    @Value("classpath:transacciones.csv")
    private Resource transactionCSV;

    @Value("classpath:intereses.csv")
    private Resource interestCSV;

    @Value("classpath:cuentas_anuales.csv")
    private Resource statementCSV;


    /* ----------------------------------------------------------------------
    SECCIÓN: Cofiguracion de batch encargado de la lectura y escritura de los datos relacionados con el job de transacciones
    ---------------------------------------------------------------------- */



    /**
     * Configura un lector de archivos (ItemReader) para procesar transacciones en Spring Batch.
     * Su propósito es leer un archivo CSV línea por línea, separar los valores por delimitador,
     * y transformarlos en objetos Java utilizables por el resto del proceso batch.
     *
     * Datos que recibe (Contexto de entrada):
     * - Lee desde el recurso definido en la variable 'transactionCSV'.
     * - Espera un archivo CSV con al menos 4 columnas en este orden exacto: 
     *   "id", "transactionDate", "amount", "type".
     * - Asume que la primera línea del archivo es un encabezado y la omite.
     *
     * Lo que retorna:
     * @return FlatFileItemReader<TransactionDTO> Un componente de lectura que emite una 
     * instancia de TransactionDTO por cada línea procesada del archivo CSV.
     */
    @Bean
    public FlatFileItemReader<TransactionDTO> sendTransactionItemReader(){
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("id", "transactionDate", "amount", "type");

        BeanWrapperFieldSetMapper<TransactionDTO> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(TransactionDTO.class);

        fieldSetMapper.setConversionService(createConversionService());

        DefaultLineMapper<TransactionDTO> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        return new FlatFileItemReaderBuilder<TransactionDTO>()
                .name("sendTransactionItemReader")
                .resource(transactionCSV)
                .linesToSkip(1) // encabezado del CSV
                .lineMapper(lineMapper)
                .build();
    }

    /**
     * Configura un escritor por lotes (ItemWriter) para persistir las transacciones en base de datos.
     * Su función es recibir bloques (chunks) de objetos TransactionDTO ya procesados y ejecutar
     * inserciones masivas (batch insert) mediante JDBC para optimizar el rendimiento.
     *
     * @param dataSource Fuente de conexión a la base de datos inyectada por el contexto de Spring.
     * 
     * Datos que recibe (Entrada en ejecución):
     * - Chunks (lotes) de instancias de TransactionDTO enviados por el Step de Spring Batch.
     * - Extrae automáticamente los valores de las propiedades del DTO:
     *   - :transactionDate -> mapeado desde transactionDTO.getTransactionDate()
     *   - :amount          -> mapeado desde transactionDTO.getAmount()
     *   - :type            -> mapeado desde transactionDTO.getType()
     *
     * Lo que retorna / Efecto:
     * @return JdbcBatchItemWriter<TransactionDTO> Componente de escritura configurado para 
     * insertar registros en la tabla 'transaction_report'.
     */
    @Bean
    public JdbcBatchItemWriter<TransactionDTO> TransactionItemWriter(DataSource dataSource){
    return new JdbcBatchItemWriterBuilder<TransactionDTO>()
        .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
        .sql("INSERT INTO transaction_report (transaction_date, amount, operation_type) VALUES (:transactionDate, :amount, :type)")
        .dataSource(dataSource)
        .build();
}

    /**
     * Define y configura el paso (Step) principal del proceso batch para las transacciones.
     * Este componente orquesta el flujo completo de Chunk-oriented processing (lectura, 
     * procesamiento y escritura). Está optimizado para alto rendimiento y entornos concurrentes,
     * implementando ejecución multi-hilo, tolerancia a fallos y reintentos automáticos.
     *
     * Datos que recibe (Dependencias inyectadas por el contexto de Spring):
     * @param jobRepository         Repositorio de Spring Batch para persistir el estado y metadatos del Step.
     * @param transactionManager    Gestor encargado de hacer commit de cada chunk o rollback en caso de fallo crítico.
     * @param sendTransactionItemReader El lector del CSV. Se envuelve internamente para soportar concurrencia.
     * @param itemOut               El procesador (Processor) que aplica transformaciones o lógica de negocio a cada item.
     * @param transactionItemWriter El escritor JDBC que realiza las inserciones por lotes en base de datos.
     * @param skipListener          Intercepta y maneja (ej. loguea) los registros que fueron omitidos por error.
     * @param skipPolicies          Reglas personalizadas que dictan qué excepciones permiten saltar un registro en lugar de abortar el Job.
     *
     * Lo que retorna / Efecto:
     * @return Step El paso configurado y listo para ser integrado en un Job de Spring Batch.
     */
    @Bean
    public Step transactionStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                            FlatFileItemReader<TransactionDTO> sendTransactionItemReader, TransactionProcessor itemOut,
                            JdbcBatchItemWriter<TransactionDTO> transactionItemWriter, CustomSkipListener skipListener, CustomSkipPolicies skipPolicies) {
    return new StepBuilder("transactionStep", jobRepository)
            .<TransactionDTO, TransactionDTO>chunk(CHUNK_SIZE, transactionManager)
            
            // 1. Sincronización del Lector (Evita colisiones de memoria)
            .reader(new SynchronizedItemStreamReaderBuilder<TransactionDTO>()
                    .delegate(sendTransactionItemReader)
                    .build())
            .processor(itemOut)
            .writer(transactionItemWriter)
            
            // 2. Orquestación Multi-hilo
            .taskExecutor(taskExecutor())
            
            // 3. Resiliencia y Saltos
            .faultTolerant()
            .skipPolicy(skipPolicies)
            .listener(skipListener)
            
            // 4. Reintentos ante bloqueos concurrentes en PostgreSQL
            .retryLimit(3)
            .retry(CannotAcquireLockException.class)
            .retry(DeadlockLoserDataAccessException.class)
            .build();
}
    

    /**
     * Define y configura el trabajo (Job) principal de Spring Batch para el procesamiento de transacciones.
     * Un Job es la entidad de nivel superior que encapsula todo el proceso batch. Su responsabilidad
     * es orquestar la ejecución de uno o múltiples pasos (Steps) en secuencia o condicionalmente.
     *
     * Datos que recibe (Dependencias inyectadas por el contexto de Spring):
     * @param jobRepository   Repositorio central de Spring Batch encargado de persistir los 
     *                        metadatos de la ejecución (estado del Job, fechas de inicio/fin, 
     *                        parámetros y contexto de ejecución).
     * @param transactionStep El paso (Step) principal previamente configurado, que contiene toda la 
     *                        lógica concurrente de lectura, procesamiento y escritura.
     *
     * Lo que retorna / Efecto:
     * @return Job El trabajo configurado y listo para ser lanzado por un JobLauncher (ya sea invocado 
     *             manualmente mediante un endpoint/controlador, o programado mediante un Scheduler).
     */
    @Bean
    public Job transactionJob(JobRepository jobRepository, Step transactionStep) {
        return new JobBuilder("transactionJob", jobRepository)
                .start(transactionStep) 
                .build();
    }


        /* ----------------------------------------------------------------------
    SECCIÓN: Componentes para la lectura y escritura del trabajo de calculo de interes de las cuentas
    ---------------------------------------------------------------------- */

    
    /**
     * Configura un lector de archivos (ItemReader) para procesar datos de cuentas de clientes.
     * Su objetivo es leer un archivo CSV (probablemente destinado a un cálculo de intereses),
     * separar los valores y transformarlos en objetos AccountDTO para el resto del proceso batch.
     *
     * Datos que recibe (Contexto de entrada):
     * - Lee desde el recurso definido en la variable 'interestCSV'.
     * - Espera un archivo CSV con 5 columnas en este orden exacto: 
     *   "id", "clientName", "balance", "age", "accountType".
     * - Omite la primera línea al iniciar la lectura, asumiendo que es un encabezado.
     *
     * Lo que retorna:
     * @return FlatFileItemReader<AccountDTO> Un componente de lectura que emite una 
     * instancia de AccountDTO por cada línea procesada del archivo CSV.
     */
    @Bean
    public FlatFileItemReader<AccountDTO> interestItemReader() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("id", "clientName", "balance", "age", "accountType"); 

        BeanWrapperFieldSetMapper<AccountDTO> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(AccountDTO.class);
        fieldSetMapper.setConversionService(createConversionService());


        DefaultLineMapper<AccountDTO> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        return new FlatFileItemReaderBuilder<AccountDTO>()
                .name("interestItemReader")
                .resource(interestCSV)
                .linesToSkip(1) // encabezado
                .lineMapper(lineMapper)
                .build();
    }


/**
     * Configura un escritor por lotes (ItemWriter) para persistir los datos de las cuentas.
     * Su función es recibir bloques de objetos AccountDTO y ejecutar inserciones masivas
     * en la base de datos de manera eficiente.
     *
     * @param dataSource Fuente de conexión a la base de datos inyectada por Spring.
     * 
     * Datos que recibe (Entrada en ejecución):
     * - Chunks de instancias de AccountDTO procesados en el Step.
     * - Extrae los valores de las propiedades del DTO mapeándolos a los parámetros SQL:
     *   - :clientName  -> accountDTO.getClientName()
     *   - :balance     -> accountDTO.getBalance()
     *   - :age         -> accountDTO.getAge()
     *   - :accountType -> accountDTO.getAccountType()
     *
     * Lo que retorna / Efecto:
     * @return JdbcBatchItemWriter<AccountDTO> Componente configurado para insertar 
     * nuevos registros en la tabla 'account'.
     */
    @Bean
    public JdbcBatchItemWriter<AccountDTO> interestItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<AccountDTO>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                // El campo id se omite en el INSERT para que PostgreSQL use el autoincremento (SERIAL)
                .sql("INSERT INTO account (client_name, balance, age, account_type) " +
                     "VALUES (:clientName, :balance, :age, :accountType)")
                .dataSource(dataSource)
                .build();
    }


/**
     * Define y configura el paso (Step) dedicado al procesamiento de intereses de las cuentas.
     * Este componente orquesta el flujo completo de lectura, aplicación de lógica de negocio 
     * (cálculo de intereses) y escritura por bloques (chunks). Está diseñado para alta 
     * concurrencia, incorporando procesamiento multi-hilo, manejo de errores y resiliencia.
     *
     * Datos que recibe (Dependencias inyectadas por el contexto de Spring):
     * @param jobRepository      Repositorio para gestionar el estado y metadatos de este Step.
     * @param transactionManager Gestor de transacciones que asegura la integridad de cada chunk (commit/rollback).
     * @param interestItemReader Lector sincronizado que provee los datos de las cuentas desde el CSV.
     * @param interestProcessor  Procesador que aplica la lógica de negocio (ej. cálculo de tasas de interés) a cada cuenta.
     * @param interestItemWriter Escritor JDBC que guarda las cuentas actualizadas en la base de datos.
     * @param skipListener       Listener para registrar o manejar los registros que fallan y son omitidos.
     * @param skipPolicies       Reglas que determinan qué excepciones son tolerables para saltar un registro.
     *
     * Lo que retorna / Efecto:
     * @return Step El paso configurado para el cálculo de intereses, listo para ser integrado en un Job.
     */
   @Bean
    public Step interestStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                         FlatFileItemReader<AccountDTO> interestItemReader,
                         InterestProcessor interestProcessor,
                         JdbcBatchItemWriter<AccountDTO> interestItemWriter, CustomSkipListener skipListener, CustomSkipPolicies skipPolicies) {
    return new StepBuilder("interestStep", jobRepository)
            .<AccountDTO, AccountDTO>chunk(CHUNK_SIZE, transactionManager)
            
            // 1. Sincronización del Lector
            .reader(new SynchronizedItemStreamReaderBuilder<AccountDTO>()
                    .delegate(interestItemReader)
                    .build())
            .processor(interestProcessor)
            .writer(interestItemWriter)
            
            // 2. Orquestación Multi-hilo
            .taskExecutor(taskExecutor())
            
            // 3. Resiliencia y Saltos
            .faultTolerant()
            .skipPolicy(skipPolicies)
            .listener(skipListener)
            
            // 4. Reintentos ante bloqueos concurrentes en PostgreSQL
            .retryLimit(3)
            .retry(CannotAcquireLockException.class)
            .retry(DeadlockLoserDataAccessException.class)
            .build();
}

/**
     * Define y configura el trabajo (Job) de Spring Batch para el procesamiento de intereses.
     * Su responsabilidad es encapsular y coordinar la ejecución del paso (Step) definido 
     * previamente, permitiendo que el framework gestione su ciclo de vida, historial y estado.
     *
     * Datos que recibe (Dependencias inyectadas por el contexto de Spring):
     * @param jobRepository Repositorio central de Spring Batch que guarda los metadatos 
     *                      de la ejecución (como el estado, tiempo de inicio/fin y resultados).
     * @param interestStep  El paso (Step) específico de intereses que contiene toda la lógica 
     *                      concurrente de lectura, cálculo y escritura de cuentas.
     *
     * Lo que retorna / Efecto:
     * @return Job El trabajo configurado y listo para ser ejecutado (por ejemplo, a través 
     *             de un JobLauncher invocado por un controlador REST o un proceso programado).
     */
    @Bean
    public Job interestJob(JobRepository jobRepository, Step interestStep) {
        return new JobBuilder("interestJob", jobRepository)
                .start(interestStep)
                .build();
    }

    /* ----------------------------------------------------------------------
    SECCIÓN: Componentes para la lectura y escritura del trabajo de registro de estado de las cuentas
    ---------------------------------------------------------------------- */


    /**
     * Configura un lector de archivos (ItemReader) para procesar estados de cuenta.
     * Además de leer y mapear el CSV, este componente incluye una lógica de limpieza de datos 
     * (Data Cleansing) mediante un convertidor personalizado, permitiendo procesar archivos 
     * con formatos de fecha inconsistentes sin interrumpir la ejecución.
     *
     * Datos que recibe (Contexto de entrada):
     * - Lee desde el recurso definido en la variable 'statementCSV'.
     * - Espera un archivo CSV con 5 columnas en este orden exacto: 
     *   "accountId", "statementDate", "transaction", "amount", "description".
     * - Omite la primera línea (encabezado).
     *
     * Lo que retorna:
     * @return FlatFileItemReader<StatementDTO> Un componente de lectura que emite una 
     * instancia de StatementDTO por cada línea procesada y estandarizada del archivo CSV.
     */
    @Bean
    public FlatFileItemReader<StatementDTO> statementItemReader() {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames("accountId", "statementDate", "transaction", "amount", "description");

        BeanWrapperFieldSetMapper<StatementDTO> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(StatementDTO.class);
        fieldSetMapper.setConversionService(createConversionService());

        DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(String.class, LocalDate.class, new Converter<String, LocalDate>() {
        @Override
        public LocalDate convert(String source) {
                // Lista de formatos identificados en el log de errores
                String[] formatos = {"yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"};
                
                for (String formato : formatos) {
                try {
                        return LocalDate.parse(source, DateTimeFormatter.ofPattern(formato));
                } catch (DateTimeParseException e) {
                        // Continúa con el siguiente formato de la lista si este falla
                }
                }
                // Si ninguno coincide, lanza excepción para que Spring Batch lo cuente como un Skip válido
                throw new IllegalArgumentException("Formato de fecha no soportado: " + source);
        }
        });

    fieldSetMapper.setConversionService(conversionService);

        DefaultLineMapper<StatementDTO> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        return new FlatFileItemReaderBuilder<StatementDTO>()
                .name("statementItemReader")
                .resource(statementCSV)
                .linesToSkip(1) // Salta el encabezado
                .lineMapper(lineMapper)
                .build();
    }

    /**
     * Configura un escritor por lotes (ItemWriter) para persistir los estados de cuenta anuales.
     * Su función es recibir los bloques de objetos StatementDTO (cuyas fechas ya fueron 
     * limpiadas y estandarizadas por el lector) y ejecutar inserciones masivas (batch insert) 
     * en la base de datos para garantizar un alto rendimiento.
     *
     * @param dataSource Fuente de conexión a la base de datos inyectada por el contexto de Spring.
     * 
     * Datos que recibe (Entrada en ejecución):
     * - Chunks (lotes) de instancias de StatementDTO validadas en el Step.
     * - Extrae los valores de las propiedades del DTO y los mapea a los parámetros SQL:
     *   - :accountId     -> statementDTO.getAccountId()
     *   - :statementDate -> statementDTO.getStatementDate()
     *   - :transaction   -> statementDTO.getTransaction()
     *   - :amount        -> statementDTO.getAmount()
     *   - :description   -> statementDTO.getDescription()
     *
     * Lo que retorna / Efecto:
     * @return JdbcBatchItemWriter<StatementDTO> Componente de escritura configurado para 
     * insertar los registros procesados en la tabla 'annual_statement'.
     */
    @Bean
    public JdbcBatchItemWriter<StatementDTO> statementItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<StatementDTO>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO annual_statement (account_id, statement_date, transaction, amount, description) " +
                     "VALUES (:accountId, :statementDate, :transaction, :amount, :description)")
                .dataSource(dataSource)
                .build();
    }


/**
     * Define y configura el paso (Step) para el procesamiento de los estados de cuenta (statements).
     * Este componente orquesta el flujo completo (lectura, procesamiento y escritura) utilizando 
     * procesamiento por bloques (chunks). Está optimizado para la concurrencia e incluye políticas 
     * de tolerancia a fallos clave para manejar datos de entrada irregulares.
     *
     * Datos que recibe (Dependencias inyectadas por el contexto de Spring):
     * @param jobRepository      Repositorio de Spring Batch para gestionar el estado de la ejecución.
     * @param transactionManager Gestor que asegura la atomicidad (commit/rollback) de cada bloque de datos.
     * @param statementItemReader Lector sincronizado que lee y estandariza los CSV de estados de cuenta.
     * @param statementProcessor Procesador que aplica validaciones o lógica de negocio adicional a los estados de cuenta.
     * @param statementItemWriter Escritor JDBC que realiza la inserción masiva en la tabla 'annual_statement'.
     * @param skipListener       Listener que intercepta los registros con errores (ej. fechas inválidas del lector) para registrar el incidente.
     * @param skipPolicies       Reglas que definen qué excepciones (ej. IllegalArgumentException) no deben detener el proceso global.
     *
     * Lo que retorna / Efecto:
     * @return Step El paso configurado y empaquetado, listo para ser ejecutado dentro de un Job.
     */
    @Bean
    public Step statementStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                          FlatFileItemReader<StatementDTO> statementItemReader,
                          StatementProcessor statementProcessor,
                          JdbcBatchItemWriter<StatementDTO> statementItemWriter, CustomSkipListener skipListener, CustomSkipPolicies skipPolicies) {
    return new StepBuilder("statementStep", jobRepository)
            .<StatementDTO, StatementDTO>chunk(CHUNK_SIZE, transactionManager)
            
            // 1. Sincronización del Lector
            .reader(new SynchronizedItemStreamReaderBuilder<StatementDTO>()
                    .delegate(statementItemReader)
                    .build())
            .processor(statementProcessor)
            .writer(statementItemWriter)
            
            // 2. Orquestación Multi-hilo
            .taskExecutor(taskExecutor())
            
            // 3. Resiliencia y Saltos
            .faultTolerant()
            .skipPolicy(skipPolicies)
            .listener(skipListener)
            
            // 4. Reintentos ante bloqueos concurrentes en PostgreSQL
            .retryLimit(3)
            .retry(CannotAcquireLockException.class)
            .retry(DeadlockLoserDataAccessException.class)
            .build();
}

/**
     * Define y configura el trabajo (Job) de Spring Batch para el procesamiento de los estados de cuenta.
     * Este componente actúa como el orquestador principal del flujo, encapsulando el paso (Step) 
     * de lectura, limpieza de datos y persistencia, permitiendo a Spring Batch rastrear 
     * su ejecución de principio a fin.
     *
     * Datos que recibe (Dependencias inyectadas por el contexto de Spring):
     * @param jobRepository Repositorio central de Spring Batch encargado de registrar y persistir 
     *                      los metadatos de la ejecución (estado actual, fechas, parámetros y errores).
     * @param statementStep El paso (Step) específico de los estados de cuenta, que contiene la 
     *                      lógica de lectura con tolerancia a fallos, procesamiento y escritura.
     *
     * Lo que retorna / Efecto:
     * @return Job El trabajo configurado bajo el nombre "statementJob", listo para ser invocado 
     *             (por ejemplo, mediante un JobLauncher, una tarea programada o un endpoint).
     */
    @Bean
    public Job statementJob(JobRepository jobRepository, Step statementStep) {
        return new JobBuilder("statementJob", jobRepository)
                .start(statementStep)
                .build();
    }

    /* ----------------------------------------------------------------------
       SECCIÓN: CLASE COMPLEMENTARIA PARA CONVERSION Y LIMPIEZA DE DATOS
       ---------------------------------------------------------------------- */

    /**
     * Crea y configura un servicio de conversión de datos (ConversionService) personalizado.
     * Este componente centraliza las reglas de limpieza y transformación de datos para los 
     * ItemReaders. Su propósito es interceptar las cadenas de texto (Strings) extraídas del CSV 
     * y convertirlas de forma segura a tipos de datos robustos de Java, manejando valores 
     * nulos, vacíos o inconsistentes para evitar que el proceso Batch falle por excepciones de formato.
     *
     * Datos que recibe (Contexto de ejecución):
     * - Este es un método de configuración interna (no recibe parámetros). 
     * - Durante el procesamiento, Spring Batch inyecta automáticamente el valor String de 
     *   cada celda del CSV en los convertidores aquí definidos.
     *
     * Lo que retorna / Efecto:
     * @return DefaultConversionService Un servicio de conversión enriquecido con tres reglas 
     *         de negocio específicas: fechas resilientes, montos enteros seguros y saldos decimales seguros.
     */
    private DefaultConversionService createConversionService() {
        // Inicializa el servicio de conversión estándar de Spring
        DefaultConversionService conversionService = new DefaultConversionService();
        
        // 1. Reparador de Fechas
        // Convierte Strings a LocalDate tolerando múltiples formatos y espacios en blanco.
        conversionService.addConverter(String.class, LocalDate.class, new Converter<String, LocalDate>() {
            @Override
            public LocalDate convert(String source) {
                // Manejo de nulos: si no hay fecha en el CSV, retorna null de forma segura
                if (source == null || source.trim().isEmpty()) return null;
                
                // Diccionario de formatos de fecha permitidos
                String[] formatos = {"yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd"};
                for (String formato : formatos) {
                    try {
                        // Limpia espacios en blanco (trim) e intenta parsear
                        return LocalDate.parse(source.trim(), DateTimeFormatter.ofPattern(formato));
                    } catch (Exception e) {
                        // Captura la excepción silenciosamente para que el bucle intente con el siguiente formato
                    }
                }
                // Si ningún formato coincide, lanza una excepción para que el SkipListener 
                // marque la línea como omitida sin botar todo el Job.
                throw new IllegalArgumentException("Fecha no procesable: " + source);
            }
        });

        // 2. Reparador de Montos Enteros (transacciones vacías)
        // Previene la clásica NumberFormatException cuando un campo numérico viene vacío en el CSV.
        conversionService.addConverter(String.class, Integer.class, new Converter<String, Integer>() {
            @Override
            public Integer convert(String source) {
                // Si la celda de la transacción está vacía, asume un valor por defecto de 0
                if (source == null || source.trim().isEmpty()) return 0;
                
                // Limpia espacios invisibles antes de convertir a número
                return Integer.parseInt(source.trim());
            }
        });

        // 3. Reparador de Saldos Decimales (cuentas vacías)
        // Convierte el texto a BigDecimal (esencial para precisión en cálculos financieros).
        conversionService.addConverter(String.class, BigDecimal.class, new Converter<String, BigDecimal>() {
            @Override
            public BigDecimal convert(String source) {
                // Si no hay saldo reportado en el archivo, inicializa en 0.0 mediante la constante segura
                if (source == null || source.trim().isEmpty()) return BigDecimal.ZERO;
                
                // Instancia el BigDecimal eliminando posibles espacios en blanco
                return new BigDecimal(source.trim());
            }
        });

        return conversionService;
    }

    /**
     * Configura y provee un pool de hilos (Thread Pool) para la ejecución concurrente en Spring Batch.
     * Su propósito principal es gestionar la creación y el ciclo de vida de los hilos que 
     * procesarán los chunks (bloques de datos) en paralelo, optimizando significativamente 
     * los tiempos de ejecución de los Jobs.
     *
     * Datos que recibe (Contexto de ejecución):
     * - Este es un método de configuración (no recibe parámetros directos).
     * - Durante la ejecución, recibe tareas (Runnables/Callables) delegadas por los Steps 
     *   de Spring Batch para procesar cada chunk de datos.
     *
     * Lo que retorna / Efecto:
     * @return TaskExecutor Una instancia de ThreadPoolTaskExecutor completamente inicializada 
     *         y ajustada para balancear la carga de trabajo sin saturar la CPU o la base de datos.
     */
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // Hilos base activos
        executor.setMaxPoolSize(20);  // Límite máximo de hilos paralelos
        executor.setQueueCapacity(50); // Capacidad de espera
        executor.setThreadNamePrefix("BatchThread-");
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return executor;
    }


    /* ----------------------------------------------------------------------
       SECCIÓN: ORQUESTACIÓN CON MASTERJOB Y FLUJOS PARALELOS
       ---------------------------------------------------------------------- */

    /**
     * Configura un "JobStep" para el procesamiento de transacciones.
     * El patrón JobStep permite encapsular un Job completo (con todos sus propios Steps de lectura,
     * procesamiento y escritura) para que actúe como un único paso dentro de un "Job Maestro" superior.
     * Esto facilita la ejecución secuencial o paralela de múltiples procesos batch pesados.
     *
     * Datos que recibe (Dependencias inyectadas):
     * @param jobRepository Repositorio central para persistir el estado de la ejecución.
     * @param jobOperator   Componente que permite manipular y lanzar el Job anidado de forma programática.
     * @param transactionJob El Job independiente de transacciones, inyectado específicamente por su nombre mediante @Qualifier.
     *
     * Lo que retorna:
     * @return Step Un paso que, cuando sea invocado por el Job Maestro, disparará la ejecución completa de 'transactionJob'.
     */
    @Bean
    public Step transactionJobStep(JobRepository jobRepository, JobOperator jobOperator, @Qualifier("transactionJob") Job transactionJob) {
        return new StepBuilder("transactionJobStep", jobRepository)
                .job(transactionJob)     // Asigna el Job que se va a encapsular
                .operator(jobOperator)   // Asigna el operador encargado de lanzar este Job anidado
                .build();
    }

    /**
     * Configura un "JobStep" para el cálculo de intereses.
     * Sigue el mismo patrón arquitectónico: envuelve el Job de intereses para poder encadenarlo 
     * en el flujo general del sistema de cuentas, permitiendo que se ejecute solo cuando el paso anterior 
     * (por ejemplo, transacciones) haya concluido exitosamente.
     *
     * @param jobRepository Repositorio central.
     * @param jobOperator   Operador para gestionar la ejecución anidada.
     * @param interestJob   El Job independiente de intereses (@Qualifier).
     * @return Step Un paso que orquesta el 'interestJob'.
     */
    @Bean
    public Step interestJobStep(JobRepository jobRepository, JobOperator jobOperator, @Qualifier("interestJob") Job interestJob) {
        return new StepBuilder("interestJobStep", jobRepository)
                .job(interestJob)
                .operator(jobOperator)
                .build();
    }

    /**
     * Configura un "JobStep" para la generación de estados de cuenta.
     * Al encapsular este proceso, garantizas que la limpieza, procesamiento y persistencia 
     * de los reportes anuales se integre de manera modular dentro del flujo de cierre (Master Job).
     *
     * @param jobRepository Repositorio central.
     * @param jobOperator   Operador para gestionar la ejecución anidada.
     * @param statementJob  El Job independiente de estados de cuenta (@Qualifier).
     * @return Step Un paso que orquesta el 'statementJob'.
     */
    @Bean
    public Step statementJobStep(JobRepository jobRepository, JobOperator jobOperator, @Qualifier("statementJob") Job statementJob) {
        return new StepBuilder("statementJobStep", jobRepository)
                .job(statementJob)
                .operator(jobOperator)
                .build();
    }

    
    // 2. Creación de Flujos (Flows) a partir de los JobSteps

    /**
     * Envuelve el paso de transacciones en un objeto Flow.
     * En Spring Batch, un Flow es una agrupación lógica de pasos (Steps) que permite 
     * construir ramas de ejecución condicionales o, como en este caso, paralelas.
     */
    @Bean
    public Flow transactionFlow(Step transactionJobStep) {
        return new FlowBuilder<Flow>("transactionFlow").start(transactionJobStep).build();
    }

    /**
     * Envuelve el paso de cálculo de intereses en su propio Flow.
     * Es requisito fundamental transformar el Step en Flow para poder inyectarlo 
     * en el método .split() del Job Maestro.
     */
    @Bean
    public Flow interestFlow(Step interestJobStep) {
        return new FlowBuilder<Flow>("interestFlow").start(interestJobStep).build();
    }

    /**
     * Envuelve el paso de estados de cuenta en su propio Flow.
     */
    @Bean
    public Flow statementFlow(Step statementJobStep) {
        return new FlowBuilder<Flow>("statementFlow").start(statementJobStep).build();
    }

    // 3. El Job Maestro que divide la ejecución usando tus hilos paralelos

    /**
     * Define el "Master Job" (Job orquestador) que dispara la ejecución concurrente de los tres procesos.
     * Al usar flujos paralelos (Split), los tres Jobs encapsulados (transacciones, intereses y 
     * estados de cuenta) se ejecutarán al mismo tiempo, compitiendo por los recursos del sistema 
     * de manera controlada gracias al TaskExecutor que configuraste anteriormente.
     *
     * Datos que recibe (Dependencias inyectadas):
     * @param jobRepository   Repositorio central para el estado global de este macro-proceso.
     * @param taskExecutor    El pool de hilos (Thread Pool) encargado de asignar un hilo 
     *                        separado para cada flujo.
     * @param transactionFlow El flujo encapsulado de las transacciones.
     * @param interestFlow    El flujo encapsulado de los intereses.
     * @param statementFlow   El flujo encapsulado de los estados de cuenta.
     *
     * Lo que retorna / Efecto:
     * @return Job El orquestador principal. Al lanzarlo, el tiempo total de procesamiento 
     *             será el equivalente al del flujo que más tarde en terminar, y no a la suma de los tres.
     */
    @Bean
    public Job masterJob(JobRepository jobRepository, TaskExecutor taskExecutor, 
                         Flow transactionFlow, Flow interestFlow, Flow statementFlow) {
        
        return new JobBuilder("masterJob", jobRepository)
                // Inicia la estructura con el primer flujo (transacciones)
                .start(transactionFlow)
                
                // .split() introduce la bifurcación, delegando la ejecución al pool de hilos
                .split(taskExecutor) 
                
                // .add() indica cuáles flujos adicionales deben arrancar en paralelo al flujo inicial
                .add(interestFlow, statementFlow) 
                
                // Cierra la configuración de la bifurcación paralela
                .end()
                
                // Construye el Master Job
                .build();
    }

}
