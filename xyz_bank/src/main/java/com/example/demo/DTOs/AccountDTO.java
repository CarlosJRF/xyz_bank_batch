package com.example.demo.DTOs;

import java.math.BigDecimal;
import lombok.Data;

/**
 * Objeto de transferencia de datos (DTO) que representa una cuenta validada con su interés aplicado.
 * Utiliza la anotación @Data de Lombok para generar automáticamente los métodos getter, setter, 
 * toString, equals y hashCode.
 */
@Data
public class AccountDTO {

    /** Identificador de la cuenta en el archivo de origen. */
    private Integer accountId;
    
    /** Nombre del cliente titular de la cuenta. */
    private String clientName;
    
    /** Saldo luego de aplicar el interés, utilizando BigDecimal para garantizar precisión financiera. */
    private BigDecimal balance;

    /** Interés aplicado sobre el saldo original. */
    private BigDecimal interest;
    
    /** Edad del cliente, validada dentro del rango aceptable. */
    private Integer age;
    
    /** Tipo de cuenta elegible: AHORRO, PRESTAMO o HIPOTECA. */
    private String accountType;

}
