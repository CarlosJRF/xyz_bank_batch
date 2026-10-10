package com.example.demo.validation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detecta registros duplicados de forma segura entre hilos dentro de una ejecución de step.
 * La primera aparición de una clave se acepta y las siguientes se marcan como duplicadas.
 * Se compara también la identidad del registro de origen: si Spring Batch reprocesa el mismo
 * ítem (por ejemplo tras un rollback de escritura) no se confunde con un duplicado.
 */
public class DuplicateGuard {

    private final Map<Object, Object> seen = new ConcurrentHashMap<>();

    public boolean isDuplicate(Object key, Object source) {
        Object first = seen.putIfAbsent(key, source);
        return first != null && first != source;
    }

    public void reset() {
        seen.clear();
    }
}
