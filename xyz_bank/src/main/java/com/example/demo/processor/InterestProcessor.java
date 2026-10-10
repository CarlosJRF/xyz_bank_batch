package com.example.demo.processor;

import java.math.BigDecimal;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;
import com.example.demo.DTOs.AccountDTO;

/**
 * Procesador de lógica de negocio para el cálculo y aplicación de tasas de interés.
 * Funciona como el componente de transformación en el pipeline ETL de Spring Batch, 
 * operando de forma individual sobre cada cuenta antes de que sea enviada al ItemWriter.
 */
@Component
public class InterestProcessor implements ItemProcessor<AccountDTO, AccountDTO>{

    //Definiendo las tasas de interes

    private static final BigDecimal SAVINGS_RATE = new BigDecimal("0.005");// 0.5%
    private static final BigDecimal LOAN_RATE = new BigDecimal("0.02"); //2.0%
    private static final BigDecimal MORTAGE_RATE = new BigDecimal("0.03");//3.0% para Hipoteca, no esta en el instructivo pero si el csv

    @Override
    public AccountDTO process(AccountDTO account) throws Exception {
        if (account.getBalance() == null) {
            return null; // Descarta la cuenta si no tiene saldo válido
        }

        BigDecimal currentBalance = account.getBalance();

        //Asignacion de la tasa utilizando switch

        BigDecimal interestRate = switch(account.getAccountType().toUpperCase()){
            case "AHORRO" -> SAVINGS_RATE;
            case "PRESTAMO" -> LOAN_RATE;
            case "HIPOTECA" -> MORTAGE_RATE;
            default -> BigDecimal.ZERO; //Otra cuenta por defecto
        };

        //Calculo de interes en caso de otra cuenta
        if(interestRate.compareTo(BigDecimal.ZERO) > 0){
            BigDecimal interest = currentBalance.multiply(interestRate);
            account.setBalance(currentBalance.add(interest));
        }

        return account;

    }

    
    
}
