package com.example.demo.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * Objeto de transferencia de datos (DTO) que encapsula los detalles de los movimientos 
 * o estados de cuenta anuales
 * Se apoya en Lombok @Data para la generación automática de métodos de acceso y utilidad
 */
@Data
public class StatementDTO {
    
    /** Identificador de la cuenta asociada al estado de cuenta*/
    private int accountId;
    
    /** Fecha en la que se registra el movimiento, tipada como LocalDate */
    private LocalDate statementDate;
    
    /** Identificador o etiqueta de la transacción */
    private String transaction;
    
    /** Monto monetario de la operación en formato BigDecimal */
    private BigDecimal amount;
    
    /** Descripción detallada del movimiento */
    private String description;
}
