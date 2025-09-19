package com.minkang.pbbridge.command;

import com.minkang.pbbridge.PBBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PBCommand implements CommandExecutor {

    private final PBBridge plugin;

    public PBCommand(PBBridge plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("플레이어만 사용 가능합니다."); return true; }
        Player p = (Player) sender;
        if (args.length != 1) {
            p.sendMessage(color(plugin.getConfig().getString("messages.usage")));
            return true;
        }

        int slot;
        try { slot = Integer.parseInt(args[0]); } catch (Exception e) {
            p.sendMessage(color(plugin.getConfig().getString("messages.invalid-slot")));
            return true;
        }
        if (slot < 1 || slot > 6) {
            p.sendMessage(color(plugin.getConfig().getString("messages.invalid-slot")));
            return true;
        }

        int need = plugin.getConfig().getInt("require-empty-slots", 1);
        int empty = countEmpty(p);
        if (empty < need) {
            String msg = plugin.getConfig().getString("messages.need-space");
            p.sendMessage(color(msg.replace("%need%", String.valueOf(need))));
            return true;
        }

        String backend = plugin.getConfig().getString("backend", "betterpokeitem").toLowerCase();
        String extra = plugin.getConfig().getString("extra-args", "").trim();
        String cmd;
        if (backend.equals("ultimatepokeitemizer") || backend.equals("upi") || backend.equals("pokeitemizer")) {
            cmd = "pokeitem " + slot + (extra.isEmpty() ? "" : " " + extra);
        } else {
            cmd = "pokeitem convert " + slot + (extra.isEmpty() ? "" : " " + extra);
        }

        String runAs = plugin.getConfig().getString("run-as", "player").toLowerCase();
        switch (runAs) {
            case "console":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                break;
            case "op":
                boolean wasOp = p.isOp();
                try {
                    if (!wasOp) p.setOp(true);
                    Bukkit.dispatchCommand(p, cmd);
                } finally {
                    if (!wasOp) p.setOp(false);
                }
                break;
            default:
                Bukkit.dispatchCommand(p, cmd);
        }

        return true;
    }

    private int countEmpty(Player p) {
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack is = p.getInventory().getItem(i);
            if (is == null || is.getType() == Material.AIR) empty++;
        }
        return empty;
    }

    private String color(String s) { return s == null ? "" : s.replace("&", "§"); }
}
