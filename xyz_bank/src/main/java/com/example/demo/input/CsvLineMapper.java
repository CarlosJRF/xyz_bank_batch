package com.example.demo.input;

import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;

/**
 * LineMapper que separa la línea por comas, recorta espacios de cada campo y construye un
 * {@link SourceRecord} crudo. No convierte tipos: toda conversión y validación ocurre en los
 * procesadores, donde cada problema recibe un {@link com.example.demo.validation.ErrorCode} explícito.
 * Una línea con distinta cantidad de columnas lanza IncorrectTokenCountException, que el reader
 * envuelve en FlatFileParseException (error estructural omitible).
 */
public class CsvLineMapper<T extends SourceRecord> implements LineMapper<T> {

    @FunctionalInterface
    public interface RecordFactory<T> {
        T create(long lineNumber, String rawLine, String[] fields);
    }

    private final DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
    private final RecordFactory<T> factory;

    public CsvLineMapper(RecordFactory<T> factory, String... columnNames) {
        this.factory = factory;
        this.tokenizer.setNames(columnNames);
        this.tokenizer.setStrict(true);
    }

    @Override
    public T mapLine(String line, int lineNumber) {
        String[] fields = tokenizer.tokenize(line).getValues();
        for (int i = 0; i < fields.length; i++) {
            fields[i] = fields[i] == null ? "" : fields[i].strip();
        }
        return factory.create(lineNumber, line, fields);
    }
}
