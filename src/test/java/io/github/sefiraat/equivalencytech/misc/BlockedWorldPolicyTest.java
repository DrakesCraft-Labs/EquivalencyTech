package io.github.sefiraat.equivalencytech.misc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class BlockedWorldPolicyTest {

    @Test
    void testBlockedWorldMatchesCaseInsensitively() {
        List<String> blocked = List.of("laboratorio");

        Assertions.assertTrue(Utils.isBlockedWorld(blocked, "laboratorio"), "laboratorio debe considerarse bloqueado");
        Assertions.assertTrue(Utils.isBlockedWorld(blocked, "LABORATORIO"), "LABORATORIO en mayusculas debe considerarse bloqueado");
        Assertions.assertFalse(Utils.isBlockedWorld(blocked, "world"), "world ordinario no debe estar bloqueado");
        Assertions.assertFalse(Utils.isBlockedWorld(blocked, "bskyblock_world"), "bskyblock_world no debe estar bloqueado");
    }

    @Test
    void testNullSafe() {
        List<String> blocked = List.of("laboratorio");
        Assertions.assertFalse(Utils.isBlockedWorld(null, "laboratorio"), "lista nula no debe lanzar excepcion");
        Assertions.assertFalse(Utils.isBlockedWorld(blocked, (String) null), "mundo nulo no debe lanzar excepcion");
        Assertions.assertFalse(Utils.isBlockedWorld(null, (org.bukkit.World) null), "plugin y mundo nulos no deben lanzar excepcion");
    }
}
