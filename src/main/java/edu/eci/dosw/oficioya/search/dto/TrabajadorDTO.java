package edu.eci.dosw.oficioya.search.dto;

import java.util.UUID;

import java.math.BigDecimal;
import java.util.List;

/**
 * Trabajador ACTIVO tal como lo entrega Worker a través del Orchestrator.
 * No incluye correo ni teléfono: si Worker los enviara, se ignoran.
 * {@code oficiosSecundarios} trae el nombre y la tarifa de cada oficio secundario, para mostrar en la tarjeta
 * el oficio que se buscó y no siempre el principal.
 * {@code perfilCompleto} es el porcentaje (0 a 100) del perfil de trabajo lleno; {@code null} si no se conoce.
 * {@code usuarioId} es la cuenta en Users: con ella el front pide la foto de perfil a Imágenes.
 */
@SuppressWarnings("java:S107")
public record TrabajadorDTO(UUID id, String nombreCompleto, String fotoPerfilUrl, UUID oficioPrincipalId,
                            String oficioPrincipal, List<UUID> oficiosSecundariosIds, List<Long> barrioIds,
                            String zona, BigDecimal tarifaAproximada, Boolean disponibleAhora,
                            List<DisponibilidadDTO> disponibilidad, Integer trabajosCompletados,
                            List<OficioOfrecidoDTO> oficiosSecundarios, Integer perfilCompleto, UUID usuarioId) {

    /** Trabajador sin la cuenta de Users. */
    public TrabajadorDTO(UUID id, String nombreCompleto, String fotoPerfilUrl, UUID oficioPrincipalId,
                         String oficioPrincipal, List<UUID> oficiosSecundariosIds, List<Long> barrioIds,
                         String zona, BigDecimal tarifaAproximada, Boolean disponibleAhora,
                         List<DisponibilidadDTO> disponibilidad, Integer trabajosCompletados,
                         List<OficioOfrecidoDTO> oficiosSecundarios, Integer perfilCompleto) {
        this(id, nombreCompleto, fotoPerfilUrl, oficioPrincipalId, oficioPrincipal, oficiosSecundariosIds, barrioIds,
                zona, tarifaAproximada, disponibleAhora, disponibilidad, trabajosCompletados, oficiosSecundarios,
                perfilCompleto, null);
    }

    /** Trabajador sin el porcentaje de perfil calculado. */
    public TrabajadorDTO(UUID id, String nombreCompleto, String fotoPerfilUrl, UUID oficioPrincipalId,
                         String oficioPrincipal, List<UUID> oficiosSecundariosIds, List<Long> barrioIds,
                         String zona, BigDecimal tarifaAproximada, Boolean disponibleAhora,
                         List<DisponibilidadDTO> disponibilidad, Integer trabajosCompletados,
                         List<OficioOfrecidoDTO> oficiosSecundarios) {
        this(id, nombreCompleto, fotoPerfilUrl, oficioPrincipalId, oficioPrincipal, oficiosSecundariosIds, barrioIds,
                zona, tarifaAproximada, disponibleAhora, disponibilidad, trabajosCompletados, oficiosSecundarios, null);
    }

    /** Trabajador sin el detalle de sus oficios secundarios: la tarjeta muestra siempre el principal. */
    public TrabajadorDTO(UUID id, String nombreCompleto, String fotoPerfilUrl, UUID oficioPrincipalId,
                         String oficioPrincipal, List<UUID> oficiosSecundariosIds, List<Long> barrioIds,
                         String zona, BigDecimal tarifaAproximada, Boolean disponibleAhora,
                         List<DisponibilidadDTO> disponibilidad, Integer trabajosCompletados) {
        this(id, nombreCompleto, fotoPerfilUrl, oficioPrincipalId, oficioPrincipal, oficiosSecundariosIds, barrioIds,
                zona, tarifaAproximada, disponibleAhora, disponibilidad, trabajosCompletados, List.of());
    }

    /**
     * Reputation guarda las reseñas con la cuenta de Users (el id que Service usa para el trabajador), no con el
     * id del perfil de Worker. Si no se conoce la cuenta, se usa el id del perfil.
     */
    public UUID idEnReputation() {
        return usuarioId != null ? usuarioId : id;
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
