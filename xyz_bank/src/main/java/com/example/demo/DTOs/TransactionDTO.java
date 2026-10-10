package com.example.demo.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * Objeto de transferencia de datos (DTO) que modela una transacción ya validada,
 * lista para ser persistida en la tabla transaction_report.
 * Anotado con @Data de Lombok para automatizar getters, setters y otros métodos estándar
 */
@Data
public class TransactionDTO {

    /** Identificador de la transacción en el archivo de origen (único) */
    private Integer id;
    
    /** Fecha en que se llevó a cabo la transacción, normalizada a ISO */
    private LocalDate transactionDate;
    
    /** Monto de la transacción, siempre positivo */
    private BigDecimal amount;
    
    /** Categoría de la transacción: DEBITO o CREDITO */
    private String type;

}
