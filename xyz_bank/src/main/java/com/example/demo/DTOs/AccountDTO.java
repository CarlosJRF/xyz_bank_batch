package com.example.demo.DTOs;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Objeto de transferencia de datos (DTO) que representa la información de una cuenta de cliente.
 * Utiliza la anotación @Data de Lombok para generar automáticamente los métodos getter, setter, 
 * toString, equals y hashCode.
 */
@Data
public class AccountDTO {

    /** Identificador único de la cuenta. */
    private int id;
    
    /** Nombre del cliente titular de la cuenta. */
    private String clientName;
    
    /** Saldo actual de la cuenta, utilizando BigDecimal para garantizar precisión financiera. */
    private BigDecimal balance;
    
    /** Edad del cliente almacenada como texto. */
    private String age;
    
    /** Clasificación o tipo de la cuenta. */
    private String accountType;

}
