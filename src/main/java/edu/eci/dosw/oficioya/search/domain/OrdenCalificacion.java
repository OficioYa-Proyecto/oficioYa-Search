package edu.eci.dosw.oficioya.search.domain;

/**
 * Orden de los resultados: por calificación promedio (de mayor a menor o de menor a mayor) o por qué tan completo
 * está el perfil de trabajo (los perfiles más completos primero; a igual perfil, mayor calificación).
 */
public enum OrdenCalificacion {
    MAYOR_A_MENOR,
    MENOR_A_MAYOR,
    PERFIL_COMPLETO
}
