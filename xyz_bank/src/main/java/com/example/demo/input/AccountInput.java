package com.example.demo.input;

/** Fila cruda de intereses_trimestrales.csv (cuenta_id,nombre,saldo,edad,tipo), sin convertir. */
public record AccountInput(long lineNumber, String rawLine,
                           String accountId, String clientName, String balance, String age,
                           String accountType) implements SourceRecord {
}
