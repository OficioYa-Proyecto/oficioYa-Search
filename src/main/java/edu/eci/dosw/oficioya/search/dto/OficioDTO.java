package edu.eci.dosw.oficioya.search.dto;

import java.util.UUID;

/**
 * Oficio del catálogo de Worker, con la categoría a la que pertenece ({@code null} si Worker no la entrega).
 */
public record OficioDTO(UUID id, String nombre, String descripcion, CategoriaDTO categoria) {

    public OficioDTO(UUID id, String nombre, String descripcion) {
        this(id, nombre, descripcion, null);
    }
}
