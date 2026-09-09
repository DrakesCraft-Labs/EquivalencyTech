package io.github.sefiraat.equivalencytech.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ChestPlaceListenerPolicyTest {

    @Test
    void placementPersistsOnlyAfterEarlierListenersAcceptIt() throws NoSuchMethodException {
        EventHandler handler = ChestPlaceListener.class
            .getDeclaredMethod("onChestPlace", org.bukkit.event.block.BlockPlaceEvent.class)
            .getAnnotation(EventHandler.class);

        Assertions.assertNotNull(handler, "onChestPlace debe seguir registrado como listener");
        Assertions.assertEquals(EventPriority.MONITOR, handler.priority(),
            "la persistencia debe observar el resultado final de Slimefun y protecciones");
        Assertions.assertTrue(handler.ignoreCancelled(),
            "una colocacion cancelada no debe crear registros de cofres EMC");
    }
}
