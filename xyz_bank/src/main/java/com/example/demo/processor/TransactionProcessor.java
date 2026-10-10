package com.example.demo.processor;

import java.util.List;
import java.util.Set;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.example.demo.DTOs.TransactionDTO;
import com.example.demo.input.TransactionInput;
import com.example.demo.validation.DataQualityLog;
import com.example.demo.validation.DuplicateGuard;
import com.example.demo.validation.ErrorCode;
import com.example.demo.validation.InvalidRecordException;
import com.example.demo.validation.RecordValidator;


/**
 * Procesador de lógica de negocio encargado de validar y convertir las transacciones diarias.
 * Cubre explícitamente los errores del dataset oficial (movimientos_financieros_diarios.csv):
 * <ul>
 *   <li>Identificador inválido o repetido: INVALID_ID / DUPLICATE_RECORD</li>
 *   <li>Fecha vacía o inexistente (ej. 2024-13-01): MISSING_DATE / INVALID_DATE</li>
 *   <li>Fecha en formato no estándar (yyyy/MM/dd, dd/MM/yyyy, dd-MM-yyyy): se normaliza (DATE_FORMAT_NORMALIZED)</li>
 *   <li>Monto vacío, no numérico, negativo o cero: MISSING_AMOUNT / INVALID_AMOUNT / NEGATIVE_AMOUNT / ZERO_AMOUNT</li>
 *   <li>Tipo distinto de debito/credito (ej. invalido, desconocido): INVALID_TRANSACTION_TYPE</li>
 * </ul>
 * Un registro inválido lanza {@link InvalidRecordException}: la política de salto lo omite y el
 * SkipListener lo deja trazado en data_quality_log.
 */
@Component
public class TransactionProcessor implements ItemProcessor<TransactionInput, TransactionDTO>, StepExecutionListener {

    public static final String STEP_NAME = "transactionStep";

    private static final Set<String> VALID_TYPES = Set.of("DEBITO", "CREDITO");

    private final DataQualityLog dataQualityLog;
    private final DuplicateGuard duplicateGuard = new DuplicateGuard();

    public TransactionProcessor(DataQualityLog dataQualityLog) {
        this.dataQualityLog = dataQualityLog;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        duplicateGuard.reset();
    }

    @Override
    public TransactionDTO process(TransactionInput item) {
        RecordValidator validator = new RecordValidator();

        TransactionDTO transaction = new TransactionDTO();
        transaction.setId(validator.positiveId(item.id()));
        transaction.setTransactionDate(validator.date(item.date()));
        transaction.setAmount(validator.positiveAmount(item.amount()));

        String type = RecordValidator.normalizeCode(item.type());
        if (!VALID_TYPES.contains(type)) {
            validator.reject(ErrorCode.INVALID_TRANSACTION_TYPE);
        }
        transaction.setType(type);

        validator.throwIfInvalid();

        if (duplicateGuard.isDuplicate(transaction.getId(), item)) {
            throw new InvalidRecordException(List.of(ErrorCode.DUPLICATE_RECORD));
        }

        dataQualityLog.corrected(STEP_NAME, item.lineNumber(), validator.getCorrections(), item.rawLine());
        return transaction;
    }

}
