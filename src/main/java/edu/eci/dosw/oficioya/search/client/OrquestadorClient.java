package edu.eci.dosw.oficioya.search.client;

import java.util.UUID;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import edu.eci.dosw.oficioya.search.dto.OficioDTO;
import edu.eci.dosw.oficioya.search.dto.ReputacionDTO;
import edu.eci.dosw.oficioya.search.dto.TrabajadorDTO;

/**
 * Única salida de Search hacia otros dominios (Worker, Reputation) y hacia la IA.
 * Toda llamada pasa por el Orchestrator; Search nunca se comunica directamente con otro microservicio.
 */
public interface OrquestadorClient {

    /**
     * Trabajadores ACTIVO que ofrecen el oficio (principal o secundario). Nunca incluye pausados ni inactivos.
     */
    List<TrabajadorDTO> buscarTrabajadoresActivos(UUID oficioId);

    /**
     * Todos los trabajadores ACTIVO, con su nombre de Users. Se usa para buscar por nombre.
     */
    List<TrabajadorDTO> listarTrabajadoresActivos();

    /**
     * Trabajadores ACTIVO (de un oficio, o todos si {@code oficioId} es {@code null}) con su reputación.
     * Esta versión consulta una cosa tras otra; la implementación REST pide Users y Reputation al mismo tiempo.
     */
    default TrabajadoresConReputacion buscarConReputacion(UUID oficioId) {
        List<TrabajadorDTO> trabajadores = oficioId == null
                ? listarTrabajadoresActivos()
                : buscarTrabajadoresActivos(oficioId);
        return new TrabajadoresConReputacion(trabajadores, obtenerReputaciones(
                trabajadores.stream().map(TrabajadorDTO::idEnReputation).distinct().toList()));
    }

    /**
     * Catálogo de oficios de Worker.
     */
    List<OficioDTO> obtenerOficios();

    /**
     * Reputación de cada trabajador, indexada por su id.
     */
    Map<UUID, ReputacionDTO> obtenerReputaciones(Collection<UUID> trabajadorIds);

    /**
     * Envía un prompt a la IA y devuelve su respuesta en texto.
     */
    String solicitarInterpretacionIA(String prompt);
}
