package com.example.demo.input;

/** Fila cruda de movimientos_financieros_diarios.csv (id,fecha,monto,tipo), sin convertir. */
public record TransactionInput(long lineNumber, String rawLine,
                               String id, String date, String amount, String type) implements SourceRecord {
}
