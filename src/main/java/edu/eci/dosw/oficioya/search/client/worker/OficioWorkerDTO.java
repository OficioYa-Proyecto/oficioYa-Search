package edu.eci.dosw.oficioya.search.client.worker;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import edu.eci.dosw.oficioya.search.dto.CategoriaDTO;
import edu.eci.dosw.oficioya.search.dto.OficioDTO;

/** Oficio del catálogo de Worker (Squad 3), en {@code GET /api/v1/trades}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OficioWorkerDTO(UUID id, String name, String description, boolean active, CategoriaWorkerDTO category) {

    /** Categoría del oficio en Worker. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoriaWorkerDTO(UUID id, String name) {
    }

    public OficioDTO aOficio() {
        CategoriaDTO categoria = category == null || category.id() == null
                ? null
                : new CategoriaDTO(category.id(), category.name());
        return new OficioDTO(id, name, description, categoria);
    }
}
