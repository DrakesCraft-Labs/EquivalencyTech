package io.github.sefiraat.equivalencytech.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BentoBox crea oneblock_world de forma asincrona: unas veces esta listo en la primera tick tras
 * onEnable y otras tarda minutos. Con un unico reintento, la segunda situacion dejaba el guardado
 * de block_storage.yml bloqueado toda la sesion y llenaba el log (1274 lineas ERROR el 06-sep).
 * Estas pruebas fijan que se reintenta hasta cubrir el arranque y que el aviso lleva freno.
 */
class BlockStoreReloadPolicyTest {

    @Test
    void soloElPrimerIntentoSeAnunciaEnElLog() {
        assertTrue(ConfigMain.isFirstBlockStoreReload(0));
        assertFalse(ConfigMain.isFirstBlockStoreReload(1));
        assertFalse(ConfigMain.isFirstBlockStoreReload(30));
    }

    @Test
    void seSigueReintentandoDespuesDeLaPrimeraTick() {
        // El fallo original: tras el intento 1 se rendia y bloqueaba el guardado.
        assertTrue(ConfigMain.shouldRetryBlockStoreReload(1));
        assertTrue(ConfigMain.shouldRetryBlockStoreReload(30));
    }

    @Test
    void losReintentosSonFinitos() {
        assertFalse(ConfigMain.shouldRetryBlockStoreReload(31));
        assertFalse(ConfigMain.shouldRetryBlockStoreReload(100));
    }

    @Test
    void elAvisoDeGuardadoBloqueadoSaleUnaVezYLuegoSeFrena() {
        // Primer guardado de la sesion: nunca se ha avisado, sale pase lo que pase con el reloj.
        assertTrue(ConfigMain.shouldWarnBlockStoreSave(1_000L, 0L));
        // Autosaves siguientes dentro de la ventana: callados.
        assertFalse(ConfigMain.shouldWarnBlockStoreSave(1_000L, 1_000L));
        assertFalse(ConfigMain.shouldWarnBlockStoreSave(300_999L, 1_000L));
        // Pasada la ventana vuelve a recordarlo.
        assertTrue(ConfigMain.shouldWarnBlockStoreSave(301_000L, 1_000L));
    }
}
