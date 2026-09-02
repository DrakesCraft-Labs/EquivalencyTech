package io.github.sefiraat.equivalencytech.runnables;

import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un cofre puede quedar en blockstore.yml con su fichero de dueños ya perdido: entonces
 * getOwnerDChest/getOwnerCChest devuelven null. Estas pruebas fijan por que el runnable
 * tiene que descartar ese cofre antes de tocar la configuracion.
 */
class OrphanOwnerWarningTest {

    @Test
    void buscarPorUnUuidNuloRevientaLaConfiguracion() {
        MemoryConfiguration config = new MemoryConfiguration();
        // Es la llamada exacta que hacen getPlayerEmc y getLearnedItems con el uuid del cofre.
        assertThrows(IllegalArgumentException.class, () -> config.contains(null));
    }

    @Test
    void elAvisoNombraElIdElFicheroYLaPosicion() {
        Location location = new Location(null, 117.0, 64.0, -2.0);
        String message = RunnableEQTick.getErrorOrphanOwner(
            "Dissolution", 8, location, "dissolution_chests.yml");

        assertTrue(message.contains("Dissolution"), message);
        assertTrue(message.contains("OWNING_PLAYER"), message);
        assertTrue(message.contains("dissolution_chests.yml"), message);
        assertTrue(message.contains(location.toString()), message);
    }
}
