package com.example.demo.input;

/** Fila cruda de estados_financieros_anuales.csv (cuenta_id,fecha,transaccion,monto,descripcion), sin convertir. */
public record StatementInput(long lineNumber, String rawLine,
                             String accountId, String date, String transaction, String amount,
                             String description) implements SourceRecord {
}
