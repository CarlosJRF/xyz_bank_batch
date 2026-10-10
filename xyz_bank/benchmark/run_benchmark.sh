#!/bin/bash
# Ejecuta el masterJob con distintas combinaciones de hilos por step y tamaño de chunk,
# en una JVM nueva por ejecución, y agrega los tiempos a un CSV.
#
# Uso (desde xyz_bank/, con PostgreSQL arriba y el jar construido con ./mvnw package):
#   benchmark/run_benchmark.sh <salida.csv> <etiqueta_dataset> <repeticiones> [args extra de Spring...]
# Ejemplos:
#   benchmark/run_benchmark.sh benchmark/results.csv oficial 5
#   benchmark/run_benchmark.sh benchmark/results.csv x10 3 \
#       --xyz.batch.input.transactions=file:/tmp/x10/movimientos_financieros_diarios.csv \
#       --xyz.batch.input.interests=file:/tmp/x10/intereses_trimestrales.csv \
#       --xyz.batch.input.statements=file:/tmp/x10/estados_financieros_anuales.csv \
#       --xyz.batch.skip-limit=100000
set -euo pipefail
OUT=$1; DATASET=$2; REPS=$3; shift 3
JAR=${JAR:-target/demo-0.0.1-SNAPSHOT.jar}
THREADS=${THREADS:-"1 2 4 8"}
CHUNKS=${CHUNKS:-"5 10 50 100 250"}

[ -f "$OUT" ] || echo "dataset,threads,chunk,rep,status,totalMs,txMs,intMs,stMs" > "$OUT"
for threads in $THREADS; do
  for chunk in $CHUNKS; do
    for rep in $(seq 1 "$REPS"); do
      log=$(java -jar "$JAR" --xyz.batch.chunk-size="$chunk" --xyz.batch.step-threads="$threads" "$@" 2>&1 \
            | grep -E 'BENCHMARK|RESUMEN')
      field() { echo "$log" | grep "$1" | sed -E "s/.*$2=([A-Z0-9]+).*/\1/"; }
      echo "$DATASET,$threads,$chunk,$rep,$(field BENCHMARK status),$(field BENCHMARK totalMs),$(field 'RESUMEN transactionStep' duracionMs),$(field 'RESUMEN interestStep' duracionMs),$(field 'RESUMEN statementStep' duracionMs)" | tee -a "$OUT"
    done
  done
done
