package edu.eci.dosw.oficioya.search.client;

import java.util.UUID;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import edu.eci.dosw.oficioya.search.client.users.UsuarioPublicoDTO;
import edu.eci.dosw.oficioya.search.client.worker.OficioWorkerDTO;
import edu.eci.dosw.oficioya.search.client.worker.TrabajadorWorkerDTO;
import edu.eci.dosw.oficioya.search.dto.OficioDTO;
import edu.eci.dosw.oficioya.search.dto.ReputacionDTO;
import edu.eci.dosw.oficioya.search.dto.RespuestaIADTO;
import edu.eci.dosw.oficioya.search.dto.SolicitudIADTO;
import edu.eci.dosw.oficioya.search.dto.TrabajadorDTO;
import edu.eci.dosw.oficioya.search.exception.IntegracionException;

/**
 * Implementación REST de {@link OrquestadorClient}: todas las llamadas van al Orchestrator, que las reenvía
 * al dominio correspondiente. Las rutas son configurables. Los formatos de Worker son los de su API real
 * ({@code GET /api/v1/workers} y {@code GET /api/v1/trades}); los nombres vienen de Users ({@code GET /api/v1/users?ids=}).
 */
@Component
@Profile("!demo")
public class OrquestadorRestClient implements OrquestadorClient {

    private static final Logger log = LoggerFactory.getLogger(OrquestadorRestClient.class);

    /** Users acepta como máximo 50 ids por consulta. */
    static final int MAX_IDS_POR_CONSULTA = 50;

    private final RestClient restClient;
    private final String rutaTrabajadores;
    private final String rutaOficios;
    private final String rutaReputaciones;
    private final String rutaInterpretacionIA;
    private final String rutaUsuarios;
    /**
     * Hilos virtuales para pedir Users y Reputation al mismo tiempo. Cada tarea lleva el contexto de seguridad de
     * quien la lanza, para que {@link PropagacionTokenInterceptor} reenvíe el JWT del usuario.
     */
    private final Executor paralelo = new DelegatingSecurityContextExecutor(Executors.newVirtualThreadPerTaskExecutor());

    @SuppressWarnings("java:S107")
    public OrquestadorRestClient(RestClient.Builder builder,
                                 @Value("${oficioya.orquestador.base-url}") String baseUrl,
                                 @Value("${oficioya.orquestador.rutas.trabajadores-activos}") String rutaTrabajadores,
                                 @Value("${oficioya.orquestador.rutas.oficios}") String rutaOficios,
                                 @Value("${oficioya.orquestador.rutas.reputaciones}") String rutaReputaciones,
                                 @Value("${oficioya.orquestador.rutas.interpretacion-ia}") String rutaInterpretacionIA,
                                 @Value("${oficioya.orquestador.rutas.usuarios}") String rutaUsuarios) {
        this.restClient = builder.baseUrl(baseUrl).requestInterceptor(new PropagacionTokenInterceptor()).build();
        this.rutaTrabajadores = rutaTrabajadores;
        this.rutaOficios = rutaOficios;
        this.rutaReputaciones = rutaReputaciones;
        this.rutaInterpretacionIA = rutaInterpretacionIA;
        this.rutaUsuarios = rutaUsuarios;
    }

    @Override
    public List<TrabajadorDTO> buscarTrabajadoresActivos(UUID oficioId) {
        return consultarTrabajadores(oficioId);
    }

    @Override
    public List<TrabajadorDTO> listarTrabajadoresActivos() {
        return consultarTrabajadores(null);
    }

    /**
     * Pide a Worker los trabajadores y, apenas responde, consulta al mismo tiempo los nombres (Users) y la
     * reputación (Reputation): ambas solo necesitan los ids que entrega Worker.
     */
    @Override
    public TrabajadoresConReputacion buscarConReputacion(UUID oficioId) {
        List<TrabajadorWorkerDTO> trabajadores = trabajadoresDeWorker(oficioId);
        if (trabajadores.isEmpty()) {
            return new TrabajadoresConReputacion(List.of(), Map.of());
        }
        CompletableFuture<Map<UUID, String>> nombres = CompletableFuture.supplyAsync(
                () -> nombresDeUsuarios(idsDeCuentas(trabajadores)), paralelo);
        CompletableFuture<Map<UUID, ReputacionDTO>> reputaciones = CompletableFuture.supplyAsync(
                () -> obtenerReputaciones(trabajadores.stream()
                        .map(t -> t.userId() != null ? t.userId() : t.id()).distinct().toList()), paralelo);
        try {
            Map<UUID, String> porCuenta = nombres.join();
            List<TrabajadorDTO> conNombre = trabajadores.stream()
                    .map(trabajador -> trabajador.aTrabajador(porCuenta.get(trabajador.userId())))
                    .toList();
            return new TrabajadoresConReputacion(conNombre, reputaciones.join());
        } catch (CompletionException e) {
            if (e.getCause() instanceof IntegracionException integracion) {
                throw integracion;
            }
            throw e;
        }
    }

