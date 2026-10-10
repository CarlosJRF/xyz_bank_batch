package com.example.demo.validation;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * Acumula los errores y correcciones de un registro mientras se validan sus campos.
 * Se crea una instancia por registro (no es compartida entre hilos).
 * Todos los errores del registro se reportan juntos para que el log de calidad muestre
 * el diagnóstico completo, no solo el primer problema.
 */
public class RecordValidator {

    private static final DateTimeFormatter ISO_DATE = strict("uuuu-MM-dd");

    /** Formatos no estándar observados en el dataset oficial; se asume día antes que mes (locale es-CL). */
    private static final List<DateTimeFormatter> LEGACY_DATE_FORMATS = List.of(
            strict("uuuu/MM/dd"),
            strict("dd/MM/uuuu"),
            strict("dd-MM-uuuu"));

    private final List<ErrorCode> errors = new ArrayList<>();
    private final List<ErrorCode> corrections = new ArrayList<>();

    private static DateTimeFormatter strict(String pattern) {
        // STRICT rechaza fechas imposibles como 2024-13-01 o 2024-02-30 en lugar de ajustarlas
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }

    public void reject(ErrorCode code) {
        errors.add(code);
    }

    public void correct(ErrorCode code) {
        corrections.add(code);
    }

    /** Identificador entero y positivo; registra {@link ErrorCode#INVALID_ID} si no lo es. */
    public Integer positiveId(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // vacío o no numérico: se reporta abajo
        }
        reject(ErrorCode.INVALID_ID);
        return null;
    }

    /** Fecha en ISO o en uno de los formatos legacy conocidos (normalizada y trazada como corrección). */
    public LocalDate date(String raw) {
        if (raw.isEmpty()) {
            reject(ErrorCode.MISSING_DATE);
            return null;
        }
        try {
            return LocalDate.parse(raw, ISO_DATE);
        } catch (DateTimeParseException e) {
            // se intenta con los formatos legacy
        }
        for (DateTimeFormatter format : LEGACY_DATE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(raw, format);
                correct(ErrorCode.DATE_FORMAT_NORMALIZED);
                return date;
            } catch (DateTimeParseException e) {
                // siguiente formato
            }
        }
        reject(ErrorCode.INVALID_DATE);
        return null;
    }

    /** Monto de una transacción: obligatorio, numérico y estrictamente positivo. */
    public BigDecimal positiveAmount(String raw) {
        BigDecimal value = decimal(raw, ErrorCode.MISSING_AMOUNT, ErrorCode.INVALID_AMOUNT);
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            reject(ErrorCode.NEGATIVE_AMOUNT);
            return null;
        }
        if (value.signum() == 0) {
            reject(ErrorCode.ZERO_AMOUNT);
            return null;
        }
        return value;
    }

    /** Saldo de una cuenta: obligatorio, numérico y no negativo (saldo cero es válido, solo no genera interés). */
    public BigDecimal balance(String raw) {
        BigDecimal value = decimal(raw, ErrorCode.MISSING_BALANCE, ErrorCode.INVALID_BALANCE);
        if (value != null && value.signum() < 0) {
            reject(ErrorCode.NEGATIVE_BALANCE);
            return null;
        }
        return value;
    }

    /** Edad del titular: obligatoria, entera y dentro de [minAge, maxAge]. */
    public Integer age(String raw, int minAge, int maxAge) {
        if (raw.isEmpty()) {
            reject(ErrorCode.MISSING_AGE);
            return null;
        }
        try {
            int age = Integer.parseInt(raw);
            if (age < minAge || age > maxAge) {
                reject(ErrorCode.AGE_OUT_OF_RANGE);
                return null;
            }
            return age;
        } catch (NumberFormatException e) {
            reject(ErrorCode.INVALID_AGE);
            return null;
        }
    }

    private BigDecimal decimal(String raw, ErrorCode missing, ErrorCode invalid) {
        if (raw.isEmpty()) {
            reject(missing);
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            reject(invalid);
            return null;
        }
    }

    /** Pasa a mayúsculas y elimina acentos: "depósito" -> "DEPOSITO". */
    public static String normalizeCode(String raw) {
        String withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toUpperCase();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** Lanza {@link InvalidRecordException} si se registró al menos un error. */
    public void throwIfInvalid() {
        if (hasErrors()) {
            throw new InvalidRecordException(errors);
        }
    }

    public List<ErrorCode> getCorrections() {
        return corrections;
    }
}
