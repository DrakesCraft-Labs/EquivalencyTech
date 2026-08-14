package io.github.sefiraat.equivalencytech;

import co.aikar.commands.PaperCommandManager;
import io.github.sefiraat.equivalencytech.commands.Commands;
import io.github.sefiraat.equivalencytech.configuration.ConfigMain;
import io.github.sefiraat.equivalencytech.item.EQItems;
import io.github.sefiraat.equivalencytech.listeners.ManagerEvents;
import io.github.sefiraat.equivalencytech.misc.ManagerSupportedPlugins;
import io.github.sefiraat.equivalencytech.recipes.EmcDefinitions;
import io.github.sefiraat.equivalencytech.recipes.Recipes;
import io.github.sefiraat.equivalencytech.runnables.ManagerRunnables;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;

public class EquivalencyTech extends JavaPlugin {

    private static EquivalencyTech instance;
    private PaperCommandManager commandManager;

    private ConfigMain configMainClass;
    private EmcDefinitions emcDefinitions;
    private EQItems eqItems;
    private Recipes recipes;
    private ManagerEvents managerEvents;
    private ManagerRunnables managerRunnables;
    private ManagerSupportedPlugins managerSupportedPlugins;

    private boolean isUnitTest = false;

    public PaperCommandManager getCommandManager() {
        return commandManager;
    }

    public static EquivalencyTech getInstance() {
        return instance;
    }

    public ConfigMain getConfigMainClass() {
        return configMainClass;
    }

    public EmcDefinitions getEmcDefinitions() {
        return emcDefinitions;
    }

    public EQItems getEqItems() {
        return eqItems;
    }

    public Recipes getRecipes() {
        return recipes;
    }

    public ManagerEvents getManagerEvents() {
        return managerEvents;
    }

    public ManagerRunnables getManagerRunnables() {
        return managerRunnables;
    }

    public ManagerSupportedPlugins getManagerSupportedPlugins() {
        return managerSupportedPlugins;
    }

    public EquivalencyTech() {
        super();
    }

    protected EquivalencyTech(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);
        isUnitTest = true;
    }

    @Override
    public void onEnable() {

        getLogger().info("########################################");
        getLogger().info(" EquivalencyTech - Created by Sefiraat  ");
        getLogger().info("########################################");

        instance = this;

        // Aqui iba el autoactualizador, que se traia el jar del repositorio de upstream.
        //
        // Se quita entero en vez de apagarlo por configuracion: este jar esta recompilado contra
        // el Slimefun repaquetado del servidor, asi que bajarse el de upstream encima dejaria el
        // addon sin cargar. Hasta ahora lo unico que lo frenaba era que su condicion exige una
        // version que empiece por "DEV", y la nuestra es "modified" -- una coincidencia que se
        // rompe el dia que alguien toque la cadena de version.

        configMainClass = new ConfigMain(this);
        eqItems = new EQItems(this);
        managerSupportedPlugins = new ManagerSupportedPlugins(this);
        emcDefinitions = new EmcDefinitions(this);
        recipes = new Recipes(this);
        managerEvents = new ManagerEvents(this);
        managerRunnables = new ManagerRunnables(this);

        registerCommands();

        // El EMC de los objetos de Slimefun se calcula ya con el servidor arriba, repartido entre
        // ticks. Antes se hacia aqui mismo y bloqueaba el arranque casi un minuto -- y el
        // 14-08-2026 no llego a terminar, dejando el servidor tres horas sin arrancar.
        emcDefinitions.calcularSlimefunPorTandas(this);

    }

    @Override
    public void onDisable() {
        saveConfig();
        configMainClass.saveAdditionalConfigs();
    }

    private void registerCommands() {
        commandManager = new PaperCommandManager(this);
        commandManager.registerCommand(new Commands(this));
    }






}
