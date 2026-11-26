package edu.eci.dosw.oficioya.search.service;

import java.util.UUID;

import static edu.eci.dosw.oficioya.search.IdsDePrueba.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.oficioya.search.client.OrquestadorClient;
import edu.eci.dosw.oficioya.search.domain.CriteriosBusqueda;
import edu.eci.dosw.oficioya.search.domain.EstadoInterpretacion;
import edu.eci.dosw.oficioya.search.domain.InterpretacionIA;
import edu.eci.dosw.oficioya.search.domain.OrdenCalificacion;
import edu.eci.dosw.oficioya.search.domain.TipoBusqueda;
import edu.eci.dosw.oficioya.search.dto.BusquedaResponse;
import edu.eci.dosw.oficioya.search.dto.DisponibilidadDTO;
import edu.eci.dosw.oficioya.search.dto.OficioDTO;
import edu.eci.dosw.oficioya.search.dto.OficioOfrecidoDTO;
import edu.eci.dosw.oficioya.search.dto.ReputacionDTO;
import edu.eci.dosw.oficioya.search.dto.ResultadoBusquedaResponse;
import edu.eci.dosw.oficioya.search.dto.TrabajadorDTO;
import edu.eci.dosw.oficioya.search.exception.IntegracionException;
import edu.eci.dosw.oficioya.search.exception.SolicitudInvalidaException;

class BusquedaServiceTest {

    private static final UUID PLOMERIA = id(1);
    private static final List<OficioDTO> CATALOGO = List.of(new OficioDTO(PLOMERIA, "Plomería", null));

    private OrquestadorClient orquestador;
    private InterpretadorLenguajeNatural interpretador;
    private BusquedaService servicio;

