package edu.eci.dosw.oficioya.search.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.eci.dosw.oficioya.search.dto.ReputacionDTO;
import edu.eci.dosw.oficioya.search.dto.TrabajadorDTO;

/**
 * Trabajadores activos con su reputación, indexada por {@link TrabajadorDTO#idEnReputation()}.
 */
public record TrabajadoresConReputacion(List<TrabajadorDTO> trabajadores, Map<UUID, ReputacionDTO> reputaciones) {

    public TrabajadoresConReputacion {
        trabajadores = trabajadores == null ? List.of() : List.copyOf(trabajadores);
        reputaciones = reputaciones == null ? Map.of() : Map.copyOf(reputaciones);
    }

    public ReputacionDTO reputacionDe(TrabajadorDTO trabajador) {
        return reputaciones.get(trabajador.idEnReputation());
    }
}
