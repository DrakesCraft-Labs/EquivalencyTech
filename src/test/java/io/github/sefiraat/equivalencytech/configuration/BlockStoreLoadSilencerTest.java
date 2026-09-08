package io.github.sefiraat.equivalencytech.configuration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bukkit escribe el ERROR de block_storage.yml por su cuenta desde
 * {@code ConfigurationSerialization}, con el metodo fallido en el mensaje y la
 * IllegalArgumentException ya desenvuelta como {@code thrown}. El filtro solo puede descartar ese
 * caso concreto: cualquier otro fallo de deserializacion tiene que seguir llegando al log.
 */
class BlockStoreLoadSilencerTest {

    private static final String MENSAJE_REAL =
        "Could not call method 'public static org.bukkit.Location"
        + " org.bukkit.Location.deserialize(java.util.Map)' of class org.bukkit.Location"
        + " for deserialization";

    @Test
    void descartaSoloLaLocationDeUnMundoQueAunNoExiste() {
        assertTrue(BlockStoreLoadSilencer.isUnknownWorldLocation(
            MENSAJE_REAL, new IllegalArgumentException("unknown world")));
    }

    @Test
    void mantieneOtrasCausasDelMismoMetodo() {
        assertFalse(BlockStoreLoadSilencer.isUnknownWorldLocation(
            MENSAJE_REAL, new IllegalArgumentException("cannot deserialize x")));
        assertFalse(BlockStoreLoadSilencer.isUnknownWorldLocation(MENSAJE_REAL, null));
    }

    @Test
    void mantieneOtraClaseAunqueLaCausaSeaLaMisma() {
        assertFalse(BlockStoreLoadSilencer.isUnknownWorldLocation(
            "Could not call method 'org.bukkit.util.Vector.deserialize(java.util.Map)'",
            new IllegalArgumentException("unknown world")));
        assertFalse(BlockStoreLoadSilencer.isUnknownWorldLocation(
            null, new IllegalArgumentException("unknown world")));
    }

    @Test
    void desenvuelveLaCausaCuandoVieneAnidada() {
        assertTrue(BlockStoreLoadSilencer.isUnknownWorldLocation(
            MENSAJE_REAL,
            new InvocationTargetException(new IllegalArgumentException("unknown world"))));
    }

    @Test
    void noSeCuelgaConUnaCadenaDeCausasCiclada() {
        Throwable a = new RuntimeException("a");
        Throwable b = new RuntimeException("b", a);
        a.initCause(b);
        assertFalse(BlockStoreLoadSilencer.isUnknownWorldLocation(MENSAJE_REAL, b));
    }

    @Test
    void elAvisoDeclaraCuantasTrazasSeDescartaron() {
        assertEquals("", ConfigMain.describeSilencedErrors(0));
        assertEquals("", ConfigMain.describeSilencedErrors(-1));
        assertTrue(ConfigMain.describeSilencedErrors(9).contains("9"));
    }
}
