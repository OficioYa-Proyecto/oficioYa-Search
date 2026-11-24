package edu.eci.dosw.oficioya.search.dto;

import java.util.UUID;

/**
 * Categoría del catálogo de Worker a la que pertenece un oficio (por ejemplo "Plomería y gas").
 */
public record CategoriaDTO(UUID id, String nombre) {
}
