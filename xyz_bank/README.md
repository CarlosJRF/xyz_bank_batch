# XYZ Bank - Procesamiento Batch Financiero (Optimizado y Resiliente)

Aplicación ETL (Extract, Transform, Load) desarrollada con Java 21 y Spring Batch para automatizar la ingesta, cálculo y validación de registros financieros del banco XYZ.

Este proyecto académico procesa el dataset oficial **[fin_legacy_data](https://github.com/KariVillagran/fin_legacy_data)** (carpeta `data/semana_3`, 1.000 filas por archivo) mediante orquestación concurrente, validaciones explícitas por tipo de error, trazabilidad de rechazos y resiliencia ante fallos de base de datos.

## 🚀 Tecnologías Utilizadas

*   **Java 21**
*   **Spring Boot** (v4.1)
*   **Spring Batch 6**: Master Job, Flows paralelos, Steps multihilo tolerantes a fallos.
*   **PostgreSQL**: Persistencia de resultados y bitácora de calidad de datos.
*   **Lombok**: Reducción de boilerplate en los DTOs.
*   **Maven**: Gestión de dependencias y construcción del proyecto.

## ⚙️ Arquitectura de Orquestación (`masterJob`)

```
masterJob (RunIdIncrementer, re-ejecutable)
 ├─ resetTablesStep           TRUNCATE de tablas destino (recarga idempotente)
 └─ split (flowTaskExecutor: 3 hilos, uno por flujo)
     ├─ transactionFlow → transactionJob → transactionStep  (movimientos_financieros_diarios.csv)
     ├─ interestFlow    → interestJob    → interestStep     (intereses_trimestrales.csv)
     └─ statementFlow   → statementJob   → statementStep    (estados_financieros_anuales.csv)
           cada step: SynchronizedItemStreamReader → Processor (validación) → JdbcBatchItemWriter
                      chunks ejecutados por stepTaskExecutor (3 × step-threads hilos)
```

Los lectores entregan cada fila **como texto crudo** (`TransactionInput`, `AccountInput`, `StatementInput`) con su número de línea. Toda conversión y validación ocurre en los procesadores, de modo que cada problema recibe un código de error explícito en lugar de convertirse silenciosamente (antes, un monto o saldo vacío se convertía en `0` y se persistía).

## ✅ Validaciones por procesador (cobertura del dataset oficial)

Cada registro inválido lanza `InvalidRecordException` con **todos** los códigos detectados; el primero se registra como causa principal. Los registros válidos pueden recibir correcciones trazadas (no se rechazan).

### `TransactionProcessor` — movimientos_financieros_diarios.csv

| Problema en el dataset | Ejemplo | Código | Acción | Casos (semana_3) |
|---|---|---|---|---|
| Tipo de transacción inválido | `invalido`, `desconocido` | `INVALID_TRANSACTION_TYPE` | Rechazo | 256 |
| Monto vacío | `5,2024-01-03,,debito` | `MISSING_AMOUNT` | Rechazo | 162 |
| Monto negativo | `-200`, `-100` | `NEGATIVE_AMOUNT` | Rechazo | 121 |
| Fecha inexistente | `2024-13-01` | `INVALID_DATE` | Rechazo | 49 |
| Monto cero | `0` | `ZERO_AMOUNT` | Rechazo | 20 |
| Fecha en formato no estándar | `2024/03/05`, `11-12-2024`, `24/10/2024` | `DATE_FORMAT_NORMALIZED` | Corrección a ISO | 284 |
| Id inválido / repetido, fecha vacía, monto no numérico | — | `INVALID_ID`, `DUPLICATE_RECORD`, `MISSING_DATE`, `INVALID_AMOUNT` | Rechazo | 0 |

Resultado: **392 persistidas / 608 rechazadas**.

### `InterestProcessor` — intereses_trimestrales.csv

| Problema en el dataset | Ejemplo | Código | Acción | Casos (semana_3) |
|---|---|---|---|---|
| Saldo vacío | `134,Steve Rogers,,45,hipoteca` | `MISSING_BALANCE` | Rechazo | 197 |
| Tipo de cuenta no elegible | `-1`, `unknown` | `INELIGIBLE_ACCOUNT_TYPE` | Rechazo | 163 |
| Edad fuera de rango (18–99) | `100`, `150` | `AGE_OUT_OF_RANGE` | Rechazo | 146 |
| Edad vacía | `131,Diana Prince,8000,,hipoteca` | `MISSING_AGE` | Rechazo | 126 |
| Titular no identificado | `Unknown` | `INVALID_CLIENT_NAME` | Rechazo | 48 |
| Fila idéntica a otra aceptada | — | `DUPLICATE_RECORD` | Rechazo | 0 (*) |
| Saldo negativo / no numérico, edad no numérica, cuenta inválida | — | `NEGATIVE_BALANCE`, `INVALID_BALANCE`, `INVALID_AGE`, `INVALID_ID` | Rechazo | 0 |

(*) El archivo tiene 7 filas repetidas, pero todas tienen además otro error, que se reporta como causa principal. `cuenta_id` **no** es único en este archivo (50 cuentas en 1.000 filas), por eso el duplicado se detecta sobre la fila completa. Solo las cuentas válidas reciben interés: AHORRO 0,5 %, PRESTAMO 2 %, HIPOTECA 3 % (redondeo a 2 decimales, HALF_EVEN). Un saldo `0` es válido (no genera interés). El rango de edad es configurable (`xyz.batch.min-age`, `xyz.batch.max-age`).

Resultado: **320 persistidas / 680 rechazadas**.

### `StatementProcessor` — estados_financieros_anuales.csv

| Problema en el dataset | Ejemplo | Código | Acción | Casos (semana_3) |
|---|---|---|---|---|
| Monto negativo | `-500`, `-1000` | `NEGATIVE_AMOUNT` | Rechazo | 232 |
| Monto cero | `0` | `ZERO_AMOUNT` | Rechazo | 116 |
| Monto vacío | `108,2024/01/24,compra,,Ingreso extra` | `MISSING_AMOUNT` | Rechazo | 48 |
| Movimiento duplicado (tras normalizar) | `deposito` vs `depósito` | `DUPLICATE_RECORD` | Rechazo | 1 |
| Fecha en formato no estándar | `2024/02/08`, `30/06/2024` | `DATE_FORMAT_NORMALIZED` | Corrección a ISO | 472 |
| Descripción vacía | `110,24-07-2024,retiro,1500,` | `MISSING_DESCRIPTION` | Corrección: `SIN DESCRIPCION` | 106 |
| Tipo con acento | `depósito` | `TRANSACTION_TYPE_NORMALIZED` | Corrección: `DEPOSITO` | 29 |
| Tipo desconocido, fecha inválida/vacía, cuenta inválida | — | `INVALID_TRANSACTION_TYPE`, `INVALID_DATE`, `MISSING_DATE`, `INVALID_ID` | Rechazo | 0 |

Resultado: **603 persistidos / 397 rechazados**.

Las correcciones solo se registran para registros que finalmente se aceptan (un registro rechazado no acumula correcciones).

**Decisiones de negocio documentadas**
* Las fechas `yyyy/MM/dd`, `dd/MM/yyyy` y `dd-MM-yyyy` se normalizan (día antes que mes, locale es-CL) porque representan ~75 % del archivo; se validan en modo estricto, por lo que `2024-13-01` o `2024-02-30` se rechazan.
* En estados de cuenta el monto es un valor absoluto y la dirección la da el tipo de movimiento, por lo que un monto negativo o cero se considera error del sistema legacy (tal como lo describe el README del dataset).
* Los conteos anteriores fueron verificados con un script independiente que aplica las mismas reglas y por el test `MasterJobIntegrationTests`. Cada una de las 3.000 líneas termina persistida o rechazada.

## 🛡️ Resiliencia y Tolerancia a Fallos

| Escenario | Tratamiento |
|---|---|
| Registro que no supera la validación de negocio | `InvalidRecordException` → **skip** sin rollback del chunk (`noRollback`), trazado en `data_quality_log` |
| Línea con columnas faltantes o sobrantes | `FlatFileParseException` → **skip** en lectura, `STRUCTURAL_ERROR` con línea y contenido original |
| Violación de restricción en BD (ej. `source_id` repetido, `CHECK`) | `DataIntegrityViolationException` → **skip** en escritura, `DB_CONSTRAINT_VIOLATION` |
| Bloqueo, deadlock o timeout de PostgreSQL | `TransientDataAccessException` → **retry** hasta `xyz.batch.retry-limit` (3) con backoff exponencial 100 ms → 1 s |
| Más rechazos que `xyz.batch.skip-limit` en un step | `SkipLimitExceededException` → el step falla (dato demasiado corrupto para continuar) |
| Cualquier otro error (bug, BD caída, archivo inexistente) | **No se salta**: el step y el job fallan, para no esconder fallos de infraestructura como si fueran datos malos |
| Reprocesamiento de un chunk tras rollback | `processorNonTransactional` + detección de duplicados por identidad: no se generan falsos duplicados ni correcciones repetidas |

La política anterior saltaba `NumberFormatException`/`BindException` genéricos, lanzaba "límite excedido" incluso para errores no omitibles y descartaba los inválidos con `return null` (no quedaban registrados ni contados como skip).

Además el esquema de BD actúa como segunda línea de defensa: `NOT NULL`, `CHECK (amount > 0)`, tipos permitidos y `source_id UNIQUE`.

### Bitácora `data_quality_log`
Cada rechazo y corrección queda con `step_name`, `line_number`, `action` (`RECHAZADO`/`CORREGIDO`), `error_code`, `error_detail` (todos los códigos) y `raw_record` (la línea original). Ejemplo:

```sql
SELECT step_name, action, error_code, COUNT(*) FROM data_quality_log
GROUP BY 1, 2, 3 ORDER BY 1, 2, 4 DESC;
```

Al finalizar, el job imprime este resumen (`CALIDAD ...`), un `RESUMEN` por step (leídos, escritos, rechazos por fase, commits, rollbacks, duración) y una línea `BENCHMARK` con el tiempo total.

## 🧵 Dimensionamiento del pool de hilos y comparación de configuraciones

### Escala real del procesamiento
* 3 archivos × 1.000 filas = **3.000 registros** por ejecución (~60 KB en total).
* El `masterJob` ya ejecuta los **3 flujos en paralelo**; dentro de cada step, Spring Batch 6 permite como máximo **4 chunks en vuelo** (throttle interno fijo de `TaskExecutorRepeatTemplate`).
* La lectura es secuencial por diseño (`SynchronizedItemStreamReader`) y la validación es trabajo de CPU trivial: el costo real está en los *round-trips* a PostgreSQL (un commit y un batch insert por chunk).

### Problemas de la configuración anterior
* Un único `ThreadPoolTaskExecutor` (core 10, max 20, cola 50) compartido por el `split` y por los tres steps. Los 3 hilos del split quedan bloqueados esperando su job anidado, ocupando capacidad del pool.
* Con cola de 50 el pool nunca crece por sobre 10 hilos, y por el throttle de 4 por step nunca podría haber más de 12 chunks en paralelo: el `max=20` no se usaba.
* `chunk=5` con 1.000 filas genera 200 commits por archivo: el costo transaccional dominaba.
* Hikari por defecto (10 conexiones) era menor que los hilos que podían pedir conexión simultáneamente.

### Configuración actual
| Pool | Hilos | Razón |
|---|---|---|
| `flowTaskExecutor` | 3 (fijo) | Uno por flujo paralelo; separado para no robar hilos a los chunks |
| `stepTaskExecutor` | 3 × `step-threads` = **6** | Hilos de trabajo de los chunks; con `step-threads=1` los steps son secuenciales |
| Hikari | **12** conexiones | 6 workers + 3 flujos + hilo principal + margen |

### Mediciones
Entorno: contenedor Linux de 4 vCPU, 15 GB RAM, PostgreSQL 16 local, JDK 21. Cada celda es la **mediana del tiempo total del `masterJob`** (línea `BENCHMARK ... totalMs`, sin contar el arranque de la JVM), con una JVM nueva por ejecución. Datos crudos en [`benchmark/results.csv`](benchmark/results.csv).

**Dataset oficial (3.000 registros) — 5 ejecuciones por celda, ms**

| step-threads \ chunk | 5 | 10 | 50 | 100 | 250 |
|---|---|---|---|---|---|
| 1 (secuencial) | 1233 | 1059 | 887 | 778 | 793 |
| 2 | 1218 | 1025 | 750 | **738** | 775 |
| 4 | 1120 | 943 | 767 | 730 | 724 |
| 8 | 1148 | 950 | 813 | 733 | 736 |

**Dataset ×10 (30.000 registros, generado con `benchmark/generate_scaled_dataset.py`) — 3 ejecuciones por celda, ms**

| step-threads \ chunk | 5 | 10 | 50 | 100 | 250 |
|---|---|---|---|---|---|
| 1 (secuencial) | 4157 | 3315 | 2612 | 2529 | 2471 |
| 2 | 4363 | 3485 | 2476 | **2481** | 2347 |
| 4 | 4519 | 3339 | 2632 | 2564 | 2507 |
| 8 | 4536 | 3399 | 2515 | 2502 | 2421 |

### Conclusiones
1. **El tamaño de chunk es la palanca principal**: pasar de 5 a 100 reduce el tiempo ~40 % en el dataset oficial (1.233 → 778 ms secuencial) y ~40 % en el ×10, porque reduce los commits de 200 a 10 por archivo. Sobre 100 ya no hay mejora apreciable.
2. **Más hilos por step casi no aportan a esta escala**: con chunk 100, 2, 4 y 8 hilos quedan dentro del ruido de medición (dispersión de ±30–60 ms entre repeticiones, ±150 ms en ×10). La concurrencia útil ya la entregan los 3 flujos paralelos sobre 4 vCPU; sumar hilos solo agrega contención en el lector sincronizado y en el pool de conexiones. Más de 4 hilos por step es inútil por el throttle de Spring Batch.
3. Se eligió **`chunk-size=100` y `step-threads=2`** (6 hilos de trabajo): ~35 % más rápido que la configuración original (chunk 5 con hasta 4 chunks por step: 1.120 → 738 ms), con el pool más pequeño que captura la mejora, menos conexiones a la BD y menos trabajo perdido si un chunk debe reintentarse.
4. Si el volumen creciera en órdenes de magnitud, la siguiente palanca no son más hilos sino particionar los archivos (`Partitioner`) para paralelizar también la lectura.

Para reproducir (con PostgreSQL arriba y el jar construido):
```bash
benchmark/run_benchmark.sh benchmark/mis_resultados.csv oficial 5
python3 benchmark/generate_scaled_dataset.py 10 /tmp/x10
benchmark/run_benchmark.sh benchmark/mis_resultados.csv x10 3 \
  --xyz.batch.input.transactions=file:/tmp/x10/movimientos_financieros_diarios.csv \
  --xyz.batch.input.interests=file:/tmp/x10/intereses_trimestrales.csv \
  --xyz.batch.input.statements=file:/tmp/x10/estados_financieros_anuales.csv \
  --xyz.batch.skip-limit=100000
```

## 🗄️ Modelo de Datos (PostgreSQL)

Creado automáticamente por `schema-postgresql.sql` (`spring.sql.init.mode=always`):
1.  `transaction_report`: transacciones válidas (`source_id` único, monto > 0, `DEBITO`/`CREDITO`).
2.  `account`: cuentas elegibles con `balance` (saldo + interés), `interest`, `age` y `account_type`.
3.  `annual_statement`: movimientos anuales válidos y normalizados.
4.  `data_quality_log`: rechazos y correcciones.

> Si creaste las tablas con una versión anterior del esquema, elimínalas una vez:
> `DROP TABLE IF EXISTS transaction_report, account, annual_statement;`

## 🛠️ Configuración y Ejecución

### 1. Preparar la Base de Datos
PostgreSQL en `localhost:5432` con una base `xyzbank`. Credenciales por defecto en `application.properties`:
*   **Usuario:** `postgres`
*   **Password:** `c1j9r9f3!psql`

### 2. Archivos de Entrada (CSV)
Los archivos oficiales de `fin_legacy_data/data/semana_3` están en `src/main/resources/data/`. Para procesar otros archivos (por ejemplo otra semana):
```bash
java -jar target/demo-0.0.1-SNAPSHOT.jar \
  --xyz.batch.input.transactions=file:/ruta/movimientos_financieros_diarios.csv \
  --xyz.batch.input.interests=file:/ruta/intereses_trimestrales.csv \
  --xyz.batch.input.statements=file:/ruta/estados_financieros_anuales.csv
```

### 3. Propiedades (`application.properties`)
```properties
spring.batch.job.name=masterJob
xyz.batch.chunk-size=100       # registros por commit
xyz.batch.step-threads=2       # hilos por step (1 = secuencial)
xyz.batch.skip-limit=1000      # rechazos tolerados por step
xyz.batch.retry-limit=3        # reintentos ante errores transitorios de BD
xyz.batch.reset-tables=true    # recarga completa e idempotente
xyz.batch.min-age=18
xyz.batch.max-age=99
```

### 4. Ejecutar y probar
```bash
./mvnw package -DskipTests && java -jar target/demo-0.0.1-SNAPSHOT.jar
./mvnw test   # unitarios de procesadores/política + integración (requiere PostgreSQL)
```
