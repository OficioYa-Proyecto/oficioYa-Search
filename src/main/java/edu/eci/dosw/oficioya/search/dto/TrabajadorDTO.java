package edu.eci.dosw.oficioya.search.dto;

import java.util.UUID;

import java.math.BigDecimal;
import java.util.List;

/**
 * Trabajador ACTIVO tal como lo entrega Worker a través del Orchestrator.
 * No incluye correo ni teléfono: si Worker los enviara, se ignoran.
 * {@code oficiosSecundarios} trae el nombre y la tarifa de cada oficio secundario, para mostrar en la tarjeta
 * el oficio que se buscó y no siempre el principal.
 */
@SuppressWarnings("java:S107")
public record TrabajadorDTO(UUID id, String nombreCompleto, String fotoPerfilUrl, UUID oficioPrincipalId,
                            String oficioPrincipal, List<UUID> oficiosSecundariosIds, List<Long> barrioIds,
                            String zona, BigDecimal tarifaAproximada, Boolean disponibleAhora,
                            List<DisponibilidadDTO> disponibilidad, Integer trabajosCompletados,
                            List<OficioOfrecidoDTO> oficiosSecundarios) {

    /** Trabajador sin el detalle de sus oficios secundarios: la tarjeta muestra siempre el principal. */
    public TrabajadorDTO(UUID id, String nombreCompleto, String fotoPerfilUrl, UUID oficioPrincipalId,
                         String oficioPrincipal, List<UUID> oficiosSecundariosIds, List<Long> barrioIds,
                         String zona, BigDecimal tarifaAproximada, Boolean disponibleAhora,
                         List<DisponibilidadDTO> disponibilidad, Integer trabajosCompletados) {
        this(id, nombreCompleto, fotoPerfilUrl, oficioPrincipalId, oficioPrincipal, oficiosSecundariosIds, barrioIds,
                zona, tarifaAproximada, disponibleAhora, disponibilidad, trabajosCompletados, List.of());
    }

    /**
     * Indica si el trabajador ofrece el oficio como principal o como secundario.
     */
    public boolean ofrece(UUID oficioId) {
        if (oficioId == null) {
            return false;
        }
        return oficioId.equals(oficioPrincipalId)
                || (oficiosSecundariosIds != null && oficiosSecundariosIds.contains(oficioId));
    }

    /**
     * Nombre y tarifa con los que se muestra al trabajador cuando se busca {@code oficioId}: si es uno de sus
     * oficios secundarios, los de ese oficio; en otro caso, los del oficio principal.
     */
    public OficioOfrecidoDTO oficioParaMostrar(UUID oficioId) {
        if (oficioId != null && !oficioId.equals(oficioPrincipalId) && oficiosSecundarios != null) {
            for (OficioOfrecidoDTO secundario : oficiosSecundarios) {
                if (oficioId.equals(secundario.id())) {
                    return secundario;
                }
            }
        }
        return new OficioOfrecidoDTO(oficioPrincipalId, oficioPrincipal, tarifaAproximada);
    }
}
