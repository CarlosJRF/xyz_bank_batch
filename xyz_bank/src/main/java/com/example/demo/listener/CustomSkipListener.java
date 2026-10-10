package com.example.demo.listener;

import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

/**
 * Componente que intercepta y gestiona los eventos de salto (skip) en Spring Batch
 * Permite registrar de forma estructurada los errores que ocurren sin interrumpir el flujo general del lote
 */
@Component
public class CustomSkipListener implements SkipListener<Object, Object> {

    /**
     * Registra los errores interceptados en la fase de lectura de datos
     * 
     * @param t Excepción capturada, como un formato incorrecto o falta de columnas en el CSV
     */
    @Override
    public void onSkipInRead(Throwable t) {
        // Se ejecuta si el error ocurre al leer el CSV (ej. formato incorrecto, faltan columnas)
        System.err.println("⏭️ SKIP (Lectura) - Fila con formato inválido detectada: " + t.getMessage()); 
    }

    /**
     * Registra los fallos ocurridos durante la aplicación de reglas de negocio o transformaciones
     * 
     * @param item El registro específico que causó el fallo
     * @param t La excepción capturada dentro de los Processors
     */
    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        // Se ejecuta si el error ocurre dentro de tus Processors[cite: 4]
        System.err.println("⏭️ SKIP (Procesamiento) - Error al procesar el registro [" + item + "]: " + t.getMessage()); 
    }

    /**
     * Registra los errores que emite la base de datos al intentar persistir los datos procesados
     * 
     * @param item El registro que la base de datos rechazó
     * @param t La excepción de persistencia, como una violación de llave única
     */
    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        // Se ejecuta si la base de datos rechaza el registro (ej. violación de llave única)
        System.err.println("⏭️ SKIP (Escritura) - Error al insertar en BD el registro [" + item + "]: " + t.getMessage()); 
    }
}