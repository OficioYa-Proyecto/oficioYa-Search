package edu.eci.dosw.oficioya.search.domain;

import java.util.UUID;

/**
 * Interpretación de la descripción en lenguaje natural.
 * La IA solo identifica el oficio (decisión de la profesora); no extrae zona, precio ni disponibilidad.
 *
 * @param oficioIdentificado nombre del oficio identificado, o {@code null}
 * @param oficioId           identificador del oficio en el catálogo de Worker, o {@code null}
 * @param nivelConfianza     confianza entre 0 y 1
 * @param estado             resultado de la interpretación
 * @param mensajeAclaracion  mensaje para pedir más detalle al contratante cuando no se identificó el oficio
 */
public record InterpretacionIA(String oficioIdentificado, UUID oficioId, double nivelConfianza,
                               EstadoInterpretacion estado, String mensajeAclaracion) {

    public static final String MENSAJE_MAS_DETALLE =
            "No encontramos profesionales que coincidan con tu búsqueda. Prueba con el nombre de un oficio "
                    + "o de un trabajador, o elige una categoría.";

    public static InterpretacionIA identificado(UUID oficioId, String oficio, double nivelConfianza) {
        return new InterpretacionIA(oficio, oficioId, nivelConfianza, EstadoInterpretacion.OFICIO_IDENTIFICADO, null);
    }

    public static InterpretacionIA requiereMasDetalle() {
        return new InterpretacionIA(null, null, 0, EstadoInterpretacion.REQUIERE_MAS_DETALLE, MENSAJE_MAS_DETALLE);
    }

    public static InterpretacionIA error() {
        return new InterpretacionIA(null, null, 0, EstadoInterpretacion.ERROR, MENSAJE_MAS_DETALLE);
    }

    /**
     * Indica si la interpretación permite ejecutar la búsqueda.
     */
    public boolean esConfiable(double umbral) {
        return estado == EstadoInterpretacion.OFICIO_IDENTIFICADO && oficioId != null && nivelConfianza >= umbral;
    }
}
