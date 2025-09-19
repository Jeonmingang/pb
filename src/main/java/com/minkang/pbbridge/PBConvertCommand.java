
package com.minkang.pbbridge;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PBConvertCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }
        Player p = (Player) sender;

        String needPerm = PBBridge.get().cfg().getString("requirePermission", "");
        if (needPerm != null && !needPerm.isEmpty() && !p.hasPermission(needPerm)) {
            p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("messages.denied")));
            return true;
        }

        String template = PBBridge.get().cfg().getString("forwardCommand", "pokeitem convert {args}");
        String joinedArgs = String.join(" ", args);
        String cmd = template.replace("{args}", joinedArgs).replace("{player}", p.getName());

        PBBridge.dispatchAsConsole(cmd);
        p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("messages.success")));
        return true;
    }
}
