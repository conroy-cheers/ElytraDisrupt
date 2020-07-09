package com.thuvenware.elytradisrupt.listeners;

import com.songoda.epicenchants.EpicEnchants;
import com.songoda.epicenchants.objects.Enchant;
import com.songoda.epicenchants.utils.EnchantUtils;
import com.thuvenware.elytradisrupt.ElytraDisrupt;
import net.minecraft.server.v1_15_R1.EntityThrownTrident;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_15_R1.entity.CraftTrident;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EventListener implements Listener {
    HashMap<UUID, Integer> inFlightProjectileTasks;
    EnchantUtils enchantUtils;

    public EventListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        inFlightProjectileTasks = new HashMap<UUID, Integer>();

        enchantUtils = new EnchantUtils(EpicEnchants.getInstance());
    }

    public void cancelAllTrackingTasks() {
        for (int taskID : inFlightProjectileTasks.values()) {
            ElytraDisrupt.getPlugin().getServer().getScheduler().cancelTask(taskID);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player shooter = (Player) event.getEntity().getShooter();
            Projectile projectile = event.getEntity();

            // Check that the projectile has the correct enchantment
            // Only Tridents are supported currently
            if (projectile instanceof Trident) {
                EntityThrownTrident trident = ((CraftTrident) projectile).getHandle();
                ItemStack bukkitItem = CraftItemStack.asBukkitCopy(trident.trident);
                Map<Enchant, Integer> itemEnchants = enchantUtils.getEnchants(bukkitItem);

                AtomicInteger magnetismEnchantmentLevel = new AtomicInteger();
                magnetismEnchantmentLevel.set(0);

                if (itemEnchants.keySet().stream().anyMatch(
                        ench -> {
                            if (ench.getIdentifier().equals(ElytraDisrupt.getPlugin().getConfig()
                                    .getString("enchantments.magnetism.name"))) {
                                magnetismEnchantmentLevel.set(itemEnchants.get(ench));
                                return true;
                            } else {
                                return false;
                            }
                        }
                )) {
                    // Magnetism enchantment is present, run magnetism task
                    double powerMultiplier = magnetismEnchantmentLevel.get() / 3.0;

                    HashMap<Player, Float> playerCastAngles = new HashMap<Player, Float>();
                    List<Player> nearbyPlayers = shooter.getNearbyEntities(100, 50, 100).stream()
                            .filter(e -> e instanceof Player && e != shooter && !e.hasMetadata("NPC"))
                            .map(e -> (Player) e)
                            .map(p -> {
                                Vector toTarget = p.getLocation().toVector().subtract(shooter.getLocation().toVector());
                                double expectedFlightTime = toTarget.length() / projectile.getVelocity().length();
                                Location projectedTargetLocation = p.getLocation()
                                        .add(p.getVelocity().multiply(expectedFlightTime));
                                playerCastAngles.put(p, projectile.getVelocity().angle(projectedTargetLocation.toVector()
                                        .subtract(shooter.getLocation().toVector())));
                                return p;
                            })
                            .filter(p -> playerCastAngles.get(p) <
                                    Math.toRadians(ElytraDisrupt.getPlugin().getConfig().getDouble("magnetism.maxCastAngle")))
                            .sorted(Comparator.comparingDouble(p ->
                                    shooter.getLocation().toVector().subtract(p.getLocation().toVector()).length() +
                                            playerCastAngles.get(p) * 300))
                            .collect(Collectors.toList());

                    if (ElytraDisrupt.getPlugin().getConfig().getBoolean("debugMode")) {
                        for (Player player : playerCastAngles.keySet()) {
                            shooter.sendMessage(String.format("Player '%s' with angle %f", player, playerCastAngles.get(player)));
                        }
                        shooter.sendMessage(String.format(
                                "Max cast angle: %f",
                                Math.toRadians(ElytraDisrupt.getPlugin().getConfig().getDouble("magnetism.maxCastAngle"))));
                    }

                    if (nearbyPlayers.size() == 0)
                        return;

                    // choose target player and schedule magnetism task
                    Player target = nearbyPlayers.get(0);

                    if (ElytraDisrupt.getPlugin().getConfig().getBoolean("debugMode"))
                        shooter.sendMessage(String.format("Projectile tracking player %s", target));

                    MagneticProjectileTask projectileTask = new MagneticProjectileTask(projectile, target, powerMultiplier,
                            (long) (ElytraDisrupt.getPlugin().getConfig().getDouble("magnetism.maxTrackingTime") * 1000 / powerMultiplier));
                    int launchID = ElytraDisrupt.getPlugin().getServer().getScheduler().scheduleSyncRepeatingTask(ElytraDisrupt.getPlugin(),
                            projectileTask::onTick, 4, 1);
                    projectileTask.setTaskID(launchID);  // task will self-cancel through the scheduler
                    inFlightProjectileTasks.put(projectile.getUniqueId(), launchID);

                    if (ElytraDisrupt.getPlugin().getConfig().getBoolean("debugMode")) {
                        shooter.sendMessage("Tracked projectiles:");
                        for (UUID projID : inFlightProjectileTasks.keySet()) {
                            shooter.sendMessage(String.format("- %s", projID.toString()));
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (inFlightProjectileTasks.containsKey(event.getEntity().getUniqueId())) {
            // Cancel the magnetism task upon tracked projectile hitting something
            ElytraDisrupt.getPlugin().getServer().getScheduler().cancelTask(
                    inFlightProjectileTasks.remove(event.getEntity().getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && !event.getEntity().hasMetadata("NPC")
                && event.getFinalDamage() > 0) {
            if (event.getDamager() instanceof Trident) {
                Trident trident = (Trident) event.getDamager();
                if (trident.getShooter() instanceof Player) {
                    // player-thrown trident.

                    ItemStack bukkitItem = CraftItemStack.asBukkitCopy(((CraftTrident) trident).getHandle().trident);
                    Map<Enchant, Integer> itemEnchants = enchantUtils.getEnchants(bukkitItem);
                    AtomicInteger pullEnchantmentLevel = new AtomicInteger();
                    pullEnchantmentLevel.set(0);

                    if (itemEnchants.keySet().stream().anyMatch(
                            ench -> {
                                if (ench.getIdentifier().equals(ElytraDisrupt.getPlugin().getConfig()
                                        .getString("enchantments.pull.name"))) {
                                    pullEnchantmentLevel.set(itemEnchants.get(ench));
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                    )) {
                        double pullPowerLevel = pullEnchantmentLevel.get() / 3.0;

                        // get config
                        FileConfiguration config = ElytraDisrupt.getPlugin().getConfig();

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
                        impulseMultiplier *= pullPowerLevel;
                        velocityCut *= pullPowerLevel;

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
                        shooter.playSound(shooter.getLocation().toVector().subtract(impulseDirection.multiply(3.0f))
                                .toLocation(shooter.getWorld()), Sound.ITEM_TRIDENT_THROW, 1.0f, 0.6f);
                        shooter.spawnParticle(Particle.SQUID_INK, shootee.getLocation(), 200, 0, 0, 0, 0.5f);

                        if (config.getBoolean("debugMode"))
                            event.getEntity().sendMessage(String.format("%f %f %f",
                                    scaledImpulse.getX(), scaledImpulse.getY(), scaledImpulse.getZ()));

                        double finalVelocityCut = velocityCut;
                        ElytraDisrupt.getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(
                                ElytraDisrupt.getPlugin(),
                                () -> shootee.setVelocity(shootee.getVelocity().multiply(finalVelocityCut).add(scaledImpulse)),
                                0);
                    }
                }
            }
        }
    }
}
