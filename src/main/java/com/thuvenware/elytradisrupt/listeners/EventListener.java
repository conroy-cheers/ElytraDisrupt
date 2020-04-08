package com.thuvenware.elytradisrupt.listeners;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.thuvenware.elytradisrupt.Main;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class EventListener implements Listener {
    HashMap<UUID, Integer> inFlightProjectileTasks;

    public EventListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        inFlightProjectileTasks = new HashMap<UUID, Integer>();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player shooter = (Player) event.getEntity().getShooter();
            Projectile projectile = event.getEntity();

            if (Main.getPlugin().getConfig().getBoolean("debugMode"))
                shooter.sendMessage(String.format("You just launched a %s", projectile.getName()));

            HashMap<Player, Float> playerCastAngles = new HashMap<Player, Float>();
            List<Player> nearbyPlayers = shooter.getNearbyEntities(100, 50, 100).stream()
                    .filter(e -> e instanceof Player && e != shooter && !e.hasMetadata("NPC"))
                    .map(e -> (Player) e)
                    .map(p -> {
                        playerCastAngles.put(p, projectile.getVelocity().angle(
                                p.getLocation().toVector().subtract(shooter.getLocation().toVector())));
                        return p;
                    })
                    .filter(p -> playerCastAngles.get(p) <
                            Math.toRadians(Main.getPlugin().getConfig().getDouble("magnetism.maxCastAngle")))
                    .sorted(Comparator.comparingDouble(p ->
                            shooter.getLocation().toVector().subtract(p.getLocation().toVector()).length() +
                            playerCastAngles.get(p) * 10))
                    .collect(Collectors.toList());

            if (Main.getPlugin().getConfig().getBoolean("debugMode")) {
                for (Player player : playerCastAngles.keySet()) {
                    shooter.sendMessage(String.format("Player '%s' with angle %f", player, playerCastAngles.get(player)));
                }
                shooter.sendMessage(String.format(
                        "Max cast angle: %f",
                        Math.toRadians(Main.getPlugin().getConfig().getDouble("magnetism.maxCastAngle"))));
            }

            if (nearbyPlayers.size() == 0)
                return;

            // choose target player and schedule magnetism task
            Player target = nearbyPlayers.get(0);

            if (Main.getPlugin().getConfig().getBoolean("debugMode"))
                shooter.sendMessage(String.format("Projectile tracking player %s", target));

            MagneticProjectileTask projectileTask = new MagneticProjectileTask(projectile, target,
                    (long) Main.getPlugin().getConfig().getDouble("magnetism.maxTrackingTime") * 1000);
            int launchID = Main.getPlugin().getServer().getScheduler().scheduleSyncRepeatingTask(Main.getPlugin(),
                    projectileTask::onTick, 0, 1);
            projectileTask.setTaskID(launchID);  // task will self-cancel through the scheduler
            inFlightProjectileTasks.put(projectile.getUniqueId(), launchID);

            if (Main.getPlugin().getConfig().getBoolean("debugMode")) {
                shooter.sendMessage("Tracked projectiles:");
                for (UUID projID : inFlightProjectileTasks.keySet()) {
                    shooter.sendMessage(String.format("- %s", projID.toString()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (inFlightProjectileTasks.isEmpty())
            return;
        if (inFlightProjectileTasks.containsKey(event.getEntity().getUniqueId())) {
            // Cancel the magnetism task upon tracked projectile hitting something
            Main.getPlugin().getServer().getScheduler().cancelTask(
                    inFlightProjectileTasks.remove(event.getEntity().getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player// && !event.getEntity().hasMetadata("NPC")
                && event.getFinalDamage() > 0) {
            if (event.getDamager() instanceof Trident) {
                Trident trident = (Trident) event.getDamager();
                if (trident.getShooter() instanceof Player) {
                    // player-thrown trident.

                    // get config
                    FileConfiguration config = Main.getPlugin().getConfig();

                    Player shooter = (Player) trident.getShooter();
                    Player shootee = (Player) event.getEntity();
                    if (config.getBoolean("debugMode"))
                        shootee.sendMessage(String.format("%s struck you with a trident!", shooter.getDisplayName()));

                    // do some fun stuff here
                    double impulseMultiplier;
                    double velocityCut;
                    if (shootee.isGliding()) {
                        if (config.getBoolean("effects.gliding.cancelGlide"))
                            shootee.setGliding(false);
                        impulseMultiplier = config.getDouble("effects.gliding.pullAmount");
                        velocityCut = config.getDouble("effects.gliding.velocityCut");
                    } else {
                        impulseMultiplier = config.getDouble("effects.notGliding.pullAmount");
                        velocityCut = config.getDouble("effects.notGliding.velocityCut");
                    }

                    // apply an impulse to the player
                    Vector impulseDirection = shooter.getLocation().toVector().subtract(
                            shootee.getLocation().toVector()).normalize();
                    if (!(Double.isFinite(impulseDirection.getX()) &&
                            Double.isFinite(impulseDirection.getY()) &&
                            Double.isFinite(impulseDirection.getZ()))) {
                        impulseDirection = new Vector(0, 0, 0);
                    }
                    Vector scaledImpulse = impulseDirection.multiply(impulseMultiplier);

                    // play reel sound at both ends
                    shootee.playSound(shootee.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 0.6f);
                    shootee.spawnParticle(Particle.SQUID_INK, shootee.getLocation(), 200, 0, 0, 0, 0.5f);
                    shooter.playSound(shooter.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 0.6f);
                    shooter.spawnParticle(Particle.SQUID_INK, shootee.getLocation(), 200, 0, 0, 0, 0.5f);

                    if (config.getBoolean("debugMode"))
                        event.getEntity().sendMessage(String.format("%f %f %f",
                                scaledImpulse.getX(), scaledImpulse.getY(), scaledImpulse.getZ()));

                    Main.getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(
                            Main.getPlugin(),
                            () -> shootee.setVelocity(shootee.getVelocity().multiply(velocityCut).add(scaledImpulse)),
                            0);
                }
            }
        }
    }
}
