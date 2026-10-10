package com.example.demo.validation;

/**
 * Catálogo explícito de los problemas de calidad presentes en el dataset oficial
 * (fin_legacy_data). Cada código se usa como rechazo (el registro no se persiste)
 * o como corrección (el registro se persiste normalizado y queda trazado).
 */
public enum ErrorCode {

    // --- Rechazos comunes ---
    INVALID_ID("Identificador vacío, no numérico o no positivo"),
    MISSING_DATE("Fecha vacía"),
    INVALID_DATE("Fecha inexistente o con formato no reconocido (ej. 2024-13-01)"),
    MISSING_AMOUNT("Monto vacío"),
    INVALID_AMOUNT("Monto no numérico"),
    NEGATIVE_AMOUNT("Monto negativo"),
    ZERO_AMOUNT("Monto en cero"),
    INVALID_TRANSACTION_TYPE("Tipo de transacción desconocido (ej. 'invalido', 'desconocido')"),
    DUPLICATE_RECORD("Registro duplicado de otro ya aceptado"),
    STRUCTURAL_ERROR("Línea con cantidad de columnas incorrecta"),
    DB_CONSTRAINT_VIOLATION("Rechazado por una restricción de la base de datos"),

    // --- Rechazos específicos de intereses ---
    INVALID_CLIENT_NAME("Nombre de cliente vacío o 'Unknown'"),
    MISSING_BALANCE("Saldo vacío"),
    INVALID_BALANCE("Saldo no numérico"),
    NEGATIVE_BALANCE("Saldo negativo"),
    MISSING_AGE("Edad vacía"),
    INVALID_AGE("Edad no numérica"),
    AGE_OUT_OF_RANGE("Edad fuera del rango aceptable"),
    INELIGIBLE_ACCOUNT_TYPE("Tipo de cuenta no elegible para intereses (ej. '-1', 'unknown')"),

    // --- Correcciones (el registro se acepta normalizado) ---
    DATE_FORMAT_NORMALIZED("Fecha en formato no estándar normalizada a ISO yyyy-MM-dd"),
    TRANSACTION_TYPE_NORMALIZED("Tipo de transacción con acento/mayúsculas normalizado"),
    MISSING_DESCRIPTION("Descripción vacía reemplazada por 'SIN DESCRIPCION'");

    private final String description;

    ErrorCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
