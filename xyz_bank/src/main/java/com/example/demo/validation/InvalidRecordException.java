package com.example.demo.validation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Excepción de negocio lanzada por los procesadores cuando un registro no supera las validaciones.
 * Es la única excepción de procesamiento que la política de salto considera omitible, de modo que
 * los rechazos quedan contabilizados (skip count) y trazados en la tabla data_quality_log,
 * en lugar de descartarse silenciosamente con un {@code return null}.
 */
public class InvalidRecordException extends RuntimeException {

    private final List<ErrorCode> errorCodes;

    public InvalidRecordException(List<ErrorCode> errorCodes) {
        super(errorCodes.stream().map(ErrorCode::name).collect(Collectors.joining(",")));
        this.errorCodes = List.copyOf(errorCodes);
    }

    /** Todos los errores encontrados en el registro, en el orden en que se validaron. */
    public List<ErrorCode> getErrorCodes() {
        return errorCodes;
    }

    /** Primer error encontrado; se usa como causa principal del rechazo. */
    public ErrorCode getPrimaryCode() {
        return errorCodes.get(0);
    }
}
