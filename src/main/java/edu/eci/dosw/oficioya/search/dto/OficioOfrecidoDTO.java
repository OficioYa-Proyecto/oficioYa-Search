package edu.eci.dosw.oficioya.search.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Oficio secundario que ofrece un trabajador, con su propia tarifa aproximada.
 *
 * @param id     id del oficio en Worker
 * @param nombre nombre del oficio
 * @param tarifa tarifa aproximada del trabajador para este oficio, o {@code null} si no la indicó
 */
public record OficioOfrecidoDTO(UUID id, String nombre, BigDecimal tarifa) {
}
