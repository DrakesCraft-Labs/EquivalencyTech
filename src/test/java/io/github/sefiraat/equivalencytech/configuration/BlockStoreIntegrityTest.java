package io.github.sefiraat.equivalencytech.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStoreIntegrityTest {

    private static final String SAMPLE = String.join("\n",
        "DIS_CHESTS:",
        "  '1':",
        "    ==: org.bukkit.Location",
        "    world: world",
        "    x: 10.0",
        "    y: 64.0",
        "    z: 20.0",
        "  '2':",
        "    ==: org.bukkit.Location",
        "    world: oneblock_world",
        "    x: 11.0",
        "    y: 64.0",
        "    z: 21.0",
        "CON_CHESTS:",
        "  '1':",
        "    ==: org.bukkit.Location",
        "    world: 'laboratorio'",
        "    x: 12.0",
        "    y: 64.0",
        "    z: 22.0",
        ""
    );

    @Test
    void cuentaTodasLasPosicionesEscritasAunqueSuMundoNoExista(@TempDir Path dir) throws IOException {
        File file = write(dir, SAMPLE);
        assertEquals(3, BlockStoreIntegrity.countSerialisedLocations(file));
    }

    @Test
    void enumeraLosMundosSinRepetirYSinComillas(@TempDir Path dir) throws IOException {
        File file = write(dir, SAMPLE);
        assertEquals(List.of("world", "oneblock_world", "laboratorio"),
            BlockStoreIntegrity.referencedWorlds(file));
    }

    @Test
    void ficheroVacioNoDeclaraPosiciones(@TempDir Path dir) throws IOException {
        File file = write(dir, "");
        assertEquals(0, BlockStoreIntegrity.countSerialisedLocations(file));
        assertTrue(BlockStoreIntegrity.referencedWorlds(file).isEmpty());
    }

    @Test
    void ficheroIlegibleNoBloqueaElGuardado(@TempDir Path dir) {
        File missing = dir.resolve("no-existe.yml").toFile();
        // -1 significa "no se pudo comparar": ConfigMain no marca degradado en ese caso.
        assertEquals(-1, BlockStoreIntegrity.countSerialisedLocations(missing));
        assertTrue(BlockStoreIntegrity.referencedWorlds(missing).isEmpty());
    }

    private File write(Path dir, String content) throws IOException {
        Path file = dir.resolve("block_storage.yml");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file.toFile();
    }
}
