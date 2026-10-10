package com.example.demo.DTOs;

import java.time.LocalDate;
import lombok.Data;
/**
 * Objeto de transferencia de datos (DTO) que modela las transacciones financieras 
 * procesadas por el sistema batch
 * Anotado con @Data de Lombok para automatizar getters, setters y otros métodos estándar
 */
@Data
public class TransactionDTO {

    /** Identificador único de la transacción */
    private int id;
    
    /** Fecha en que se llevó a cabo la transacción */
    private LocalDate transactionDate;
    
    /** Valor entero asociado al monto de la transacción */
    private int amount;
    
    /** Categoría o tipo de la transacción */
    private String type;

}
