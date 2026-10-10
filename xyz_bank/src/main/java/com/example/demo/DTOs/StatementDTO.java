package com.example.demo.DTOs;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * Objeto de transferencia de datos (DTO) que encapsula un movimiento validado del estado de cuenta anual
 * Se apoya en Lombok @Data para la generación automática de métodos de acceso y utilidad
 */
@Data
public class StatementDTO {
    
    /** Identificador de la cuenta asociada al estado de cuenta*/
    private Integer accountId;
    
    /** Fecha en la que se registra el movimiento, normalizada a ISO */
    private LocalDate statementDate;
    
    /** Tipo de movimiento normalizado: DEPOSITO, RETIRO, COMPRA o PAGO */
    private String transaction;
    
    /** Monto monetario de la operación en formato BigDecimal, siempre positivo */
    private BigDecimal amount;
    
    /** Descripción detallada del movimiento, en mayúsculas */
    private String description;
}
