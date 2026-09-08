package io.github.sefiraat.equivalencytech.configuration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Silencia las trazas que Bukkit escribe por su cuenta al cargar block_storage.yml.
 *
 * <p>Cuando una Location apunta a un mundo que todavia no existe (los de BentoBox se crean despues
 * de nuestro onEnable), {@code ConfigurationSerialization} registra un ERROR con la pila completa
 * por cada posicion y devuelve null. Eso son nueve trazas identicas en cada arranque para una
 * situacion que el reintento de {@link ConfigMain} resuelve solo, y que no se puede evitar desde el
 * plugin porque el mensaje no lo emite el plugin.</p>
 *
 * <p>Aqui se instala un filtro en ese logger concreto y solo mientras dura nuestra lectura, para
 * descartar exclusivamente esos registros. Cualquier otro error de deserializacion, incluido el de
 * otra clase o el de otra causa, sigue saliendo en el log.</p>
 */
final class BlockStoreLoadSilencer {

    static final String BUKKIT_SERIALIZATION_LOGGER =
        "org.bukkit.configuration.serialization.ConfigurationSerialization";

    /** Fragmento del mensaje de Bukkit: el metodo cuya llamada fallo. */
    static final String LOCATION_DESERIALIZE = "org.bukkit.Location.deserialize";

    /** Mensaje de la IllegalArgumentException que lanza Location.deserialize sin mundo. */
    static final String UNKNOWN_WORLD = "unknown world";

    private BlockStoreLoadSilencer() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * @return true solo para el ERROR de Bukkit por una Location cuyo mundo aun no esta cargado.
     */
    static boolean isUnknownWorldLocation(String message, Throwable thrown) {
        if (message == null || !message.contains(LOCATION_DESERIALIZE)) {
            return false;
        }
        Throwable cause = thrown;
        // La causa puede venir envuelta; se recorre la cadena con tope por si esta ciclada.
        for (int depth = 0; cause != null && depth < 10; depth++) {
            if (cause instanceof IllegalArgumentException
                    && cause.getMessage() != null
                    && cause.getMessage().contains(UNKNOWN_WORLD)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Ejecuta la lectura con el filtro puesto y lo retira siempre al terminar.
     *
     * @return cuantas trazas se descartaron, 0 si el filtro no se pudo instalar.
     */
    static int runSilenced(Runnable load) {
        Logger logger = Logger.getLogger(BUKKIT_SERIALIZATION_LOGGER);
        Filter previous;
        AtomicInteger suppressed = new AtomicInteger();
        try {
            previous = logger.getFilter();
            logger.setFilter(buildFilter(previous, suppressed, Thread.currentThread()));
        } catch (RuntimeException e) {
            // Si el logger no admite filtros (otra implementacion de LogManager) se lee igual:
            // el ruido es preferible a no cargar el fichero.
            load.run();
            return 0;
        }
        try {
            load.run();
        } finally {
            restore(logger, previous);
        }
        return suppressed.get();
    }

    private static Filter buildFilter(Filter previous, AtomicInteger suppressed, Thread owner) {
        return (LogRecord record) -> {
            // El filtro vive en un logger compartido: solo se descarta lo que emite el hilo que
            // esta leyendo block_storage.yml, para no tragar el error de otro plugin que
            // deserialice a la vez.
            if (Thread.currentThread() == owner
                    && isUnknownWorldLocation(record.getMessage(), record.getThrown())) {
                suppressed.incrementAndGet();
                return false;
            }
            return previous == null || previous.isLoggable(record);
        };
    }

    private static void restore(Logger logger, Filter previous) {
        try {
            logger.setFilter(previous);
        } catch (RuntimeException e) {
            // Nada que hacer: dejar el filtro puesto seria peor, pero tampoco se puede quitar.
        }
    }
}
