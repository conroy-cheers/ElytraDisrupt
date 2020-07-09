package com.thuvenware.elytradisrupt.listeners;

import com.thuvenware.elytradisrupt.ElytraDisrupt;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

public class MagneticProjectileTask {
    private Projectile projectile;
    private Player target;
    private long cancelTimeMillis;
    private double powerMultiplier;
    private int taskID;

    private boolean activationEffectsPlayed;
    private boolean applyGlowing;

    public MagneticProjectileTask(Projectile projectile, Player target, double powerMultiplier) {
        this(projectile, target, powerMultiplier, 5000);
    }

    public MagneticProjectileTask(Projectile projectile, Player target, double powerMultiplier, long timeoutMillis) {
        this.projectile = projectile;
        this.target = target;
        this.powerMultiplier = powerMultiplier;
        this.cancelTimeMillis = System.currentTimeMillis() + timeoutMillis;

        this.activationEffectsPlayed = false;
        this.applyGlowing = false;
    }

    public void setTaskID(int taskID) {
        this.taskID = taskID;
    }

    private Vector vectorToTarget() {
        return target.getEyeLocation().toVector().subtract(projectile.getLocation().toVector());
    }

    private double angleToTarget() {
        return projectile.getVelocity().angle(vectorToTarget());
    }

    private void cancelTask() {
        projectile.setGlowing(false);
        ElytraDisrupt.getPlugin().getServer().getScheduler().cancelTask(taskID);
    }

    public void onTick() {
        if (System.currentTimeMillis() > cancelTimeMillis || !projectile.isValid()) {
            // task no longer needed
            cancelTask();
            return;
        }

        Player shooter = (Player) projectile.getShooter();
        assert shooter != null;

        Vector currentVel = projectile.getVelocity();
        Vector outputVel;

        double startTrackingDistance = ElytraDisrupt.getPlugin().getConfig().getDouble("magnetism.startTrackingDistance");
        double fullTrackingDistance = ElytraDisrupt.getPlugin().getConfig().getDouble("magnetism.fullTrackingDistance");
        double activationFactor = 1 - ((vectorToTarget().length() - fullTrackingDistance)
                / (startTrackingDistance - fullTrackingDistance));
        if (vectorToTarget().length() > startTrackingDistance) activationFactor = 0;
        if (vectorToTarget().length() < fullTrackingDistance) activationFactor = 1;

        if (activationFactor > 0.5 && !activationEffectsPlayed) {
            // play sound to shooter and target
            shooter.playSound(projectile.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 2, 2);
            target.playSound(projectile.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 2, 2);
            // add glowing effect to target
            applyGlowing = true;
            activationEffectsPlayed = true;
        }

        if (applyGlowing) {
            projectile.setGlowing(true);
        }

        double angleToTarget = angleToTarget();
        double desiredAngle = angleToTarget *
                (1.0 - (ElytraDisrupt.getPlugin().getConfig().getDouble("magnetism.correctionFactor") * activationFactor * powerMultiplier));

        if (ElytraDisrupt.getPlugin().getConfig().getBoolean("debugMode")) {
            shooter.sendMessage(String.format("current angle %f, desired angle %f", angleToTarget, desiredAngle));
        }

        double maxTrackingAngle = Math.toRadians(ElytraDisrupt.getPlugin().getConfig().getDouble("magnetism.maxTrackingAngle") * powerMultiplier);
        if (angleToTarget > maxTrackingAngle) {
            // we lost the target; stop tracking
            cancelTask();
            return;
        }

        Vector rotationAxis = currentVel.getCrossProduct(vectorToTarget());

        double desiredRotation = desiredAngle - angleToTarget;
        double maxDegreesPerTick = Math.toRadians(ElytraDisrupt.getPlugin().getConfig().getDouble("magnetism.maxDegreesPerTick") * powerMultiplier);
        if (desiredRotation > maxDegreesPerTick) desiredRotation = maxDegreesPerTick;

        // Rotate as desired
        outputVel = currentVel.rotateAroundAxis(rotationAxis, -desiredRotation);
        projectile.setVelocity(outputVel);

        if (ElytraDisrupt.getPlugin().getConfig().getBoolean("debugMode"))
            shooter.sendMessage(String.format("%s: Corrected angle from %f to %f",
                    projectile.getName(),
                    Math.toDegrees(angleToTarget),
                    Math.toDegrees(angleToTarget())));
    }

    public Projectile getProjectile() {
        return projectile;
    }

    public Player getTarget() {
        return target;
    }

    public long getCancelTimeMillis() {
        return cancelTimeMillis;
    }
}
