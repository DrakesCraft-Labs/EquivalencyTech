package io.github.sefiraat.equivalencytech.configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lectura en crudo de block_storage.yml.
 *
 * <p>Bukkit deserializa las Location al cargar el YAML y descarta en silencio las que apuntan a un
 * mundo que aun no esta cargado, asi que el arbol en memoria no sirve para saber cuantas habia en
 * disco. Estas utilidades leen el fichero como texto para poder comparar.</p>
 */
final class BlockStoreIntegrity {

    static final String LOCATION_TAG = "==: org.bukkit.Location";

    private BlockStoreIntegrity() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * @return cuantas Location serializadas contiene el fichero, o -1 si no se pudo leer.
     */
    static int countSerialisedLocations(File file) {
        int count = 0;
        try (BufferedReader reader = newReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals(LOCATION_TAG)) {
                    count++;
                }
            }
        } catch (IOException e) {
            return -1;
        }
        return count;
    }

    /**
     * @return los mundos citados por el fichero, sin repetir y en orden de aparicion.
     */
    static List<String> referencedWorlds(File file) {
        Set<String> worlds = new LinkedHashSet<>();
        try (BufferedReader reader = newReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("world:")) {
                    String world = unquote(trimmed.substring("world:".length()).trim());
                    if (!world.isEmpty()) {
                        worlds.add(world);
                    }
                }
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }
        return new ArrayList<>(worlds);
    }

    private static BufferedReader newReader(File file) throws IOException {
        return Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && (value.charAt(0) == '\'' || value.charAt(0) == '"')
                && value.charAt(value.length() - 1) == value.charAt(0)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