    /**
     * Worker ya devuelve solo los trabajadores ACTIVOS; con {@code tradeId} filtra por oficio principal o
     * secundario, y sin él entrega todos (para la búsqueda por nombre).
     */
    private List<TrabajadorDTO> consultarTrabajadores(UUID oficioId) {
        List<TrabajadorWorkerDTO> trabajadores = trabajadoresDeWorker(oficioId);
        if (trabajadores.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> nombres = nombresDeUsuarios(idsDeCuentas(trabajadores));
        return trabajadores.stream()
                .map(trabajador -> trabajador.aTrabajador(nombres.get(trabajador.userId())))
                .toList();
    }

    private List<TrabajadorWorkerDTO> trabajadoresDeWorker(UUID oficioId) {
        try {
            List<TrabajadorWorkerDTO> trabajadores = restClient.get()
                    .uri(uri -> oficioId == null
                            ? uri.path(rutaTrabajadores).build()
                            : uri.path(rutaTrabajadores).queryParam("tradeId", oficioId).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TrabajadorWorkerDTO>>() { });
            return trabajadores == null ? List.of() : trabajadores;
        } catch (RestClientException e) {
            throw new IntegracionException("No fue posible consultar los trabajadores en Worker", e);
        }
    }

    private static List<UUID> idsDeCuentas(List<TrabajadorWorkerDTO> trabajadores) {
        return trabajadores.stream().map(TrabajadorWorkerDTO::userId).filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Nombres de las cuentas en Users, en bloques de {@value #MAX_IDS_POR_CONSULTA} ids. Si Users no responde, la
     * búsqueda sigue: esos trabajadores quedan sin nombre y el front muestra un texto genérico.
     */
    Map<UUID, String> nombresDeUsuarios(List<UUID> usuarioIds) {
        Map<UUID, String> nombres = new HashMap<>();
        for (int inicio = 0; inicio < usuarioIds.size(); inicio += MAX_IDS_POR_CONSULTA) {
            List<UUID> bloque = usuarioIds.subList(inicio, Math.min(inicio + MAX_IDS_POR_CONSULTA, usuarioIds.size()));
            try {
                List<UsuarioPublicoDTO> usuarios = restClient.get()
                        .uri(uri -> uri.path(rutaUsuarios).queryParam("ids", bloque.toArray()).build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<UsuarioPublicoDTO>>() { });
                if (usuarios != null) {
                    usuarios.stream()
                            .filter(usuario -> usuario.id() != null && usuario.name() != null && !usuario.name().isBlank())
                            .forEach(usuario -> nombres.put(usuario.id(), usuario.name().trim()));
                }
            } catch (RestClientException e) {
                log.warn("No fue posible consultar los nombres en Users; los resultados salen sin nombre: {}",
                        e.getMessage());
            }
        }
        return nombres;
    }

    @Override
    public List<OficioDTO> obtenerOficios() {
        try {
            List<OficioWorkerDTO> oficios = restClient.get()
                    .uri(uri -> uri.path(rutaOficios).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<OficioWorkerDTO>>() { });
            return oficios == null ? List.of()
                    : oficios.stream().filter(OficioWorkerDTO::active).map(OficioWorkerDTO::aOficio).toList();
        } catch (RestClientException e) {
            throw new IntegracionException("No fue posible consultar el catálogo de oficios en Worker", e);
        }
    }

    @Override
    public Map<UUID, ReputacionDTO> obtenerReputaciones(Collection<UUID> trabajadorIds) {
        if (trabajadorIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<ReputacionDTO> reputaciones = restClient.get()
                    .uri(uri -> uri.path(rutaReputaciones).queryParam("trabajadorIds", trabajadorIds.toArray()).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ReputacionDTO>>() { });
            if (reputaciones == null) {
                return Map.of();
            }
            return reputaciones.stream()
                    .collect(Collectors.toMap(ReputacionDTO::trabajadorId, Function.identity(), (a, b) -> a));
        } catch (RestClientException e) {
            throw new IntegracionException("No fue posible consultar las reputaciones en Reputation", e);
        }
    }

    @Override
    public String solicitarInterpretacionIA(String prompt) {
        try {
            RespuestaIADTO respuesta = restClient.post()
                    .uri(uri -> uri.path(rutaInterpretacionIA).build())
                    .body(new SolicitudIADTO(prompt))
                    .retrieve()
                    .body(RespuestaIADTO.class);
            return respuesta == null ? "" : respuesta.respuesta();
        } catch (RestClientException e) {
            throw new IntegracionException("No fue posible consultar la IA", e);
        }
    }
}
