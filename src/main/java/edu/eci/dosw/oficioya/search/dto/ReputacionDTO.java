package edu.eci.dosw.oficioya.search.dto;

import java.util.UUID;

/**
 * Reputación de un trabajador tal como la entrega Reputation a través del Orchestrator.
 * {@code trabajosCompletados} son los servicios cumplidos que Service le avisó a Reputation.
 */
public record ReputacionDTO(UUID trabajadorId, Double reputacion, Integer totalResenas, Integer trabajosCompletados) {

    public ReputacionDTO(UUID trabajadorId, Double reputacion, Integer totalResenas) {
        this(trabajadorId, reputacion, totalResenas, null);
    }
}
