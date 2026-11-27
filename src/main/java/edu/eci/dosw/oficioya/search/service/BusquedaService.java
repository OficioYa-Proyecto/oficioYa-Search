package edu.eci.dosw.oficioya.search.service;

import java.util.UUID;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.eci.dosw.oficioya.search.client.OrquestadorClient;
import edu.eci.dosw.oficioya.search.client.TrabajadoresConReputacion;
import edu.eci.dosw.oficioya.search.domain.Busqueda;
import edu.eci.dosw.oficioya.search.domain.CriteriosBusqueda;
import edu.eci.dosw.oficioya.search.domain.FranjaDisponibilidad;
import edu.eci.dosw.oficioya.search.domain.InterpretacionIA;
import edu.eci.dosw.oficioya.search.domain.OrdenCalificacion;
import edu.eci.dosw.oficioya.search.domain.OrdenamientoPorReputacion;
import edu.eci.dosw.oficioya.search.domain.ResultadoBusqueda;
import edu.eci.dosw.oficioya.search.domain.TipoBusqueda;
import edu.eci.dosw.oficioya.search.domain.filtro.FiltroBusqueda;
import edu.eci.dosw.oficioya.search.dto.BusquedaResponse;
import edu.eci.dosw.oficioya.search.dto.OficioDTO;
import edu.eci.dosw.oficioya.search.dto.OficioOfrecidoDTO;
import edu.eci.dosw.oficioya.search.dto.ReputacionDTO;
import edu.eci.dosw.oficioya.search.dto.ResultadoBusquedaResponse;
import edu.eci.dosw.oficioya.search.dto.TrabajadorDTO;
import edu.eci.dosw.oficioya.search.exception.IntegracionException;
import edu.eci.dosw.oficioya.search.exception.SolicitudInvalidaException;

/**
 * Casos de uso de Search. No guarda información: consulta Worker y Reputation a través del Orchestrator
 * y registra un log de cada búsqueda.
 */
@Service
public class BusquedaService {

    private static final Logger log = LoggerFactory.getLogger(BusquedaService.class);

    private final OrquestadorClient orquestadorClient;
    private final InterpretadorLenguajeNatural interpretador;
    private final OrdenamientoPorReputacion ordenamiento = new OrdenamientoPorReputacion();
    private final double umbralConfianza;
    private final int maxSugerencias;

    public BusquedaService(OrquestadorClient orquestadorClient, InterpretadorLenguajeNatural interpretador,
                           @Value("${oficioya.ia.umbral-confianza:0.6}") double umbralConfianza,
                           @Value("${oficioya.search.max-sugerencias:10}") int maxSugerencias) {
        this.orquestadorClient = orquestadorClient;
        this.interpretador = interpretador;
        this.umbralConfianza = umbralConfianza;
        this.maxSugerencias = maxSugerencias;
    }

    /**
     * Catálogo de oficios para la búsqueda por oficio (RF-3).
     */
    public List<OficioDTO> consultarOficios() {
        return orquestadorClient.obtenerOficios();
    }

    /**
     * Búsqueda por oficio con filtros combinados, ordenada por reputación (RF-3, RF-4, RF-5, RF-6, RF-7).
     */
    public BusquedaResponse buscarPorOficio(String usuario, CriteriosBusqueda criterios) {
        long inicio = System.currentTimeMillis();
        List<ResultadoBusqueda> resultados = enOrden(ejecutar(criterios), criterios.orden());
        registrarLog(usuario, TipoBusqueda.OFICIO, criterios.oficioId(), resultados.size(), inicio);
        return new BusquedaResponse(TipoBusqueda.OFICIO, null, null, null, resultados.size(), aRespuesta(resultados));
    }

    /**
     * Todos los trabajadores activos, sin oficio ni texto, con los filtros combinados. Es lo que se muestra al
     * entrar a buscar sin haber elegido nada.
     */
    public BusquedaResponse buscarTodos(String usuario, CriteriosBusqueda criterios) {
        long inicio = System.currentTimeMillis();
        List<ResultadoBusqueda> resultados = enOrden(
                aResultados(orquestadorClient.buscarConReputacion(null), null, criterios), criterios.orden());
        registrarLog(usuario, TipoBusqueda.TODOS, null, resultados.size(), inicio);
        return new BusquedaResponse(TipoBusqueda.TODOS, null, null, null, resultados.size(), aRespuesta(resultados));
    }

