package io.github.sefiraat.equivalencytech.runnables;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.configuration.ConfigMain;
import io.github.sefiraat.equivalencytech.misc.Utils;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunnableEQTick extends BukkitRunnable {

    public final EquivalencyTech plugin;
    public final boolean sf;

    // Este runnable corre cada 20 ticks (1 vez por segundo) en el hilo principal. El aviso de
    // cofre huerfano se emitia en CADA pasada por cada cofre cuyo chunk estuviera cargado: los
    // 8 cofres huerfanos de laboratorio produjeron 368 lineas en 50 s, ~28.800 por hora mientras
    // esos chunks siguieran cargados. Se recuerda que ID ya fue avisado y se vuelve a permitir el
    // aviso solo cuando el cofre se restaura, para no perder la senal si el problema reaparece.
    private final Set<Integer> warnedDChests = new HashSet<>();
    private final Set<Integer> warnedCChests = new HashSet<>();

    // getOwnerDChest/getOwnerCChest leen OWNING_PLAYER del yml de cofres y devuelven null cuando
    // ese registro se perdio pero la posicion sigue en blockstore.yml (la misma desincronizacion
    // que ya obligo a filtrar los mundos que cargan tarde). Con el uuid a null, getLearnedItems y
    // getPlayerEmc acaban llamando a FileConfiguration.contains(null), que lanza IllegalArgumentException:
    // al ocurrir dentro de este runnable de 20 ticks, se repetiria cada segundo en el hilo principal.
    private final Set<Integer> warnedDOwners = new HashSet<>();
    private final Set<Integer> warnedCOwners = new HashSet<>();

    public RunnableEQTick(EquivalencyTech plugin) {
        this.plugin = plugin;
        sf = EquivalencyTech.getInstance().getManagerSupportedPlugins().isInstalledSlimefun();
    }

    @Override
    public void run() {
        processDChests();
        processCChests();
    }

    private void processDChests() {
        // getAllDChestLocations devuelve UNA posicion POR ID, asi que una posicion reclamada por
        // varios ids entra varias veces en esta vuelta; como getDChestIdStore siempre resuelve al
        // PRIMER id, el mismo cofre se procesaba una vez por duplicado en el mismo tick (un cofre
        // con 5 ids consumia 5 items por segundo y acreditaba 5 veces su EMC). La repeticion
        // dentro de la propia vuelta es la senal de duplicado, y ademas alcanza los casos que el
        // saneo perezoso no veia: colapsarDuplicados solo se invocaba cuando el id resuelto no
        // tenia dueño, y en estas posiciones el primer id si lo tiene.
        Set<Location> vistas = new HashSet<>();
        for (Location location : ConfigMain.getAllDChestLocations(plugin)) {
            if (Utils.isBlockedWorld(plugin, location.getWorld())) {
                continue;
            }
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                if (!vistas.add(location)) {
                    colapsarDuplicados(ConfigMain.getAllDChestIdsStore(plugin, location), location, true);
                    continue;
                }
                Integer storedId = ConfigMain.getDChestIdStore(plugin, location);
                if (storedId == null) {
                    continue;
                }
                int chestId = storedId;
                String playerUUID = ConfigMain.getOwnerDChest(plugin, chestId);
                if (playerUUID == null) {
                    if (colapsarDuplicados(ConfigMain.getAllDChestIdsStore(plugin, location), location, true)) {
                        continue;
                    }
                    if (warnedDOwners.add(chestId)) {
                        EquivalencyTech.getInstance().getLogger()
                            .warning(getErrorOrphanOwner("Dissolution", chestId, location, "dissolution_chests.yml"));
                    }
                    continue;
                }
                warnedDOwners.remove(chestId);

                BlockState state = location.getBlock().getState();

                if (!(state instanceof Chest)) {
                    if (warnedDChests.add(chestId)) {
                        EquivalencyTech.getInstance().getLogger().warning(getErrorDissolutionChest(chestId, location));
                    }
                    continue;
                }

                warnedDChests.remove(chestId);

                Chest chest = (Chest) location.getBlock().getState();
                Inventory inventory = chest.getBlockInventory();
                ItemStack[] contents = inventory.getContents();
                for (int slot = 0; slot < contents.length; slot++) {
                    ItemStack itemStack = contents[slot];
                    if (itemStack != null && itemStack.getType() != Material.AIR) {
                        boolean isEQ = ContainerStorage.isCraftable(itemStack, plugin);
                        SlimefunItem sfItem = null;
                        if (sf) {
                            sfItem = SlimefunItem.getByItem(itemStack);
                        }
                        Material material = itemStack.getType();
                        // getEMC devuelve el valor de un mapa, asi que es null para cualquier objeto
                        // sin EMC definido. Antes se dividia entre 100 sin comprobarlo: el
                        // desempaquetado del Double lanzaba NullPointerException y, como esto corre en
                        // cada tick sobre el contenido de los cofres, el fallo se repetia sin parar --
                        // 12.867 veces en un solo arranque, en el hilo principal. El guard de abajo
                        // llegaba tarde: la excepcion saltaba antes, al hacer la cuenta.
                        Double emcBase = Utils.getEMC(plugin, itemStack);
                        if (emcBase == null) {
                            continue;
                        }
                        Double emcValue = Utils.roundDown((emcBase / 100) * 150, 2);
                        if (emcValue != null && Utils.canBeSynth(plugin, itemStack)) {
                            String entryName;
                            if (isEQ) {
                                entryName = Utils.eqNameConfig(itemStack.getItemMeta().getDisplayName());
                            } else if (sfItem != null) {
                                entryName = sfItem.getId();
                            } else {
                                entryName = material.toString();
                            }
                            if (!ConfigMain.getLearnedItems(plugin, playerUUID).contains(entryName)) {
                                ConfigMain.addLearnedItem(plugin, playerUUID, entryName);
                            }
                            ConfigMain.addPlayerEmc(plugin, playerUUID, emcValue);
                            if (itemStack.getAmount() <= 1) {
                                inventory.setItem(slot, null);
                            } else {
                                itemStack.setAmount(itemStack.getAmount() - 1);
                                inventory.setItem(slot, itemStack);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private void processCChests() {
        // Misma duplicidad que en processDChests: una posicion con varios ids entregaba su item
        // una vez por id en el mismo tick, cobrando el EMC otras tantas veces.
        Set<Location> vistas = new HashSet<>();
        for (Location location : ConfigMain.getAllCChestLocations(plugin)) {
            if (Utils.isBlockedWorld(plugin, location.getWorld())) {
                continue;
            }
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                if (!vistas.add(location)) {
                    colapsarDuplicados(ConfigMain.getAllCChestIdsStore(plugin, location), location, false);
                    continue;
                }
                Integer storedId = ConfigMain.getCChestIdStore(plugin, location);
                if (storedId == null) {
                    continue;
                }
                int chestId = storedId;
                String playerUUID = ConfigMain.getOwnerCChest(plugin, chestId);
                if (playerUUID == null) {
                    if (colapsarDuplicados(ConfigMain.getAllCChestIdsStore(plugin, location), location, false)) {
                        continue;
                    }
                    if (warnedCOwners.add(chestId)) {
                        EquivalencyTech.getInstance().getLogger()
                            .warning(getErrorOrphanOwner("Condensate", chestId, location, "condensate_chests.yml"));
                    }
                    continue;
                }
                warnedCOwners.remove(chestId);

                BlockState state = location.getBlock().getState();

                if (!(state instanceof Chest)) {
                    if (warnedCChests.add(chestId)) {
                        EquivalencyTech.getInstance().getLogger().warning(getErrorCondensateChest(chestId, location));
                    }
                    continue;
                }

                warnedCChests.remove(chestId);

                Chest chest = (Chest) location.getBlock().getState();
                Inventory inventory = chest.getBlockInventory();
                ItemStack itemStack = ConfigMain.getCChestItem(plugin, chestId);
                if (itemStack != null) {
                    Double emcValue = Utils.getEMC(plugin, itemStack);
                    if (emcValue != null) {
                        Double playerEmc = ConfigMain.getPlayerEmc(plugin, playerUUID);
                        if (playerEmc >= emcValue) {
                            HashMap<Integer, ItemStack> failed = inventory.addItem(itemStack);
                            if (failed.isEmpty()) {
                                ConfigMain.removePlayerEmc(plugin, playerUUID, emcValue);
                            }
                        }
                    }
                }
            }
        }
    }

    // Saneo de los registros que ya estaban duplicados en disco antes del fix del alta.
    // Una posicion solo puede pertenecer a un cofre: si varios ids la reclaman, el bueno es el
    // que conserva OWNING_PLAYER y el resto son fantasmas nacidos de la escritura del dueño en
    // el id equivocado. Se borran SOLO los sobrantes y nunca el ultimo registro que queda, para
    // que un cofre real jamas pierda su entrada. Devuelve true si purgo algo, para no avisar de
    // un id que acaba de desaparecer.
    private boolean colapsarDuplicados(List<Integer> ids, Location location, boolean dissolution) {
        if (ids.size() < 2) {
            return false;
        }
        Integer conservado = null;
        for (Integer id : ids) {
            String owner = dissolution
                ? ConfigMain.getOwnerDChest(plugin, id)
                : ConfigMain.getOwnerCChest(plugin, id);
            if (owner != null) {
                conservado = id;
                break;
            }
        }
        if (conservado == null) {
            // Ninguno tiene dueño: se conserva el mas bajo para que el aviso siga siendo visible
            // una sola vez y no se pierda la senal de un cofre realmente sin propietario.
            conservado = ids.get(0);
        }
        List<Integer> purgados = new ArrayList<>();
        for (Integer id : ids) {
            if (id.equals(conservado)) {
                continue;
            }
            if (dissolution) {
                ConfigMain.removeDChestStore(plugin, id);
                ConfigMain.removeDChest(plugin, id);
                warnedDOwners.remove(id);
                warnedDChests.remove(id);
            } else {
                ConfigMain.removeCChestStore(plugin, id);
                ConfigMain.removeCChest(plugin, id);
                warnedCOwners.remove(id);
                warnedCChests.remove(id);
            }
            purgados.add(id);
        }
        if (purgados.isEmpty()) {
            return false;
        }
        EquivalencyTech.getInstance().getLogger().info(
            "Registros duplicados de cofre EMC eliminados (" + (dissolution ? "dissolution" : "condensate")
                + "=" + purgados + ", conservado=" + conservado + ") : la posicion " + location
                + " solo puede pertenecer a un cofre.");
        return true;
    }

    public static String getErrorDissolutionChest(int chestId, Location location) {
        return MessageFormat.format(
            "A Dissolution chest (ID: {0}) has been removed wrongly. " +
                "Either replace with a vanilla chest (location : {1}) " +
                "or remove from dissolution_chests.yml",
            chestId,
            location.toString()
        );
    }

    public static String getErrorOrphanOwner(String kind, int chestId, Location location, String file) {
        return MessageFormat.format(
            "A {0} chest (ID: {1}) has no OWNING_PLAYER recorded and cannot be processed. " +
                "Either restore its owner in {2} or remove the entry (location : {3})",
            kind,
            chestId,
            file,
            location.toString()
        );
    }

    public static String getErrorCondensateChest(int chestId, Location location) {
        return MessageFormat.format(
            "A Condensate chest (ID: {0}) has been removed wrongly. " +
                "Either replace with a vanilla chest (location : {1})  " +
                "or remove from condensate_chests.yml",
            chestId,
            location.toString()
        );
    }
}