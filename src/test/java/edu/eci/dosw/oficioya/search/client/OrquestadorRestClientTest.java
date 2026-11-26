package edu.eci.dosw.oficioya.search.client;

import java.util.UUID;

import static edu.eci.dosw.oficioya.search.IdsDePrueba.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import edu.eci.dosw.oficioya.search.dto.CategoriaDTO;
import edu.eci.dosw.oficioya.search.dto.OficioDTO;
import edu.eci.dosw.oficioya.search.dto.ReputacionDTO;
import edu.eci.dosw.oficioya.search.dto.TrabajadorDTO;
import edu.eci.dosw.oficioya.search.exception.IntegracionException;

class OrquestadorRestClientTest {

    private static final String BASE = "http://orquestador";

    private MockRestServiceServer servidor;
    private OrquestadorRestClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new OrquestadorRestClient(builder, BASE, "/api/v1/workers", "/api/v1/trades",
                "/api/v1/reputation/reputaciones", "/api/ia/interpretaciones", "/api/v1/users");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consultaTrabajadoresActivosPorElOrquestadorYReenviaElJwt() {
        Jwt jwt = Jwt.withTokenValue("token-del-usuario").header("alg", "HS256").subject("contratante-1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        servidor.expect(requestTo(BASE + "/api/v1/workers?tradeId=" + id(1)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-del-usuario"))
                .andRespond(withSuccess("""
                        [{"id": "%s", "userId": "%s", "status": "ACTIVE", "availableNow": true,
                          "zoneIds": [10, 11],
                          "primaryTrade": {"id": "%s", "approxRate": 45000, "rateUnit": "PER_HOUR",
                            "trade": {"id": "%s", "name": "Plomería", "active": true}},
                          "secondaryTrades": [{"id": "%s", "approxRate": 50000, "rateUnit": "PER_JOB",
                            "trade": {"id": "%s", "name": "Técnico", "active": true}}],
                          "slots": [{"recurrenceType": "WEEKLY", "dayOfWeek": "MONDAY", "startTime": "08:00",
                                     "endTime": "17:00"},
                                    {"recurrenceType": "ONCE", "startDate": "2026-11-20", "startTime": "08:00"}],
                          "photos": []}]
                        """.formatted(id(101), id(201), id(501), id(1), id(502), id(6)) + """
                        """, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/api/v1/users?ids=" + id(201)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-del-usuario"))
                .andRespond(withSuccess("""
                        [{"id": "%s", "name": "Carlos Martínez", "email": "c***@correo.com", "roles": ["TRABAJADOR"]}]
                        """.formatted(id(201)), MediaType.APPLICATION_JSON));

        List<TrabajadorDTO> trabajadores = cliente.buscarTrabajadoresActivos(id(1));

        assertThat(trabajadores).hasSize(1);
        assertThat(trabajadores.get(0).id()).isEqualTo(id(101));
        assertThat(trabajadores.get(0).nombreCompleto()).isEqualTo("Carlos Martínez");
        assertThat(trabajadores.get(0).oficioPrincipalId()).isEqualTo(id(1));
        assertThat(trabajadores.get(0).oficioPrincipal()).isEqualTo("Plomería");
        assertThat(trabajadores.get(0).oficiosSecundariosIds()).containsExactly(id(6));
        assertThat(trabajadores.get(0).oficioParaMostrar(id(6)).nombre()).isEqualTo("Técnico");
        assertThat(trabajadores.get(0).oficioParaMostrar(id(6)).tarifa()).isEqualByComparingTo("50000");
        assertThat(trabajadores.get(0).barrioIds()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(trabajadores.get(0).tarifaAproximada()).isEqualByComparingTo("45000");
        assertThat(trabajadores.get(0).disponibleAhora()).isTrue();
        assertThat(trabajadores.get(0).disponibilidad()).hasSize(1);
        assertThat(trabajadores.get(0).disponibilidad().get(0).diaSemana()).isEqualTo(DayOfWeek.MONDAY);
        // Oficio, nombre, barrios, horario y oficios secundarios: 5 de 6 partes (le faltan fotos de trabajos).
        assertThat(trabajadores.get(0).perfilCompleto()).isEqualTo(83);
        assertThat(trabajadores.get(0).usuarioId()).isEqualTo(id(201));
        assertThat(trabajadores.get(0).disponibilidad().get(0).horaInicio()).isEqualTo(LocalTime.of(8, 0));
        servidor.verify();
    }

