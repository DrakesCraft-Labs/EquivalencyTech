package io.github.sefiraat.equivalencytech.configuration;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * block_storage.yml indexa por id, de modo que getAll*ChestLocations devuelve UNA posicion POR
 * ID y una posicion reclamada por N ids entra N veces en la vuelta del runnable. Como
 * get*ChestIdStore resuelve siempre al PRIMER id, el mismo cofre se procesaba N veces en el
 * mismo tick (N items consumidos por segundo y N acreditaciones de EMC). Estas pruebas fijan
 * los dos contratos en los que se apoya el saltarse las repeticiones dentro de la vuelta.
 */
class ChestPassDeduplicationTest {

    private static final String SECCION = ConfigMain.DIS_CHEST_CFG;

    @Test
    void dosIdsDeLaMismaPosicionColapsanEnUnSoloProcesado() {
        // Es el mecanismo exacto del arreglo: la posicion repetida no vuelve a entrar al Set.
        Set<Location> vistas = new HashSet<>();
        Location primera = new Location(null, -3989859.0, 91.0, 1230901.0);
        Location repetida = new Location(null, -3989859.0, 91.0, 1230901.0);
        Location otra = new Location(null, -3989860.0, 91.0, 1230901.0);

        assertTrue(vistas.add(primera));
        assertFalse(vistas.add(repetida), "la misma posicion no puede procesarse dos veces en la vuelta");
        assertTrue(vistas.add(otra), "una posicion distinta si tiene que procesarse");
        assertEquals(2, vistas.size());
    }

    @Test
    void elBarridoPorPosicionVeTodosLosIdsAunqueElPrimeroTengaDueno() {
        // Estas son las posiciones que el saneo perezoso nunca alcanzaba: solo se invocaba en la
        // rama sin OWNING_PLAYER, y aqui el id resuelto (el mas bajo) si tiene dueño.
        YamlConfiguration c = new YamlConfiguration();
        Location compartida = new Location(null, -3989859.0, 91.0, 1230901.0);
        for (int id : new int[] {137, 138, 139, 140, 141}) {
            c.set(SECCION + "." + id, compartida);
        }

        assertEquals(List.of(137, 138, 139, 140, 141), ConfigMain.collectIdsAt(c, SECCION, compartida));
    }

    @Test
    void laListaDePosicionesRepiteUnaEntradaPorIdDuplicado() {
        YamlConfiguration c = new YamlConfiguration();
        Location compartida = new Location(null, 10.0, 64.0, 20.0);
        c.set(SECCION + ".1", compartida);
        c.set(SECCION + ".2", compartida);
        c.set(SECCION + ".3", new Location(null, 11.0, 64.0, 20.0));

        // Sin mundo cargado la lista se filtra entera: es la guarda que ya existia para los
        // mundos que aun no estan montados, y por eso el conteo se comprueba sobre los ids.
        assertTrue(ConfigMain.collectLocations(c, SECCION).isEmpty());
        assertEquals(2, ConfigMain.collectIdsAt(c, SECCION, compartida).size());
    }
}
