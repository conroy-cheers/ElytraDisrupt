package com.thuvenware.elytradisrupt;

import com.thuvenware.elytradisrupt.commands.ElytraDisruptCommandExecutor;
import com.thuvenware.elytradisrupt.config.ElytraDisruptConfig;
import com.thuvenware.elytradisrupt.listeners.EventListener;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion.Target;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.author.Author;


@Plugin(name = "ElytraDisrupt", version = "1.0")
@Description(value = "Disrupt players flying with Elytra")
@Author(value = "Thuvenware")
@ApiVersion(Target.v1_15)
public class Main extends JavaPlugin {
    private static Main plugin;
    private ElytraDisruptConfig config;

    @Override
    public void onLoad() {
        plugin = this;
    }

    @Override
    public void onEnable() {
        // setup config
        config = new ElytraDisruptConfig(this);
        config.createConfigFile();

        // register event listener
        new EventListener(this);

        getCommand("elytradisrupt").setExecutor(new ElytraDisruptCommandExecutor());
        getLogger().info("ElytraDisrupt enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ElytraDisrupt disabled.");
    }

    public void createConfigFile() {
        config.createConfigFile();
    }

    public static Main getPlugin() {
        return plugin;
    }
}