    @Test
    void siUsersNoRespondeLosTrabajadoresSalenSinNombre() {
        servidor.expect(requestTo(BASE + "/api/v1/workers?tradeId=" + id(1)))
                .andRespond(withSuccess("""
                        [{"id": "%s", "userId": "%s", "status": "ACTIVE", "availableNow": false}]
                        """.formatted(id(101), id(201)), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/api/v1/users?ids=" + id(201))).andRespond(withServerError());

        List<TrabajadorDTO> trabajadores = cliente.buscarTrabajadoresActivos(id(1));

        assertThat(trabajadores).singleElement().satisfies(trabajador -> {
            assertThat(trabajador.id()).isEqualTo(id(101));
            assertThat(trabajador.nombreCompleto()).isNull();
        });
        servidor.verify();
    }

    @Test
    void listaTodosLosTrabajadoresActivosSinFiltrarPorOficio() {
        servidor.expect(requestTo(BASE + "/api/v1/workers"))
                .andRespond(withSuccess("""
                        [{"id": "%s", "userId": "%s", "status": "ACTIVE", "availableNow": true}]
                        """.formatted(id(101), id(201)), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/api/v1/users?ids=" + id(201)))
                .andRespond(withSuccess("""
                        [{"id": "%s", "name": "Yazid Sánchez"}]
                        """.formatted(id(201)), MediaType.APPLICATION_JSON));

        assertThat(cliente.listarTrabajadoresActivos()).singleElement()
                .satisfies(trabajador -> assertThat(trabajador.nombreCompleto()).isEqualTo("Yazid Sánchez"));
        servidor.verify();
    }

    @Test
    void sinTrabajadoresNoConsultaUsers() {
        servidor.expect(requestTo(BASE + "/api/v1/workers?tradeId=" + id(1)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(cliente.buscarTrabajadoresActivos(id(1))).isEmpty();
        servidor.verify();
    }

    @Test
    void pideLosNombresEnBloquesDeCincuentaIds() {
        List<UUID> ids = IntStream.rangeClosed(1, 51).mapToObj(n -> id(1000 + n)).toList();
        String primerBloque = ids.subList(0, 50).stream().map(usuario -> "ids=" + usuario)
                .collect(Collectors.joining("&"));
        servidor.expect(requestTo(BASE + "/api/v1/users?" + primerBloque))
                .andRespond(withSuccess("""
                        [{"id": "%s", "name": "  Ana Pérez  "}, {"id": "%s", "name": " "}]
                        """.formatted(ids.get(0), ids.get(1)), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/api/v1/users?ids=" + ids.get(50)))
                .andRespond(withSuccess("""
                        [{"id": "%s", "name": "Luis Gómez"}]
                        """.formatted(ids.get(50)), MediaType.APPLICATION_JSON));

        Map<UUID, String> nombres = cliente.nombresDeUsuarios(ids);

        assertThat(nombres).containsOnly(Map.entry(ids.get(0), "Ana Pérez"), Map.entry(ids.get(50), "Luis Gómez"));
        servidor.verify();
    }

    @Test
    void consultaElCatalogoDeOficios() {
        servidor.expect(requestTo(BASE + "/api/v1/trades"))
                .andRespond(withSuccess("""
                        [{"id": "%s", "name": "Plomería", "active": true},
                         {"id": "%s", "name": "Oficio retirado", "active": false}]
                        """.formatted(id(1), id(9)), MediaType.APPLICATION_JSON));
        assertThat(cliente.obtenerOficios()).containsExactly(new OficioDTO(id(1), "Plomería", null));
    }

    @Test
    void entregaLaCategoriaDeCadaOficio() {
        servidor.expect(requestTo(BASE + "/api/v1/trades"))
                .andRespond(withSuccess("""
                        [{"id": "%s", "name": "Plomero", "active": true,
                          "category": {"id": "%s", "name": "Plomería y gas", "detail": "Tuberías"}},
                         {"id": "%s", "name": "Todero", "active": true}]
                        """.formatted(id(1), id(50), id(2)), MediaType.APPLICATION_JSON));
        assertThat(cliente.obtenerOficios()).containsExactly(
                new OficioDTO(id(1), "Plomero", null, new CategoriaDTO(id(50), "Plomería y gas")),
                new OficioDTO(id(2), "Todero", null));
    }

    @Test
    void consultaReputacionesYLasIndexaPorTrabajador() {
        servidor.expect(requestTo(BASE + "/api/v1/reputation/reputaciones?trabajadorIds=" + id(101) + "&trabajadorIds=" + id(102)))
                .andRespond(withSuccess("""
                        [{"trabajadorId": "00000000-0000-0000-0000-000000000101", "reputacion": 4.9, "totalResenas": 128},
                         {"trabajadorId": "00000000-0000-0000-0000-000000000102", "reputacion": 4.1, "totalResenas": 7}]
                        """, MediaType.APPLICATION_JSON));
        Map<UUID, ReputacionDTO> reputaciones = cliente.obtenerReputaciones(List.of(id(101), id(102)));
        assertThat(reputaciones).containsKeys(id(101), id(102));
        assertThat(reputaciones.get(id(101)).reputacion()).isEqualTo(4.9);
    }

    @Test
    void sinTrabajadoresNoConsultaReputaciones() {
        assertThat(cliente.obtenerReputaciones(List.of())).isEmpty();
        servidor.verify();
    }

    @Test
    void enviaElPromptALaIAPorElOrquestador() {
        servidor.expect(requestTo(BASE + "/api/ia/interpretaciones"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"respuesta\": \"{\\\"oficioId\\\": 1}\"}", MediaType.APPLICATION_JSON));
        assertThat(cliente.solicitarInterpretacionIA("prompt")).isEqualTo("{\"oficioId\": 1}");
    }

    @Test
    void respuestasVaciasSeConviertenEnListasVacias() {
        servidor.expect(requestTo(BASE + "/api/v1/workers?tradeId=" + id(1))).andRespond(withSuccess());
        servidor.expect(requestTo(BASE + "/api/v1/trades")).andRespond(withSuccess());
        servidor.expect(requestTo(BASE + "/api/v1/reputation/reputaciones?trabajadorIds=" + id(1))).andRespond(withSuccess());
        servidor.expect(requestTo(BASE + "/api/ia/interpretaciones")).andRespond(withSuccess());
        assertThat(cliente.buscarTrabajadoresActivos(id(1))).isEmpty();
        assertThat(cliente.obtenerOficios()).isEmpty();
        assertThat(cliente.obtenerReputaciones(List.of(id(1)))).isEmpty();
        assertThat(cliente.solicitarInterpretacionIA("p")).isEmpty();
    }

    @Test
    void losErroresDelOrquestadorSeConviertenEnIntegracionException() {
        servidor.expect(requestTo(BASE + "/api/v1/workers?tradeId=" + id(1))).andRespond(withServerError());
        servidor.expect(requestTo(BASE + "/api/v1/trades")).andRespond(withServerError());
        servidor.expect(requestTo(BASE + "/api/v1/reputation/reputaciones?trabajadorIds=" + id(1))).andRespond(withServerError());
        servidor.expect(requestTo(BASE + "/api/ia/interpretaciones")).andRespond(withServerError());
        UUID id1 = id(1);
        assertThatThrownBy(() -> cliente.buscarTrabajadoresActivos(id1)).isInstanceOf(IntegracionException.class);
        assertThatThrownBy(() -> cliente.obtenerOficios()).isInstanceOf(IntegracionException.class);
        List<UUID> ids1 = List.of(id(1));
        assertThatThrownBy(() -> cliente.obtenerReputaciones(ids1)).isInstanceOf(IntegracionException.class);
        assertThatThrownBy(() -> cliente.solicitarInterpretacionIA("p")).isInstanceOf(IntegracionException.class);
    }
}
