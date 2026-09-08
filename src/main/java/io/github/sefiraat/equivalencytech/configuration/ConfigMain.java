package io.github.sefiraat.equivalencytech.configuration;

import io.github.sefiraat.equivalencytech.EquivalencyTech;
import io.github.sefiraat.equivalencytech.misc.Utils;
import io.github.sefiraat.equivalencytech.statics.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConfigMain {

    private final EquivalencyTech plugin;

    private final ConfigStrings strings;
    private final ConfigEMC emc;
    private final ConfigBooleans bools;

    private File learnedItemsConfigFile;
    private FileConfiguration learnedItemsConfig;
    private File playerEMCConfigFile;
    private FileConfiguration playerEMCConfig;
    private File blockStoreConfigFile;
    private FileConfiguration blockStoreConfig;
    private File dChestConfigFile;
    private FileConfiguration dChestConfig;
    private File cChestConfigFile;
    private FileConfiguration cChestConfig;

    /**
     * Cuantas veces se relee block_storage.yml esperando a que aparezcan los mundos que faltan.
     * La primera corre en la tick siguiente y el resto cada {@link #BLOCK_STORE_RELOAD_PERIOD_TICKS},
     * asi que cubren unos cinco minutos desde el arranque.
     */
    private static final int BLOCK_STORE_RELOAD_ATTEMPTS = 31;
    private static final long BLOCK_STORE_RELOAD_PERIOD_TICKS = 200L;

    /** Cada cuanto se repite el aviso de guardado bloqueado, para no llenar el log. */
    private static final long BLOCK_STORE_SAVE_WARNING_INTERVAL_MS = 300_000L;

    private boolean blockStoreDegraded;
    private int blockStoreSilencedErrors;
    private int blockStoreExpectedLocations;
    private int blockStoreReloadAttempts;
    private boolean blockStoreReloadPending;
    private long blockStoreSaveWarnedAt;

    public ConfigStrings getStrings() {
        return strings;
    }
    public ConfigEMC getEmc() {
        return emc;
    }
    public ConfigBooleans getBools() {
        return bools;
    }

    public File getLearnedItemsConfigFile() {
        return learnedItemsConfigFile;
    }

    public FileConfiguration getLearnedItemsConfig() {
        return learnedItemsConfig;
    }

    public File getPlayerEMCConfigFile() {
        return playerEMCConfigFile;
    }

    public FileConfiguration getPlayerEMCConfig() {
        return playerEMCConfig;
    }

    public File getBlockStoreConfigFile() {
        return blockStoreConfigFile;
    }

    public FileConfiguration getBlockStoreConfig() {
        return blockStoreConfig;
    }

    public File getDChestConfigFile() {
        return dChestConfigFile;
    }

    public FileConfiguration getDChestConfig() {
        return dChestConfig;
    }

    public File getCChestConfigFile() {
        return cChestConfigFile;
    }

    public FileConfiguration getCChestConfig() {
        return cChestConfig;
    }

    public static final String DIS_CHEST_CFG = "DIS_CHESTS";
    public static final String CON_CHEST_CFG = "CON_CHESTS";

    public ConfigMain(EquivalencyTech plugin) {

        this.plugin = plugin;

        strings = new ConfigStrings(plugin);
        emc = new ConfigEMC(plugin);
        bools = new ConfigBooleans(plugin);

        sortConfigs();
    }

    private void sortConfigs() {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        createAdditionalConfigs();
    }

    private void createAdditionalConfigs() {
        createLearnedConfig();
        createEmcConfig();
        createBlockStoreConfig();
        createDChestConfig();
        createCChestConfig();
    }

    public void saveAdditionalConfigs() {
        saveEmcConfig();
        saveLearnedConfig();
        saveBlockStoreConfig();
        saveDChestConfig();
        saveCChestConfig();
    }

    private void createLearnedConfig() {
        learnedItemsConfigFile = new File(plugin.getDataFolder(), "learned_items.yml");
        if (!learnedItemsConfigFile.exists()) {
            learnedItemsConfigFile.getParentFile().mkdirs();
            plugin.saveResource("learned_items.yml", false);
        }
        learnedItemsConfig = new YamlConfiguration();
        try {
            learnedItemsConfig.load(learnedItemsConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void saveLearnedConfig() {
        try {
            learnedItemsConfig.save(learnedItemsConfigFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to save " + learnedItemsConfigFile.getName());
        }
    }

    private void createEmcConfig() {
        playerEMCConfigFile = new File(plugin.getDataFolder(), "player_emc.yml");
        if (!playerEMCConfigFile.exists()) {
            playerEMCConfigFile.getParentFile().mkdirs();
            plugin.saveResource("player_emc.yml", false);
        }
        playerEMCConfig = new YamlConfiguration();
        try {
            playerEMCConfig.load(playerEMCConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void saveEmcConfig() {
        try {
            playerEMCConfig.save(playerEMCConfigFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to save " + playerEMCConfigFile.getName());
        }
    }

    private void createBlockStoreConfig() {
        blockStoreConfigFile = new File(plugin.getDataFolder(), "block_storage.yml");
        if (!blockStoreConfigFile.exists()) {
            blockStoreConfigFile.getParentFile().mkdirs();
            plugin.saveResource("block_storage.yml", false);
        }
        blockStoreExpectedLocations = BlockStoreIntegrity.countSerialisedLocations(blockStoreConfigFile);
        loadBlockStoreConfig();
    }

    private void loadBlockStoreConfig() {
        blockStoreConfig = new YamlConfiguration();
        // Bukkit escribe una traza completa por cada Location de un mundo aun no creado. Son
        // nueve por arranque para algo que el reintento resuelve solo, asi que se descartan
        // mientras dura la lectura y se resumen en el aviso propio.
        blockStoreSilencedErrors = BlockStoreLoadSilencer.runSilenced(() -> {
            try {
                blockStoreConfig.load(blockStoreConfigFile);
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
            }
        });
        checkBlockStoreIntegrity();
    }

    /**
     * Los mundos de BentoBox se crean despues de nuestro onEnable, asi que Location.deserialize
     * lanza "unknown world" y Bukkit borra esas entradas del arbol en silencio. Al guardar el
     * fichero mas tarde se perdian del disco para siempre. Comparamos lo cargado contra lo que
     * hay escrito para detectarlo y, mientras falten posiciones, bloqueamos el guardado.
     */
    private void checkBlockStoreIntegrity() {
        if (blockStoreExpectedLocations < 0) {
            // No se pudo leer el fichero en crudo: no hay con que comparar, no bloqueamos nada.
            blockStoreDegraded = false;
            return;
        }

        int loaded = countLoadedLocations();
        blockStoreDegraded = loaded < blockStoreExpectedLocations;

        if (!blockStoreDegraded) {
            return;
        }

        String detalle = "block_storage.yml: " + (blockStoreExpectedLocations - loaded) + " de "
            + blockStoreExpectedLocations + " posiciones no se pudieron leer porque su mundo no"
            + " esta cargado " + describeMissingWorlds() + ". No se guardara este fichero mientras"
            + " falten, para no borrar los cofres de esos mundos.";

        if (isFirstBlockStoreReload(blockStoreReloadAttempts)) {
            // Primera deteccion durante onEnable: los mundos de BentoBox aun no existen y el
            // reintento de la primera tick suele recuperarlos. Es ruido esperado, no una averia;
            // se registra como aviso para no disparar alertas de arranque que se resuelven solas.
            plugin.getLogger().warning(
                detalle + " Se reintentara al terminar el arranque."
                + describeSilencedErrors(blockStoreSilencedErrors)
            );
            scheduleBlockStoreReload(0L);
            return;
        }

        if (shouldRetryBlockStoreReload(blockStoreReloadAttempts)) {
            // BentoBox crea sus mundos de forma asincrona: unas veces estan listos en la primera
            // tick y otras tardan minutos. Mientras queden intentos seguimos releyendo en silencio
            // en vez de rendirnos, porque rendirse deja el guardado bloqueado toda la sesion.
            scheduleBlockStoreReload(BLOCK_STORE_RELOAD_PERIOD_TICKS);
            return;
        }

        // Agotados los reintentos y siguen faltando mundos: el guardado queda bloqueado toda la
        // sesion y eso si necesita intervencion.
        plugin.getLogger().severe(detalle);
    }

    /**
     * Deja constancia de las trazas de Bukkit que se descartaron, para que quien lea el arranque
     * sepa que no se han perdido en silencio.
     */
    static String describeSilencedErrors(int silenced) {
        if (silenced <= 0) {
            return "";
        }
        return " Se han silenciado " + silenced + " traza(s) identica(s) de Bukkit por estas"
            + " mismas posiciones.";
    }

    /** La deteccion de onEnable, la unica que se anuncia en el log. */
    static boolean isFirstBlockStoreReload(int attempts) {
        return attempts == 0;
    }

    /** Quedan reintentos: se relee en silencio en vez de rendirse y bloquear toda la sesion. */
    static boolean shouldRetryBlockStoreReload(int attempts) {
        return attempts < BLOCK_STORE_RELOAD_ATTEMPTS;
    }

    /**
     * Freno del aviso de guardado bloqueado. lastWarnedAt a 0 significa "aun no se ha avisado en
     * esta sesion", asi que el primero sale siempre sin depender de la magnitud del reloj.
     */
    static boolean shouldWarnBlockStoreSave(long now, long lastWarnedAt) {
        return lastWarnedAt == 0L || now - lastWarnedAt >= BLOCK_STORE_SAVE_WARNING_INTERVAL_MS;
    }

    private void scheduleBlockStoreReload(long delayTicks) {
        if (blockStoreReloadPending) {
            return;
        }
        blockStoreReloadPending = true;
        Bukkit.getScheduler().runTaskLater(plugin, this::retryBlockStoreLoad, delayTicks);
    }

    private void retryBlockStoreLoad() {
        blockStoreReloadPending = false;
        blockStoreReloadAttempts++;
        loadBlockStoreConfig();
        if (!blockStoreDegraded) {
            plugin.getLogger().info(
                "block_storage.yml releido con todos los mundos disponibles: "
                + blockStoreExpectedLocations + " posiciones recuperadas y guardado rehabilitado"
                + " tras " + blockStoreReloadAttempts + " reintento(s)."
            );
        }
    }

    private int countLoadedLocations() {
        return countLoadedLocations(DIS_CHEST_CFG) + countLoadedLocations(CON_CHEST_CFG);
    }

    private int countLoadedLocations(String path) {
        ConfigurationSection section = blockStoreConfig.getConfigurationSection(path);
        if (section == null) {
            return 0;
        }
        int count = 0;
        for (String key : section.getKeys(false)) {
            if (section.getLocation(key) != null) {
                count++;
            }
        }
        return count;
    }

    private String describeMissingWorlds() {
        List<String> missing = new ArrayList<>();
        for (String world : BlockStoreIntegrity.referencedWorlds(blockStoreConfigFile)) {
            if (Bukkit.getWorld(world) == null) {
                missing.add(world);
            }
        }
        return missing.isEmpty() ? "(mundo no identificado)" : String.join(", ", missing);
    }

    private void saveBlockStoreConfig() {
        if (blockStoreDegraded) {
            // Se guarda en cada autosave, asi que sin freno una sola sesion degradada deja miles
            // de lineas identicas en el log y tapa el resto de la consola.
            long now = System.currentTimeMillis();
            if (shouldWarnBlockStoreSave(now, blockStoreSaveWarnedAt)) {
                blockStoreSaveWarnedAt = now;
                plugin.getLogger().severe(
                    "block_storage.yml NO se guarda: la carga quedo incompleta y sobrescribirlo"
                    + " borraria las posiciones que no se pudieron leer. Este aviso se repite como"
                    + " mucho cada 5 minutos mientras dure el bloqueo."
                );
            }
            return;
        }
        try {
            blockStoreConfig.save(blockStoreConfigFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to save " + blockStoreConfigFile.getName());
        }
    }

    private void createDChestConfig() {
        dChestConfigFile = new File(plugin.getDataFolder(), "dissolution_chests.yml");
        if (!dChestConfigFile.exists()) {
            dChestConfigFile.getParentFile().mkdirs();
            plugin.saveResource("dissolution_chests.yml", false);
        }
        dChestConfig = new YamlConfiguration();
        try {
            dChestConfig.load(dChestConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void saveDChestConfig() {
        try {
            dChestConfig.save(dChestConfigFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to save " + dChestConfigFile.getName());
        }
    }

    private void createCChestConfig() {
        cChestConfigFile = new File(plugin.getDataFolder(), "condensation_chests.yml");
        if (!cChestConfigFile.exists()) {
            cChestConfigFile.getParentFile().mkdirs();
            plugin.saveResource("condensation_chests.yml", false);
        }
        cChestConfig = new YamlConfiguration();
        try {
            cChestConfig.load(cChestConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void saveCChestConfig() {
        try {
            cChestConfig.save(cChestConfigFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to save " + cChestConfigFile.getName());
        }
    }

    public static void addLearnedItem(EquivalencyTech plugin, String uuid, String itemName) {
        FileConfiguration c = plugin.getConfigMainClass().getLearnedItemsConfig();
        c.set(uuid + "." + ChatColor.stripColor(itemName), true);
    }

    public static void removeLearnedItem(EquivalencyTech plugin, Player player, String itemName) {
        FileConfiguration c = plugin.getConfigMainClass().getLearnedItemsConfig();
        c.set(player.getUniqueId().toString() + "." + itemName, null);
    }

    public static List<String> getLearnedItems(EquivalencyTech plugin, String uuid) {
        FileConfiguration c = plugin.getConfigMainClass().getLearnedItemsConfig();
        List<String> list = new ArrayList<>();
        if (c.contains(uuid)) {
            list.addAll(c.getConfigurationSection(uuid).getKeys(false));
            java.util.Collections.sort(list);
        }
        return list;
    }

    public static int getLearnedItemAmount(EquivalencyTech plugin, Player player) {
        return getLearnedItems(plugin, player.getUniqueId().toString()).size();
    }

    public static void addPlayerEmc(EquivalencyTech plugin, Player player, Double emcValue, Double totalEmc, int stackAmount) {
        double playerEmc = getPlayerEmc(plugin, player);
        int burnRate = plugin.getConfigMainClass().getEmc().getBurnRate();
        if (burnRate > 0) {
            totalEmc -= ((totalEmc / 100) * burnRate);
        }
        totalEmc = Utils.roundDown(totalEmc, 2);
        Double sum = playerEmc + totalEmc;
        if (sum.equals(Double.POSITIVE_INFINITY)) {
            sum = Double.MAX_VALUE;
        }
        setPlayerEmc(plugin, player, sum);
        player.sendMessage(Messages.messageGuiEmcGiven(plugin, player, emcValue, totalEmc, stackAmount, burnRate));
    }

    public static void addPlayerEmc(EquivalencyTech plugin, String uuid, Double totalEmc) {
        double playerEmc = getPlayerEmc(plugin, uuid);
        int burnRate = plugin.getConfigMainClass().getEmc().getBurnRate();
        if (burnRate > 0) {
            totalEmc -= ((totalEmc / 100) * burnRate);
        }
        totalEmc = Utils.roundDown(totalEmc, 2);
        Double sum = playerEmc + totalEmc;
        if (sum.equals(Double.POSITIVE_INFINITY)) {
            sum = Double.MAX_VALUE;
        }
        setPlayerEmc(plugin, uuid, sum);
    }

    public static void removePlayerEmc(EquivalencyTech plugin, Player player, Double emcValue) {
        setPlayerEmc(plugin, player, getPlayerEmc(plugin, player) - emcValue);
    }

    public static void removePlayerEmc(EquivalencyTech plugin, String uuid, Double emcValue) {
        setPlayerEmc(plugin, uuid, getPlayerEmc(plugin, uuid) - emcValue);
    }

    public static void setPlayerEmc(EquivalencyTech plugin, Player player, Double emcValue) {
        FileConfiguration c = plugin.getConfigMainClass().getPlayerEMCConfig();
        c.set(player.getUniqueId().toString(), emcValue);
    }

    public static void setPlayerEmc(EquivalencyTech plugin, String uuid, Double emcValue) {
        FileConfiguration c = plugin.getConfigMainClass().getPlayerEMCConfig();
        c.set(uuid, emcValue);
    }

    public static double getPlayerEmc(EquivalencyTech plugin, Player player) {
        FileConfiguration c = plugin.getConfigMainClass().getPlayerEMCConfig();
        double amount = 0;
        if (c.contains(player.getUniqueId().toString())) {
            amount = Utils.roundDown(c.getDouble(player.getUniqueId().toString()),2);
        }
        return amount;
    }

    public static double getPlayerEmc(EquivalencyTech plugin, String uuid) {
        FileConfiguration c = plugin.getConfigMainClass().getPlayerEMCConfig();
        double amount = 0;
        if (c.contains(uuid)) {
            amount = c.getDouble(uuid);
        }
        return amount;
    }


    private static int highestNumericKey(@Nullable ConfigurationSection section) {
        int highest = 0;
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    highest = Math.max(highest, Integer.parseInt(key));
                } catch (NumberFormatException ignored) {
                    // Claves que no son ids (o basura heredada) no participan en la numeracion.
                }
            }
        }
        return highest;
    }

    public static Integer getNextDChestID(EquivalencyTech plugin) {
        ConfigMain config = plugin.getConfigMainClass();
        // Se mira tambien el fichero de dueños: si una posicion se perdio al cargar pero su
        // registro de propiedad sigue vivo, reutilizar ese id le daria el cofre a otro jugador.
        int nextValue = Math.max(
            highestNumericKey(config.blockStoreConfig.getConfigurationSection(DIS_CHEST_CFG)),
            highestNumericKey(config.dChestConfig)
        );
        return nextValue + 1;
    }

    public static void addDChestStore(EquivalencyTech plugin, Location location) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        c.set(DIS_CHEST_CFG + "." + getNextDChestID(plugin).toString(), location);
    }

    @Nullable
    public static Integer getDChestIdStore(EquivalencyTech plugin, Location location) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        ConfigurationSection section = c.getConfigurationSection(DIS_CHEST_CFG);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Location l = section.getLocation(key);
                if (location.equals(l)) {
                    return Integer.parseInt(key);
                }
            }
        }
        return null;
    }

    public static void removeDChestStore(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        ConfigurationSection section = c.getConfigurationSection(DIS_CHEST_CFG);
        if (section != null) {
            section.set(id.toString(), null);
        }
    }

    public static void setupDChest(EquivalencyTech plugin, Integer id, Player player) {
        FileConfiguration c = plugin.getConfigMainClass().dChestConfig;
        c.set(id + ".OWNING_PLAYER", player.getUniqueId().toString());
        c.set(id + ".LEVEL", 1);
    }

    public static void removeDChest(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().dChestConfig;
        c.set(String.valueOf(id), null);
    }

    public static boolean isOwnerDChest(EquivalencyTech plugin, Player player, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().dChestConfig;
        return c.getString(id + ".OWNING_PLAYER").equals(player.getUniqueId().toString());
    }

    public static String getOwnerDChest(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().dChestConfig;
        return c.getString(id + ".OWNING_PLAYER");
    }

    @Nullable
    public static Location getDChestLocation(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        ConfigurationSection section = c.getConfigurationSection(DIS_CHEST_CFG);
        return section == null ? null : section.getLocation(id.toString());
    }

    public static List<Location> getAllDChestLocations(EquivalencyTech plugin) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        ConfigurationSection section = c.getConfigurationSection(DIS_CHEST_CFG);
        List<Location> ids = new ArrayList<>();
        if (section != null) {
            for (String s : section.getKeys(false)) {
                Location location = section.getLocation(s);
                if (location != null && location.getWorld() != null) {
                    ids.add(location);
                }
            }
        }
        return ids;
    }

    public static Integer getNextCChestID(EquivalencyTech plugin) {
        ConfigMain config = plugin.getConfigMainClass();
        // Se mira tambien el fichero de dueños: si una posicion se perdio al cargar pero su
        // registro de propiedad sigue vivo, reutilizar ese id le daria el cofre a otro jugador.
        int nextValue = Math.max(
            highestNumericKey(config.blockStoreConfig.getConfigurationSection(CON_CHEST_CFG)),
            highestNumericKey(config.cChestConfig)
        );
        return nextValue + 1;
    }

    public static void addCChestStore(EquivalencyTech plugin, Location location) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        c.set(CON_CHEST_CFG + "." + getNextCChestID(plugin).toString(), location);
    }

    @Nullable
    public static Integer getCChestIdStore(EquivalencyTech plugin, Location location) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        ConfigurationSection section = c.getConfigurationSection(CON_CHEST_CFG);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Location l = section.getLocation(key);
                if (location.equals(l)) {
                    return Integer.parseInt(key);
                }
            }
        }
        return null;
    }

    public static void removeCChestStore(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        ConfigurationSection section = c.getConfigurationSection(CON_CHEST_CFG);
        if (section != null) {
            section.set(id.toString(), null);
        }
    }

    public static void setupCChest(EquivalencyTech plugin, Integer id, Player player) {
        FileConfiguration c = plugin.getConfigMainClass().cChestConfig;
        c.set(id + ".OWNING_PLAYER", player.getUniqueId().toString());
        c.set(id + ".LEVEL", 1);
    }

    public static void removeCChest(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().cChestConfig;
        c.set(String.valueOf(id), null);
    }

    public static boolean isOwnerCChest(EquivalencyTech plugin, Player player, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().cChestConfig;
        return c.getString(id + ".OWNING_PLAYER").equals(player.getUniqueId().toString());
    }

    public static String getOwnerCChest(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().cChestConfig;
        return c.getString(id + ".OWNING_PLAYER");
    }

    @Nullable
    public static Location getCChestLocation(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        ConfigurationSection section = c.getConfigurationSection(CON_CHEST_CFG);
        return section == null ? null : section.getLocation(id.toString());
    }

    public static List<Location> getAllCChestLocations(EquivalencyTech plugin) {
        FileConfiguration c = plugin.getConfigMainClass().blockStoreConfig;
        ConfigurationSection section = c.getConfigurationSection(CON_CHEST_CFG);
        List<Location> ids = new ArrayList<>();
        if (section != null) {
            for (String s : section.getKeys(false)) {
                Location location = section.getLocation(s);
                if (location != null && location.getWorld() != null) {
                    ids.add(location);
                }
            }
        }
        return ids;
    }

    @Nullable
    public static ItemStack getCChestItem(EquivalencyTech plugin, Integer id) {
        FileConfiguration c = plugin.getConfigMainClass().cChestConfig;
        return c.getItemStack(id + ".ITEM");
    }

    public static void setCChestItem(EquivalencyTech plugin, Integer id, ItemStack itemStack) {
        FileConfiguration c = plugin.getConfigMainClass().cChestConfig;
        c.set(id + ".ITEM", itemStack);
    }

}
