package org.mydreamduels.dreamworldedit;

import org.bukkit.plugin.java.JavaPlugin;
import org.mydreamduels.dreamworldedit.command.BrushCommands;
import org.mydreamduels.dreamworldedit.command.ClipboardCommands;
import org.mydreamduels.dreamworldedit.command.Commands;
import org.mydreamduels.dreamworldedit.command.GeneralCommands;
import org.mydreamduels.dreamworldedit.command.NavigationCommands;
import org.mydreamduels.dreamworldedit.command.RegionCommands;
import org.mydreamduels.dreamworldedit.command.SelectionCommands;
import org.mydreamduels.dreamworldedit.edit.EditService;
import org.mydreamduels.dreamworldedit.edit.Engine;
import org.mydreamduels.dreamworldedit.edit.Operations;
import org.mydreamduels.dreamworldedit.platform.PaperSchedulerBridge;
import org.mydreamduels.dreamworldedit.platform.PlatformSupport;
import org.mydreamduels.dreamworldedit.platform.SchedulerBridge;
import org.mydreamduels.dreamworldedit.platform.SpigotSchedulerBridge;
import org.mydreamduels.dreamworldedit.session.SessionManager;
import org.mydreamduels.dreamworldedit.util.Msg;

/**
 * DreamWorldEdit - WorldEdit/FAWE-style editing (selections, //set,
 * //replace, copy/paste, .schem files, brushes, shapes, undo...) that runs
 * on Paper, Folia and Spigot. See edit/Engine for how edits stay lag-free
 * and Folia-safe.
 */
public class DreamWorldEdit extends JavaPlugin {

    private SchedulerBridge scheduler;
    private SessionManager sessions;
    private Engine engine;
    private Operations ops;
    private EditService edits;
    private Visualizer visualizer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        Msg.setPrefix(getConfig().getString("prefix", "&8[&dDWE&8] &r"));

        scheduler = PlatformSupport.hasUnifiedSchedulers() ? new PaperSchedulerBridge(this) : new SpigotSchedulerBridge(this);
        sessions = new SessionManager();
        engine = new Engine(this);
        ops = new Operations(this);
        edits = new EditService(this);
        visualizer = new Visualizer(this);
        engine.start();

        Commands commands = new Commands(this);
        SelectionCommands.register(commands);
        RegionCommands.register(commands);
        ClipboardCommands.register(commands);
        BrushCommands.register(commands);
        NavigationCommands.register(commands);
        GeneralCommands.register(commands);
        commands.registerAll();

        getServer().getPluginManager().registerEvents(new ToolListener(this), this);
        new java.io.File(getDataFolder(), "schematics").mkdirs();

        getLogger().info("Enabled on " + PlatformSupport.name() + " with " + commands.all().size() + " commands.");
        if (getServer().getPluginManager().getPlugin("WorldEdit") != null
                || getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            getLogger().warning("WorldEdit/FAWE is also installed - both use //commands; whichever registers last wins.");
        }
    }

    @Override
    public void onDisable() {
        if (engine != null) engine.stop();
        if (scheduler != null) scheduler.cancelAll();
    }

    public SchedulerBridge scheduler() { return scheduler; }
    public SessionManager sessions() { return sessions; }
    public Engine engine() { return engine; }
    public Operations ops() { return ops; }
    public EditService edits() { return edits; }
    public Visualizer visualizer() { return visualizer; }
}
