package com.thuvenware.elytradisrupt.commands;

import com.thuvenware.elytradisrupt.Main;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.annotation.command.*;

@Commands(@Command(name = "elytradisrupt", aliases = "", usage = "/elytradisrupt"))
public class ElytraDisruptCommandExecutor implements CommandExecutor {
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            Main.getPlugin().createConfigFile();
            Main.getPlugin().reloadConfig();
            sender.sendMessage("[ElytraDisrupt] Configuration reloaded.");
            return true;
        }
        sender.sendMessage("ElytraDisrupt 1.0");
        return true;
    }
}
