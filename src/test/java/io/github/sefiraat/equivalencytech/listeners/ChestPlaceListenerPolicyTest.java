package io.github.sefiraat.equivalencytech.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ChestPlaceListenerPolicyTest {

    @Test
    void placementPersistsOnlyAfterEarlierListenersAcceptIt() throws NoSuchMethodException {
        EventHandler validationHandler = ChestPlaceListener.class
            .getDeclaredMethod("onChestPlace", org.bukkit.event.block.BlockPlaceEvent.class)
            .getAnnotation(EventHandler.class);
        EventHandler persistenceHandler = ChestPlaceListener.class
            .getDeclaredMethod("onChestPlaceAccepted", org.bukkit.event.block.BlockPlaceEvent.class)
            .getAnnotation(EventHandler.class);

        Assertions.assertNotNull(validationHandler, "la validacion debe seguir registrada");
        Assertions.assertEquals(EventPriority.HIGHEST, validationHandler.priority(),
            "la validacion debe ejecutarse despues de las protecciones ordinarias");
        Assertions.assertTrue(validationHandler.ignoreCancelled(),
            "la validacion no debe revivir una colocacion ya cancelada");
        Assertions.assertNotNull(persistenceHandler, "la persistencia debe estar registrada");
        Assertions.assertEquals(EventPriority.MONITOR, persistenceHandler.priority(),
            "la persistencia debe observar el resultado final de Slimefun y protecciones");
        Assertions.assertTrue(persistenceHandler.ignoreCancelled(),
            "una colocacion cancelada no debe crear registros de cofres EMC");
    }
}
