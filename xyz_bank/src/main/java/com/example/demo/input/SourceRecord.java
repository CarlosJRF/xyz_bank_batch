package com.example.demo.input;

/**
 * Registro crudo leído de un CSV legacy. Conserva el número de línea y el texto original
 * para que cualquier rechazo o corrección pueda trazarse hasta la fila del archivo de origen.
 */
public interface SourceRecord {

    long lineNumber();

    String rawLine();
}
