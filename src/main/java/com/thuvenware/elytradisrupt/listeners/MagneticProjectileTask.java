package com.thuvenware.elytradisrupt.listeners;

import com.thuvenware.elytradisrupt.Main;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

public class MagneticProjectileTask {
    private Projectile projectile;
    private Player target;
    private long cancelTimeMillis;
    private int taskID;

    public MagneticProjectileTask(Projectile projectile, Player target) {
        this(projectile, target, 5000);
    }

    public MagneticProjectileTask(Projectile projectile, Player target, long timeoutMillis) {
        this.projectile = projectile;
        this.target = target;
        this.cancelTimeMillis = System.currentTimeMillis() + timeoutMillis;
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
        Main.getPlugin().getServer().getScheduler().cancelTask(taskID);
    }

    public void onTick() {
        if (System.currentTimeMillis() > cancelTimeMillis || !projectile.isValid()) {
            // task no longer needed
            cancelTask();
        }

        Player shooter = (Player) projectile.getShooter();

        Vector currentVel = projectile.getVelocity();
        Vector outputVel;

        double startTrackingDistance = Main.getPlugin().getConfig().getDouble("magnetism.startTrackingDistance");
        double fullTrackingDistance = Main.getPlugin().getConfig().getDouble("magnetism.fullTrackingDistance");
        double activationFactor = 1 - ((vectorToTarget().length() - fullTrackingDistance)
                / (startTrackingDistance - fullTrackingDistance));

        double angleToTarget = angleToTarget();
        double desiredAngle = angleToTarget *
                (1.0 - (Main.getPlugin().getConfig().getDouble("magnetism.correctionFactor") * activationFactor));

        if (Main.getPlugin().getConfig().getBoolean("debugMode")) {
            shooter.sendMessage(String.format("current angle %f, desired angle %f", angleToTarget, desiredAngle));
        }

        double maxTrackingAngle = Math.toRadians(Main.getPlugin().getConfig().getDouble("magnetism.maxTrackingAngle"));
        if (angleToTarget > maxTrackingAngle) {
            // we lost the target; stop tracking
            cancelTask();
        }

        Vector rotationAxis = currentVel.getCrossProduct(vectorToTarget());

        double desiredRotation = desiredAngle - angleToTarget;
        double maxDegreesPerTick = Math.toRadians(Main.getPlugin().getConfig().getDouble("magnetism.maxDegreesPerTick"));
        if (desiredRotation > maxDegreesPerTick) desiredRotation = maxDegreesPerTick;

        // Rotate as desired
        outputVel = currentVel.rotateAroundAxis(rotationAxis, -desiredRotation);
        projectile.setVelocity(outputVel);

        if (Main.getPlugin().getConfig().getBoolean("debugMode"))
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
