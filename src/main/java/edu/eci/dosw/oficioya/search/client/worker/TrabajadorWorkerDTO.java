package edu.eci.dosw.oficioya.search.client.worker;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import edu.eci.dosw.oficioya.search.dto.DisponibilidadDTO;
import edu.eci.dosw.oficioya.search.dto.OficioOfrecidoDTO;
import edu.eci.dosw.oficioya.search.dto.TrabajadorDTO;

/**
 * Trabajador tal como lo entrega Worker (Squad 3) en {@code GET /api/v1/workers}. Se convierte al modelo de Search
 * con {@link #aTrabajador()}.
 * Worker no entrega el nombre (es de Users): lo completa {@link #aTrabajador(String)} con lo que responde Users.
 * PENDIENTE con otros dominios: la foto de perfil (Imágenes) y los trabajos completados (Service) quedan vacíos.
 * {@link #perfilCompleto(String)} mide qué tan lleno está el perfil de trabajo con lo que entrega Worker.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrabajadorWorkerDTO(UUID id, UUID userId, String status, boolean availableNow, Set<Long> zoneIds,
                                  OficioDelTrabajadorDTO primaryTrade, List<OficioDelTrabajadorDTO> secondaryTrades,
                                  List<FranjaWorkerDTO> slots, List<Object> photos) {

    /** Oficio de un trabajador con su tarifa. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OficioDelTrabajadorDTO(UUID id, BigDecimal approxRate, String rateUnit, OficioWorkerDTO trade) {
    }

    /** Franja de disponibilidad. Solo se usan las semanales (día de la semana con hora de inicio y fin). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FranjaWorkerDTO(String recurrenceType, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    public TrabajadorDTO aTrabajador() {
        return aTrabajador(null);
    }

    /**
     * @param nombreCompleto nombre de la cuenta en Users, o {@code null} si Users no lo entregó
     */
    public TrabajadorDTO aTrabajador(String nombreCompleto) {
        OficioWorkerDTO principal = primaryTrade == null ? null : primaryTrade.trade();
        List<OficioOfrecidoDTO> oficiosSecundarios = secondaryTrades == null ? List.of() : secondaryTrades.stream()
                .filter(s -> s.trade() != null && s.trade().id() != null)
                .map(s -> new OficioOfrecidoDTO(s.trade().id(), s.trade().name(), s.approxRate()))
                .toList();
        List<UUID> secundarios = oficiosSecundarios.stream().map(OficioOfrecidoDTO::id).toList();
        List<DisponibilidadDTO> disponibilidad = slots == null ? List.of() : slots.stream()
                .filter(f -> f.dayOfWeek() != null && f.startTime() != null && f.endTime() != null)
                .map(f -> new DisponibilidadDTO(f.dayOfWeek(), f.startTime(), f.endTime()))
                .toList();
        return new TrabajadorDTO(id, nombreCompleto, null,
                principal == null ? null : principal.id(), principal == null ? null : principal.name(),
                secundarios, zoneIds == null ? List.of() : List.copyOf(zoneIds), null,
                primaryTrade == null ? null : primaryTrade.approxRate(), availableNow, disponibilidad, null,
                oficiosSecundarios, perfilCompleto(nombreCompleto), userId);
    }

    /**
     * Porcentaje (0 a 100) del perfil de trabajo que está lleno. Cada parte vale lo mismo: oficio principal con
     * tarifa, nombre en Users, barrios donde trabaja, horario, fotos de trabajos realizados y oficios secundarios.
     */
    public int perfilCompleto(String nombreCompleto) {
        boolean[] partes = {
            primaryTrade != null && primaryTrade.trade() != null && primaryTrade.approxRate() != null,
            nombreCompleto != null && !nombreCompleto.isBlank(),
            zoneIds != null && !zoneIds.isEmpty(),
            slots != null && !slots.isEmpty(),
            photos != null && !photos.isEmpty(),
            secondaryTrades != null && !secondaryTrades.isEmpty(),
        };
        int llenas = 0;
        for (boolean parte : partes) {
            if (parte) {
                llenas++;
            }
        }
        return Math.round(llenas * 100f / partes.length);
    }
}
