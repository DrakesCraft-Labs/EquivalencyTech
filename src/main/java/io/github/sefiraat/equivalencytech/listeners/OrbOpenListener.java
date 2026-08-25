package io.github.sefiraat.equivalencytech.listeners;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.gui.GuiTransmutationOrb;
import io.github.sefiraat.equivalencytech.statics.ContainerStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;

public class OrbOpenListener implements Listener {

    private final EquivalencyTech plugin;

    public OrbOpenListener(@Nonnull EquivalencyTech plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().getItemMeta() != null && (e.getAction().equals(Action.RIGHT_CLICK_AIR) || e.getAction().equals(Action.RIGHT_CLICK_BLOCK))) {
            Player player = e.getPlayer();
            ItemStack i = e.getItem();
            if (ContainerStorage.isTransmutationOrb(i, plugin)) {
                e.setCancelled(true);

                // El EMC y los items aprendidos se guardan por jugador y valen en todas las
                // modalidades. En un mundo donde los items se consiguen gratis, el orbe los
                // convierte en EMC que despues se gasta en la modalidad normal, asi que ahi no
                // se abre. La lista vive en config porque manana puede haber otro mundo
                // creativo y no deberia hacer falta recompilar para cubrirlo.
                if (plugin.getConfig().getStringList("BLOCKED_WORLDS")
                        .contains(player.getWorld().getName())) {
                    String aviso = plugin.getConfig().getString("MESSAGES.ORB_WORLD_BLOCKED");
                    if (aviso != null && !aviso.isBlank()) {
                        player.sendMessage(aviso);
                    }
                    return;
                }

                GuiTransmutationOrb gui = GuiTransmutationOrb.buildGui(plugin, player);
                gui.open(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiTransmutationOrb gui) {
            gui.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiTransmutationOrb
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

}