    private static TrabajadorDTO trabajador(long numero, UUID oficioPrincipal, List<UUID> secundarios, int tarifa,
                                            boolean disponibleAhora, Integer trabajos) {
        return new TrabajadorDTO(id(numero), "Trabajador " + numero, null, oficioPrincipal, "Oficio " + oficioPrincipal,
                secundarios, List.of(10L), "Chapinero", BigDecimal.valueOf(tarifa), disponibleAhora,
                List.of(new DisponibilidadDTO(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))), trabajos);
    }

    @BeforeEach
    void preparar() {
        orquestador = mock(OrquestadorClient.class);
        interpretador = mock(InterpretadorLenguajeNatural.class);
        servicio = new BusquedaService(orquestador, interpretador, 0.6, 2);
        when(orquestador.obtenerOficios()).thenReturn(CATALOGO);
        when(orquestador.buscarTrabajadoresActivos(PLOMERIA)).thenReturn(List.of(
                trabajador(1, PLOMERIA, List.of(), 40_000, true, 10),
                trabajador(2, id(9), List.of(PLOMERIA), 60_000, false, 50),
                trabajador(3, PLOMERIA, null, 30_000, true, null),
                trabajador(4, id(9), List.of(id(8)), 20_000, true, 5)));
        when(orquestador.obtenerReputaciones(anyList())).thenReturn(Map.of(
                id(1), new ReputacionDTO(id(1), 4.5, 10),
                id(2), new ReputacionDTO(id(2), 4.9, 30)));
    }

    @Test
    void buscaPorOficioPrincipalYSecundarioYOrdenaPorReputacion() {
        BusquedaResponse respuesta = servicio.buscarPorOficio("u1", CriteriosBusqueda.soloOficio(PLOMERIA));

        assertThat(respuesta.tipo()).isEqualTo(TipoBusqueda.OFICIO);
        // El 4 no ofrece plomería; el 3 no tiene reseñas (calificación 0) y queda de último.
        assertThat(respuesta.resultados()).extracting(ResultadoBusquedaResponse::trabajadorId)
                .containsExactly(id(2), id(1), id(3));
        assertThat(respuesta.resultados()).extracting(ResultadoBusquedaResponse::posicion).containsExactly(1, 2, 3);
        assertThat(respuesta.total()).isEqualTo(3);
    }

    @Test
    void siElOficioBuscadoEsSecundarioMuestraEseOficioYSuTarifa() {
        UUID electricidad = id(9);
        TrabajadorDTO electricistaQueTambienEsPlomero = new TrabajadorDTO(id(5), "Munar", null, electricidad,
                "Electricista", List.of(PLOMERIA), List.of(10L), null, new BigDecimal("45000"), true, List.of(), 0,
                List.of(new OficioOfrecidoDTO(PLOMERIA, "Plomero", new BigDecimal("70000"))));
        when(orquestador.buscarTrabajadoresActivos(PLOMERIA)).thenReturn(List.of(electricistaQueTambienEsPlomero));

        ResultadoBusquedaResponse resultado = servicio.buscarPorOficio("u1", CriteriosBusqueda.soloOficio(PLOMERIA))
                .resultados().get(0);

        assertThat(resultado.oficio()).isEqualTo("Plomero");
        assertThat(resultado.tarifaAproximada()).isEqualByComparingTo("70000");
    }

    @Test
    void elFiltroDePrecioUsaLaTarifaDelOficioBuscado() {
        TrabajadorDTO trabajador = new TrabajadorDTO(id(5), "Munar", null, id(9), "Electricista", List.of(PLOMERIA),
                List.of(10L), null, new BigDecimal("45000"), true, List.of(), 0,
                List.of(new OficioOfrecidoDTO(PLOMERIA, "Plomero", new BigDecimal("70000"))));
        when(orquestador.buscarTrabajadoresActivos(PLOMERIA)).thenReturn(List.of(trabajador));
        CriteriosBusqueda hasta50mil = new CriteriosBusqueda(PLOMERIA, null, null, new BigDecimal("50000"),
                null, null, false, null);

        assertThat(servicio.buscarPorOficio("u1", hasta50mil).resultados()).isEmpty();
    }

    @Test
    void aplicaLosFiltrosCombinados() {
        CriteriosBusqueda criterios = new CriteriosBusqueda(PLOMERIA, List.of(10L), new BigDecimal("30000"),
                new BigDecimal("50000"), DayOfWeek.MONDAY, LocalTime.of(9, 0), true, 4.0);

        BusquedaResponse respuesta = servicio.buscarPorOficio("u1", criterios);

        assertThat(respuesta.resultados()).extracting(ResultadoBusquedaResponse::trabajadorId).containsExactly(id(1));
    }

    @Test
    void sinOficioLaBusquedaEsInvalida() {
        CriteriosBusqueda sinOficio = CriteriosBusqueda.soloOficio(null);
        assertThatThrownBy(() -> servicio.buscarPorOficio("u1", sinOficio))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void lenguajeNaturalSugiereLosDeMayorReputacionEnElOficioIdentificado() {
        when(interpretador.interpretar("necesito arreglar una gotera", CATALOGO))
                .thenReturn(InterpretacionIA.identificado(PLOMERIA, "Plomería", 0.9));

        BusquedaResponse respuesta = servicio.buscarPorLenguajeNatural("u1", "  necesito arreglar\tuna gotera ",
                CriteriosBusqueda.soloOficio(null));

        assertThat(respuesta.tipo()).isEqualTo(TipoBusqueda.LENGUAJE_NATURAL);
        assertThat(respuesta.estado()).isEqualTo(EstadoInterpretacion.OFICIO_IDENTIFICADO);
        assertThat(respuesta.oficioIdentificado()).isEqualTo("Plomería");
        // máximo 2 sugerencias, las de mayor reputación
        assertThat(respuesta.resultados()).extracting(ResultadoBusquedaResponse::trabajadorId).containsExactly(id(2), id(1));
    }

    @Test
    void siElTextoEsElNombreDeUnTrabajadorLoMuestraSinConsultarLaIA() {
        TrabajadorDTO yazid = new TrabajadorDTO(id(7), "Yazid Sánchez", null, PLOMERIA, "Plomero", List.of(),
                List.of(10L), null, new BigDecimal("80000"), true, List.of(), 0);
        TrabajadorDTO otro = new TrabajadorDTO(id(8), "Samuel Lozano", null, PLOMERIA, "Plomero", List.of(),
                List.of(10L), null, new BigDecimal("60000"), true, List.of(), 0);
        when(orquestador.listarTrabajadoresActivos()).thenReturn(List.of(yazid, otro));

        BusquedaResponse respuesta = servicio.buscarPorLenguajeNatural("u1", "yazid SANCH",
                CriteriosBusqueda.soloOficio(null));

        assertThat(respuesta.tipo()).isEqualTo(TipoBusqueda.NOMBRE);
        assertThat(respuesta.resultados()).extracting(ResultadoBusquedaResponse::nombreCompleto)
                .containsExactly("Yazid Sánchez");
        verify(interpretador, never()).interpretar(anyString(), any());
    }

    @Test
    void siNingunNombreCoincideSeUsaLaIA() {
        when(orquestador.listarTrabajadoresActivos()).thenReturn(List.of(new TrabajadorDTO(id(7), "Yazid Sánchez",
                null, PLOMERIA, "Plomero", List.of(), List.of(), null, BigDecimal.ONE, true, List.of(), 0)));
        when(interpretador.interpretar(anyString(), any()))
                .thenReturn(InterpretacionIA.identificado(PLOMERIA, "Plomería", 0.9));

        BusquedaResponse respuesta = servicio.buscarPorLenguajeNatural("u1", "necesito un plomero",
                CriteriosBusqueda.soloOficio(null));

        assertThat(respuesta.tipo()).isEqualTo(TipoBusqueda.LENGUAJE_NATURAL);
    }

    @Test
    void siWorkerFallaAlBuscarPorNombreSeUsaLaIA() {
        when(orquestador.listarTrabajadoresActivos()).thenThrow(new IntegracionException("caído", null));
        when(interpretador.interpretar(anyString(), any())).thenReturn(InterpretacionIA.requiereMasDetalle());

        BusquedaResponse respuesta = servicio.buscarPorLenguajeNatural("u1", "hola", CriteriosBusqueda.soloOficio(null));

        assertThat(respuesta.estado()).isEqualTo(EstadoInterpretacion.REQUIERE_MAS_DETALLE);
    }

    @Test
    void lasPalabrasSeComparanSinTildesNiMayusculas() {
        assertThat(BusquedaService.palabras("  José  MARÍA-Núñez ")).containsExactly("jose", "maria", "nunez");
        assertThat(BusquedaService.coincide(List.of("jose", "nunez"), List.of("nu"))).isTrue();
        assertThat(BusquedaService.coincide(List.of("jose", "nunez"), List.of("jose", "perez"))).isFalse();
    }

    @Test
    void lenguajeNaturalPideMasDetalleSinConsultarTrabajadores() {
        when(interpretador.interpretar(anyString(), any())).thenReturn(InterpretacionIA.requiereMasDetalle());

        BusquedaResponse respuesta = servicio.buscarPorLenguajeNatural("u1", "hola", CriteriosBusqueda.soloOficio(null));

        assertThat(respuesta.estado()).isEqualTo(EstadoInterpretacion.REQUIERE_MAS_DETALLE);
        assertThat(respuesta.mensaje()).isEqualTo(InterpretacionIA.MENSAJE_MAS_DETALLE);
        assertThat(respuesta.resultados()).isEmpty();
        verify(orquestador, never()).buscarTrabajadoresActivos(any());
    }

    @Test
    void lenguajeNaturalConConfianzaBajaPideMasDetalle() {
        when(interpretador.interpretar(anyString(), any()))
                .thenReturn(InterpretacionIA.identificado(PLOMERIA, "Plomería", 0.3));
        BusquedaResponse respuesta = servicio.buscarPorLenguajeNatural("u1", "algo raro", CriteriosBusqueda.soloOficio(null));
        assertThat(respuesta.resultados()).isEmpty();
        verify(orquestador, never()).buscarTrabajadoresActivos(any());
    }

    @Test
    void elTextoSeValidaAntesDeEnviarloALaIA() {
        CriteriosBusqueda criterios = CriteriosBusqueda.soloOficio(null);
        assertThatThrownBy(() -> servicio.buscarPorLenguajeNatural("u1", " \n ", criterios))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.buscarPorLenguajeNatural("u1", null, criterios))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(interpretador, never()).interpretar(anyString(), any());
    }

    @Test
    void sanearEliminaCaracteresDeControlYEspaciosRepetidos() {
        assertThat(BusquedaService.sanear("arreglar\u0000 la   llave\n")).isEqualTo("arreglar la llave");
    }

    @Test
    void sinOficioNiTextoMuestraTodosLosTrabajadoresConElPerfilMasCompletoPrimero() {
        TrabajadorDTO incompleto = new TrabajadorDTO(id(1), "Ana", null, PLOMERIA, "Plomero", List.of(), List.of(10L),
                null, BigDecimal.ONE, true, List.of(), 0, List.of(), 50, id(301));
        TrabajadorDTO completo = new TrabajadorDTO(id(2), "Luis", null, id(9), "Electricista", List.of(),
                List.of(10L), null, BigDecimal.ONE, true, List.of(), 0, List.of(), 100);
        TrabajadorDTO sinDato = trabajador(3, PLOMERIA, List.of(), 10_000, false, 0);
        when(orquestador.listarTrabajadoresActivos()).thenReturn(List.of(incompleto, sinDato, completo));

        BusquedaResponse respuesta = servicio.buscarTodos("u1", new CriteriosBusqueda(null, null, null, null, null,
                null, false, null, null, OrdenCalificacion.PERFIL_COMPLETO));

        assertThat(respuesta.tipo()).isEqualTo(TipoBusqueda.TODOS);
        assertThat(respuesta.resultados()).extracting(ResultadoBusquedaResponse::trabajadorId)
                .containsExactly(id(2), id(1), id(3));
        assertThat(respuesta.resultados()).extracting(ResultadoBusquedaResponse::perfilCompleto)
                .containsExactly(100, 50, 0);
        assertThat(respuesta.resultados().get(1).usuarioId()).isEqualTo(id(301));
    }

    @Test
    void consultaElCatalogoDeOficios() {
        assertThat(servicio.consultarOficios()).isEqualTo(CATALOGO);
    }
}
