package com.thuvenware.elytradisrupt.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

public class ElytraDisruptConfig {
    private FileConfiguration config;
    private Plugin plugin;

    public ElytraDisruptConfig(Plugin plugin) {
        this.config = plugin.getConfig();
        this.plugin = plugin;
    }

    public void createConfigFile() {
        try {
            if (!plugin.getDataFolder().exists()) {
                if (!plugin.getDataFolder().mkdirs()) {
                    throw new Exception("Could not create config folder!");
                }
                File file = new File(plugin.getDataFolder(), "config.yml");
                if (!file.getAbsoluteFile().exists()) {
                    plugin.getLogger().info("config.yml not found, creating it now");
                    addDefaultOptions();
                    plugin.saveConfig();
                } else {
                    plugin.getLogger().info("Loading values from config.yml");
                    config = plugin.getConfig();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addDefaultOptions() {
        config.options().header("ElytraDisrupt Configuration");

        config.addDefault("enable", true);
        config.addDefault("debugMode", false);

        config.addDefault("magnetism.maxCastAngle", 10.0);
        config.addDefault("magnetism.maxTrackingTime", 5.0);
        config.addDefault("magnetism.maxTrackingAngle", 25.0);
        config.addDefault("magnetism.correctionFactor", 1.0);
        config.addDefault("magnetism.maxDegreesPerTick", 2.0);
        config.addDefault("magnetism.startTrackingDistance", 50.0);
        config.addDefault("magnetism.fullTrackingDistance", 15.0);

        config.addDefault("effects.gliding.cancelGlide", true);
        config.addDefault("effects.gliding.velocityCut", 0.6);
        config.addDefault("effects.gliding.pullAmount", 3.0);

        config.addDefault("effects.notGliding.velocityCut", 0.4);
        config.addDefault("effects.notGliding.pullAmount", 1.0);

        config.options().copyDefaults(true);
    }
}
