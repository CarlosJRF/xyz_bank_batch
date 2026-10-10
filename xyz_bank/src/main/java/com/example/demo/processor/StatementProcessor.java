package com.example.demo.processor;

import java.util.List;
import java.util.Set;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.example.demo.DTOs.StatementDTO;
import com.example.demo.input.StatementInput;
import com.example.demo.validation.DataQualityLog;
import com.example.demo.validation.DuplicateGuard;
import com.example.demo.validation.ErrorCode;
import com.example.demo.validation.InvalidRecordException;
import com.example.demo.validation.RecordValidator;

/**
 * Procesador de lógica de negocio para los estados de cuenta anuales.
 * Cubre explícitamente los errores del dataset oficial (estados_financieros_anuales.csv):
 * <ul>
 *   <li>Cuenta inválida: INVALID_ID</li>
 *   <li>Fecha vacía o inexistente: MISSING_DATE / INVALID_DATE; formato no estándar: se normaliza</li>
 *   <li>Tipo de movimiento desconocido: INVALID_TRANSACTION_TYPE; "depósito" se normaliza a DEPOSITO</li>
 *   <li>Monto vacío, no numérico, negativo o cero: MISSING_AMOUNT / INVALID_AMOUNT / NEGATIVE_AMOUNT / ZERO_AMOUNT</li>
 *   <li>Descripción vacía: se reemplaza por "SIN DESCRIPCION" (MISSING_DESCRIPTION)</li>
 *   <li>Movimiento idéntico a otro ya aceptado: DUPLICATE_RECORD</li>
 * </ul>
 */
@Component
public class StatementProcessor implements ItemProcessor<StatementInput, StatementDTO>, StepExecutionListener {

    public static final String STEP_NAME = "statementStep";

    private static final Set<String> VALID_TRANSACTIONS = Set.of("DEPOSITO", "RETIRO", "COMPRA", "PAGO");
    private static final String NO_DESCRIPTION = "SIN DESCRIPCION";

    private final DataQualityLog dataQualityLog;
    private final DuplicateGuard duplicateGuard = new DuplicateGuard();

    public StatementProcessor(DataQualityLog dataQualityLog) {
        this.dataQualityLog = dataQualityLog;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        duplicateGuard.reset();
    }

    @Override
    public StatementDTO process(StatementInput item) {
        RecordValidator validator = new RecordValidator();

        StatementDTO statement = new StatementDTO();
        statement.setAccountId(validator.positiveId(item.accountId()));
        statement.setStatementDate(validator.date(item.date()));

        String transaction = RecordValidator.normalizeCode(item.transaction());
        if (!VALID_TRANSACTIONS.contains(transaction)) {
            validator.reject(ErrorCode.INVALID_TRANSACTION_TYPE);
        } else if (!transaction.equals(item.transaction().toUpperCase())) {
            validator.correct(ErrorCode.TRANSACTION_TYPE_NORMALIZED);
        }
        statement.setTransaction(transaction);

        statement.setAmount(validator.positiveAmount(item.amount()));

        if (item.description().isEmpty()) {
            validator.correct(ErrorCode.MISSING_DESCRIPTION);
            statement.setDescription(NO_DESCRIPTION);
        } else {
            statement.setDescription(item.description().toUpperCase());
        }

        validator.throwIfInvalid();

        List<Object> key = List.of(statement.getAccountId(), statement.getStatementDate(), transaction,
                statement.getAmount(), statement.getDescription());
        if (duplicateGuard.isDuplicate(key, item)) {
            throw new InvalidRecordException(List.of(ErrorCode.DUPLICATE_RECORD));
        }

        dataQualityLog.corrected(STEP_NAME, item.lineNumber(), validator.getCorrections(), item.rawLine());
        return statement;
    }
}
