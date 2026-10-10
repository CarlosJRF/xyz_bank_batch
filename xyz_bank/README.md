# XYZ Bank - Procesamiento Batch Financiero (Optimizado y Resiliente)

Aplicación ETL (Extract, Transform, Load) desarrollada con Java 21 y Spring Batch para automatizar la ingesta, cálculo y validación de registros financieros del banco XYZ. 

Este proyecto académico demuestra la implementación avanzada de procesamiento por lotes para manejar grandes volúmenes de datos mediante orquestación concurrente (multi-hilo), transacciones seguras y resiliencia ante fallos de base de datos, garantizando la integridad de los saldos y reportes de auditoría.

## 🚀 Tecnologías Utilizadas

*   **Java 21**
*   **Spring Boot** (v4.1)
*   **Spring Batch**: Orquestación de Master Jobs, Flows paralelos, Steps y procesamiento asíncrono por chunks.
*   **PostgreSQL**: Base de datos relacional para persistencia.
*   **Lombok**: Reducción de código repetitivo (boilerplate) en los DTOs.
*   **Maven**: Gestión de dependencias y construcción del proyecto.

## ⚙️ Arquitectura de Orquestación Paralela (`masterJob`)

El procesamiento legacy secuencial fue refactorizado hacia una arquitectura de alta concurrencia. El sistema utiliza un **Job Maestro** que divide la carga de trabajo en tres flujos independientes (`Flows`) que se ejecutan de manera simultánea utilizando un `ThreadPoolTaskExecutor` dimensionado de forma óptima.

### Flujo 1: Carga de Transacciones (`transactionStep`)
*   **Reader:** Lee registros desde `transacciones.csv` utilizando `SynchronizedItemStreamReader` para asegurar el aislamiento entre hilos.
*   **Processor:** Invalida y descarta registros con montos negativos (Lógica de negocio).
*   **Writer:** Inserta los datos validados en la tabla `transaction_report` mediante JDBC Batch.

### Flujo 2: Cálculo de Intereses (`interestStep`)
*   **Reader:** Lee saldos y tipos de cuenta desde `intereses.csv` de forma sincronizada (Thread-Safe).
*   **Processor:** Aplica lógica de negocios utilizando *Switch Expressions* de Java para calcular el nuevo saldo en base a tasas específicas (`AHORRO`: +0.5%, `PRESTAMO`: +2.0%, `HIPOTECA`: +3.0%).
*   **Writer:** Registra las cuentas y sus saldos actualizados en la tabla `account`.

### Flujo 3: Auditoría Anual (`statementStep`)
*   **Reader:** Extrae el reporte detallado desde `cuentas_anuales.csv` filtrando formatos corruptos.
*   **Processor:** Estandariza las descripciones a mayúsculas y audita los datos (descartando filas sin monto).
*   **Writer:** Guarda el estado de cuenta histórico en la tabla `annual_statement`.

## 🛡️ Resiliencia y Tolerancia a Fallos

El sistema fue diseñado para no interrumpirse ante datos corruptos o cuellos de botella:
1. **Data Cleansing Centralizado:** Implementación de un `DefaultConversionService` que repara fechas con formatos inconsistentes, maneja nulos y convierte campos vacíos a ceros de manera segura.
2. **Políticas de Salto (SkipPolicy):** Se toleran hasta 1000 excepciones de parsing, validación matemática o integridad de datos. Los errores son capturados y documentados por un `CustomSkipListener`.
3. **Manejo de Deadlocks (RetryPolicy):** El procesamiento concurrente hacia PostgreSQL está protegido contra colisiones de inserción (`CannotAcquireLockException` y `DeadlockLoserDataAccessException`), permitiendo hasta 3 reintentos automáticos por cada hilo.

## 🗄️ Modelo de Datos (PostgreSQL)

El sistema genera e interactúa con las siguientes tablas:
1.  `transaction_report`: Historial de operaciones transaccionales.
2.  `account`: Catálogo de cuentas, tipos y saldos actualizados post-intereses.
3.  `annual_statement`: Registro detallado de movimientos para auditorías anuales.

## 🛠️ Configuración y Ejecución

### 1. Preparar la Base de Datos
Asegúrate de tener PostgreSQL ejecutándose localmente en el puerto `5432`. Crea una base de datos llamada `xyzbank`.
Las credenciales por defecto configuradas en `application.properties` son:
*   **Usuario:** `postgres`
*   **Password:** `c1j9r9f3!psql`

### 2. Archivos de Entrada (CSV)
Los siguientes archivos deben estar ubicados en la carpeta `src/main/resources`:
*   `transacciones.csv`
*   `intereses.csv`
*   `cuentas_anuales.csv`

### 3. Propiedades de Orquestación
Asegúrate de que en el archivo `application.properties` se lance exclusivamente el Job Orquestador:
```properties
spring.batch.job.name=masterJob