    /**
     * Búsqueda en lenguaje natural (RF-1, RF-2). Primero se revisa si el texto es el nombre de un trabajador;
     * si no lo es, la IA identifica el oficio y se sugieren los trabajadores
     * con mayor reputación en ese oficio. Si no se identifica el oficio, se pide más detalle.
     */
    public BusquedaResponse buscarPorLenguajeNatural(String usuario, String texto, CriteriosBusqueda criterios) {
        long inicio = System.currentTimeMillis();
        String descripcion = sanear(texto);
        // Si el texto es el nombre de uno o más trabajadores, se muestran ellos y no se consulta la IA.
        List<TrabajadorDTO> porNombre = trabajadoresConNombre(descripcion);
        if (!porNombre.isEmpty()) {
            List<ResultadoBusqueda> resultados = enOrden(aResultados(porNombre, null, criterios), criterios.orden());
            registrarLog(usuario, TipoBusqueda.NOMBRE, null, resultados.size(), inicio);
            return new BusquedaResponse(TipoBusqueda.NOMBRE, null, null, null, resultados.size(),
                    aRespuesta(resultados));
        }
        InterpretacionIA interpretacion = interpretador.interpretar(descripcion, orquestadorClient.obtenerOficios());
        if (!interpretacion.esConfiable(umbralConfianza)) {
            registrarLog(usuario, TipoBusqueda.LENGUAJE_NATURAL, null, 0, inicio);
            return new BusquedaResponse(TipoBusqueda.LENGUAJE_NATURAL, interpretacion.estado(), null,
                    interpretacion.mensajeAclaracion(), 0, List.of());
        }
        // Se toman las de mayor reputación y luego se presentan en el orden pedido.
        List<ResultadoBusqueda> sugerencias = enOrden(ejecutar(criterios.conOficio(interpretacion.oficioId())).stream()
                .limit(maxSugerencias)
                .toList(), criterios.orden());
        registrarLog(usuario, TipoBusqueda.LENGUAJE_NATURAL, interpretacion.oficioId(), sugerencias.size(), inicio);
        return new BusquedaResponse(TipoBusqueda.LENGUAJE_NATURAL, interpretacion.estado(),
                interpretacion.oficioIdentificado(), null, sugerencias.size(), aRespuesta(sugerencias));
    }

    private List<ResultadoBusqueda> ejecutar(CriteriosBusqueda criterios) {
        if (criterios.oficioId() == null) {
            throw new SolicitudInvalidaException("Debes indicar el oficio que buscas.");
        }
        TrabajadoresConReputacion encontrados = orquestadorClient.buscarConReputacion(criterios.oficioId());
        List<TrabajadorDTO> queOfrecen = encontrados.trabajadores().stream()
                .filter(t -> t.ofrece(criterios.oficioId()))
                .toList();
        return aResultados(new TrabajadoresConReputacion(queOfrecen, encontrados.reputaciones()),
                criterios.oficioId(), criterios);
    }

    /**
     * Agrega la reputación, aplica los filtros y ordena por reputación.
     */
    private List<ResultadoBusqueda> aResultados(List<TrabajadorDTO> trabajadores, UUID oficioBuscado,
                                                CriteriosBusqueda criterios) {
        Map<UUID, ReputacionDTO> reputaciones = orquestadorClient.obtenerReputaciones(
                trabajadores.stream().map(TrabajadorDTO::idEnReputation).distinct().toList());
        return aResultados(new TrabajadoresConReputacion(trabajadores, reputaciones), oficioBuscado, criterios);
    }

    private List<ResultadoBusqueda> aResultados(TrabajadoresConReputacion encontrados, UUID oficioBuscado,
                                                CriteriosBusqueda criterios) {
        FiltroBusqueda filtro = criterios.construirFiltro();
        List<ResultadoBusqueda> filtrados = encontrados.trabajadores().stream()
                .map(t -> aResultado(t, oficioBuscado, encontrados.reputacionDe(t)))
                .filter(filtro::cumple)
                .toList();
        return ordenamiento.ordenar(filtrados);
    }

