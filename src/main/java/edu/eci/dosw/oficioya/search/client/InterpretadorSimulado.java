package edu.eci.dosw.oficioya.search.client;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import edu.eci.dosw.oficioya.search.domain.InterpretacionIA;
import edu.eci.dosw.oficioya.search.dto.OficioDTO;
import edu.eci.dosw.oficioya.search.service.InterpretadorLenguajeNatural;

/**
 * Intérprete por palabras, sin IA (modo {@code oficioya.ia.modo=simulado}, el modo por defecto).
 * <ol>
 *   <li>Busca en el texto el nombre de un oficio del catálogo de Worker ("peluquería canina",
 *       "busco un plomero"). Gana el oficio con más palabras de su nombre presentes en el texto.</li>
 *   <li>Si ninguno coincide, usa palabras asociadas a algunos oficios ("gotera" → plomería).</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "oficioya.ia.modo", havingValue = "simulado", matchIfMissing = true)
public class InterpretadorSimulado implements InterpretadorLenguajeNatural {

    static final double CONFIANZA_NOMBRE = 0.9;
    static final double CONFIANZA_NOMBRE_PARCIAL = 0.8;
    static final double CONFIANZA_PALABRA_CLAVE = 0.75;

    /** Palabras que no ayudan a distinguir un oficio. */
    private static final Set<String> SIN_SIGNIFICADO = Set.of("de", "del", "la", "las", "el", "los", "y", "a", "en",
            "para", "por", "un", "una", "unos", "unas", "con", "mi", "que", "al", "o");

    /** Raíz del nombre del oficio → palabras con las que la gente suele describir esa necesidad. */
    private static final Map<String, List<String>> PALABRAS_CLAVE = new LinkedHashMap<>();

    static {
        PALABRAS_CLAVE.put("plomer", List.of("gotera", "fuga", "tuberia", "tubo", "lavamanos", "inodoro", "sanitario",
                "ducha", "grifo", "desague", "lavaplatos", "plomero"));
        PALABRAS_CLAVE.put("electric", List.of("electricista", "luz", "enchufe", "toma", "cable", "corto", "breaker",
                "bombillo", "lampara", "interruptor"));
        PALABRAS_CLAVE.put("carpinter", List.of("carpintero", "mueble", "madera", "puerta", "closet", "armario",
                "repisa"));
        PALABRAS_CLAVE.put("profesor", List.of("profesor", "clase", "tarea", "matematicas", "ingles", "fisica",
                "quimica", "tutor"));
        PALABRAS_CLAVE.put("mascota", List.of("mascota", "perro", "gato", "pasear", "cuidador", "guarderia"));
        PALABRAS_CLAVE.put("tecnic", List.of("tecnico", "nevera", "lavadora", "televisor", "computador", "horno",
                "microondas", "electrodomestico"));
    }

    @Override
    public InterpretacionIA interpretar(String texto, List<OficioDTO> catalogo) {
        String normalizado = normalizar(texto);
        InterpretacionIA porNombre = porNombreDelOficio(palabras(normalizado), catalogo);
        return porNombre != null ? porNombre : porPalabrasClave(normalizado, catalogo);
    }

    /**
     * El oficio con la mayor parte de su nombre presente en el texto, o {@code null} si ninguno coincide o si
     * dos oficios coinciden por igual.
     */
    private static InterpretacionIA porNombreDelOficio(List<String> delTexto, List<OficioDTO> catalogo) {
        OficioDTO mejor = null;
        double mejorPuntaje = 0;
        boolean empate = false;
        for (OficioDTO oficio : catalogo) {
            if (oficio.nombre() == null) {
                continue;
            }
            double puntaje = puntaje(delTexto, palabras(normalizar(oficio.nombre())));
            if (puntaje > mejorPuntaje) {
                mejor = oficio;
                mejorPuntaje = puntaje;
                empate = false;
            } else if (puntaje > 0 && puntaje == mejorPuntaje) {
                empate = true;
            }
        }
        if (mejor == null || empate) {
            return null;
        }
        double confianza = mejorPuntaje == 1 ? CONFIANZA_NOMBRE : CONFIANZA_NOMBRE_PARCIAL;
        return InterpretacionIA.identificado(mejor.id(), mejor.nombre(), confianza);
    }

    /** Fracción de las palabras del nombre del oficio que aparecen en el texto. */
    private static double puntaje(List<String> delTexto, List<String> delOficio) {
        if (delOficio.isEmpty()) {
            return 0;
        }
        long presentes = delOficio.stream()
                .filter(o -> delTexto.stream().anyMatch(t -> mismaPalabra(t, o)))
                .count();
        return (double) presentes / delOficio.size();
    }

    /**
     * Dos palabras son la misma si comparten casi todo su inicio, para aceptar plural, género y variantes
     * ("perro"/"perros", "canino"/"canina", "plomero"/"plomería") sin confundir "electricidad" con
     * "electrodomésticos".
     */
    static boolean mismaPalabra(String a, String b) {
        int corta = Math.min(a.length(), b.length());
        if (corta < 4) {
            return a.equals(b);
        }
        int comun = 0;
        while (comun < corta && a.charAt(comun) == b.charAt(comun)) {
            comun++;
        }
        return comun >= Math.ceil(corta * 0.75);
    }

    private static InterpretacionIA porPalabrasClave(String normalizado, List<OficioDTO> catalogo) {
        Map<OficioDTO, Double> coincidencias = new LinkedHashMap<>();
        for (OficioDTO oficio : catalogo) {
            if (oficio.nombre() == null) {
                continue;
            }
            double confianza = confianzaPorPalabraClave(normalizado, normalizar(oficio.nombre()));
            if (confianza > 0) {
                coincidencias.put(oficio, confianza);
            }
        }
        if (coincidencias.size() != 1) {
            // Ningún oficio o varios a la vez: la descripción es ambigua.
            return InterpretacionIA.requiereMasDetalle();
        }
        Map.Entry<OficioDTO, Double> unica = coincidencias.entrySet().iterator().next();
        return InterpretacionIA.identificado(unica.getKey().id(), unica.getKey().nombre(), unica.getValue());
    }

    private static double confianzaPorPalabraClave(String texto, String nombreOficio) {
        Set<String> palabrasDelTexto = new LinkedHashSet<>(List.of(texto.split("\\W+")));
        for (Map.Entry<String, List<String>> entrada : PALABRAS_CLAVE.entrySet()) {
            String raiz = entrada.getKey();
            if (!nombreOficio.contains(raiz)) {
                continue;
            }
            if (entrada.getValue().stream().anyMatch(clave -> palabrasDelTexto.stream().anyMatch(p -> p.startsWith(clave)))) {
                return CONFIANZA_PALABRA_CLAVE;
            }
        }
        return 0;
    }

    /** Palabras con significado, ya normalizadas. */
    private static List<String> palabras(String normalizado) {
        return Arrays.stream(normalizado.split("[^\\p{L}\\p{N}]+"))
                .filter(p -> !p.isBlank() && !SIN_SIGNIFICADO.contains(p))
                .toList();
    }

    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}
