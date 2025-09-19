
package com.minkang.pbbridge;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PoketPhotoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }
        Player p = (Player) sender;

        String needPerm = PBBridge.get().cfg().getString("requirePermission", "");
        if (needPerm != null && !needPerm.isEmpty() && !p.hasPermission(needPerm)) {
            p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("photo.messages.denied")));
            return true;
        }

        int min = PBBridge.get().cfg().getInt("photo.minIndex", 1);
        int max = PBBridge.get().cfg().getInt("photo.maxIndex", 6);
        if (args.length != 1) {
            p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("photo.messages.usage")));
            return true;
        }
        int index;
        try { index = Integer.parseInt(args[0]); }
        catch (NumberFormatException ex) {
            p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("photo.messages.invalidIndex")));
            return true;
        }
        if (index < min || index > max) {
            p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("photo.messages.invalidIndex")));
            return true;
        }

        String template = PBBridge.get().cfg().getString("photo.forwardCommand", "pokeitem convert {index}");
        String cmd = template.replace("{index}", String.valueOf(index)).replace("{player}", p.getName());

        PBBridge.dispatchAsConsole(cmd);
        p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("photo.messages.success")));
        return true;
    }
}
