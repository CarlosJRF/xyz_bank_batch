package com.example.demo.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.example.demo.DTOs.AccountDTO;
import com.example.demo.config.XyzBatchProperties;
import com.example.demo.input.AccountInput;
import com.example.demo.validation.DuplicateGuard;
import com.example.demo.validation.ErrorCode;
import com.example.demo.validation.InvalidRecordException;
import com.example.demo.validation.RecordValidator;

/**
 * Procesador de lógica de negocio para la validación de cuentas y el cálculo de intereses trimestrales.
 * Cubre explícitamente los errores del dataset oficial (intereses_trimestrales.csv):
 * <ul>
 *   <li>Cuenta vacía, no numérica o no positiva: INVALID_ID</li>
 *   <li>Titular vacío o "Unknown": INVALID_CLIENT_NAME</li>
 *   <li>Saldo vacío, no numérico o negativo: MISSING_BALANCE / INVALID_BALANCE / NEGATIVE_BALANCE</li>
 *   <li>Edad vacía, no numérica o fuera de [minAge, maxAge] (ej. 100, 150): MISSING_AGE / INVALID_AGE / AGE_OUT_OF_RANGE</li>
 *   <li>Tipo de cuenta no elegible (ej. -1, unknown): INELIGIBLE_ACCOUNT_TYPE</li>
 *   <li>Fila idéntica a otra ya aceptada: DUPLICATE_RECORD</li>
 * </ul>
 * Solo las cuentas válidas reciben el interés según su tipo y se persisten.
 */
@Component
public class InterestProcessor implements ItemProcessor<AccountInput, AccountDTO>, StepExecutionListener {

    public static final String STEP_NAME = "interestStep";

    /** Tasas por tipo de cuenta. HIPOTECA no está en el instructivo pero sí en el dataset. */
    private static final Map<String, BigDecimal> RATES = Map.of(
            "AHORRO", new BigDecimal("0.005"),   // 0.5%
            "PRESTAMO", new BigDecimal("0.02"),  // 2.0%
            "HIPOTECA", new BigDecimal("0.03")); // 3.0%

    private final int minAge;
    private final int maxAge;
    private final DuplicateGuard duplicateGuard = new DuplicateGuard();

    public InterestProcessor(XyzBatchProperties properties) {
        this.minAge = properties.minAge();
        this.maxAge = properties.maxAge();
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        duplicateGuard.reset();
    }

    @Override
    public AccountDTO process(AccountInput item) {
        RecordValidator validator = new RecordValidator();

        AccountDTO account = new AccountDTO();
        account.setAccountId(validator.positiveId(item.accountId()));

        String clientName = item.clientName();
        if (clientName.isEmpty() || clientName.equalsIgnoreCase("unknown")) {
            validator.reject(ErrorCode.INVALID_CLIENT_NAME);
        }
        account.setClientName(clientName);

        BigDecimal balance = validator.balance(item.balance());
        account.setAge(validator.age(item.age(), minAge, maxAge));

        String accountType = RecordValidator.normalizeCode(item.accountType());
        BigDecimal rate = RATES.get(accountType);
        if (rate == null) {
            validator.reject(ErrorCode.INELIGIBLE_ACCOUNT_TYPE);
        }
        account.setAccountType(accountType);

        validator.throwIfInvalid();

        List<Object> key = List.of(account.getAccountId(), clientName.toUpperCase(), balance, account.getAge(), accountType);
        if (duplicateGuard.isDuplicate(key, item)) {
            throw new InvalidRecordException(List.of(ErrorCode.DUPLICATE_RECORD));
        }

        BigDecimal interest = balance.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
        account.setInterest(interest);
        account.setBalance(balance.add(interest));
        return account;
    }

}