    /**
     * Trabajadores activos cuyo nombre contiene todas las palabras del texto (sin importar tildes ni mayúsculas;
     * cada palabra puede ser el inicio de una del nombre: "yazid sanch" encuentra a "Yazid Sánchez").
     * Si Worker no responde, se sigue con la búsqueda por oficio.
     */
    private List<TrabajadorDTO> trabajadoresConNombre(String texto) {
        List<String> buscadas = palabras(texto);
        if (buscadas.isEmpty()) {
            return List.of();
        }
        try {
            return orquestadorClient.listarTrabajadoresActivos().stream()
                    .filter(t -> t.nombreCompleto() != null && coincide(palabras(t.nombreCompleto()), buscadas))
                    .toList();
        } catch (IntegracionException e) {
            log.warn("No fue posible buscar por nombre; se sigue con la interpretación del oficio", e);
            return List.of();
        }
    }

    /** Palabras en minúscula y sin tildes. */
    static List<String> palabras(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Arrays.stream(sinTildes.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(palabra -> !palabra.isBlank())
                .toList();
    }

    static boolean coincide(List<String> delNombre, List<String> buscadas) {
        return buscadas.stream().allMatch(buscada -> delNombre.stream().anyMatch(n -> n.startsWith(buscada)));
    }

    private static List<ResultadoBusqueda> enOrden(List<ResultadoBusqueda> resultados, OrdenCalificacion orden) {
        return orden == OrdenCalificacion.MAYOR_A_MENOR ? resultados : new OrdenamientoPorReputacion(orden).ordenar(resultados);
    }

    /**
     * Arma el resultado con el oficio buscado: si el trabajador lo ofrece como secundario, la tarjeta muestra ese
     * oficio y su tarifa (y el filtro de precio usa esa tarifa), no los del oficio principal.
     */
    private static ResultadoBusqueda aResultado(TrabajadorDTO t, UUID oficioBuscado, ReputacionDTO reputacion) {
        OficioOfrecidoDTO oficio = t.oficioParaMostrar(oficioBuscado);
        double calificacion = reputacion == null || reputacion.reputacion() == null ? 0 : reputacion.reputacion();
        int resenas = reputacion == null || reputacion.totalResenas() == null ? 0 : reputacion.totalResenas();
        int trabajos = reputacion == null || reputacion.trabajosCompletados() == null
                ? (t.trabajosCompletados() == null ? 0 : t.trabajosCompletados())
                : reputacion.trabajosCompletados();
        List<FranjaDisponibilidad> franjas = t.disponibilidad() == null ? List.of() : t.disponibilidad().stream()
                .map(d -> new FranjaDisponibilidad(d.diaSemana(), d.horaInicio(), d.horaFin()))
                .toList();
        return new ResultadoBusqueda(t.id(), t.nombreCompleto(), t.fotoPerfilUrl(), oficio.nombre(), t.zona(),
                t.barrioIds() == null ? null : Set.copyOf(t.barrioIds()), oficio.tarifa(),
                calificacion, resenas, trabajos,
                Boolean.TRUE.equals(t.disponibleAhora()), franjas,
                t.perfilCompleto() == null ? 0 : t.perfilCompleto()).conUsuario(t.usuarioId());
    }

    private static List<ResultadoBusquedaResponse> aRespuesta(List<ResultadoBusqueda> resultados) {
        return resultados.stream().map(ResultadoBusquedaResponse::desde).toList();
    }

    /**
     * Valida y limpia el texto antes de enviarlo a la IA: elimina caracteres de control y espacios repetidos.
     */
    static String sanear(String texto) {
        String limpio = texto == null ? "" : texto.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (limpio.length() < 3) {
            throw new SolicitudInvalidaException("Describe el servicio que necesitas con al menos 3 caracteres.");
        }
        return limpio;
    }

    private static void registrarLog(String usuario, TipoBusqueda tipo, UUID oficioId, int total, long inicio) {
        Busqueda busqueda = new Busqueda(usuario, tipo, oficioId, total, System.currentTimeMillis() - inicio,
                LocalDateTime.now());
        log.info("Búsqueda realizada: {}", busqueda.resumen());
    }
}
