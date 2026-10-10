package com.example.demo.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;
import com.example.demo.DTOs.StatementDTO;

/**
 * Procesador de lógica de negocio para los estados de cuenta anuales.
 * Actúa como un filtro y transformador intermedio en el pipeline de Spring Batch,
 * garantizando la calidad de los datos antes de su persistencia en la base de datos.
 */
@Component
public class StatementProcessor implements ItemProcessor<StatementDTO, StatementDTO> {
    @Override
    public StatementDTO process(StatementDTO statement) throws Exception {
        // Garantizar la consistencia: descartar filas sin monto
        if (statement.getAmount() == null) {
            return null; // Spring Batch ignora el registro automáticamente
        }
        
        if (statement.getDescription() != null) {
            statement.setDescription(statement.getDescription().toUpperCase());
        } else {
            statement.setDescription("SIN DESCRIPCIÓN");
        }
        return statement;
    }
}