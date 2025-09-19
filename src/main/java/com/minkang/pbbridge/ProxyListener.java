
package com.minkang.pbbridge;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;

public class ProxyListener implements Listener {

    @EventHandler
    public void onPreprocess(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().trim(); // e.g. "/pokeitem convert 1"
        String lower = msg.toLowerCase();
        String head = "/pokeitem convert";
        if (!lower.startsWith(head)) return;

        Player p = e.getPlayer();
        String needPerm = PBBridge.get().cfg().getString("requirePermission", "");
        if (needPerm != null && !needPerm.isEmpty() && !p.hasPermission(needPerm)) {
            p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("messages.denied")));
            return;
        }

        int idx2 = msg.toLowerCase().indexOf(" convert");
        String args = "";
        if (idx2 != -1) {
            args = msg.substring(idx2 + " convert".length()).trim();
        }

        String template = PBBridge.get().cfg().getString("forwardCommand", "pokeitem convert {args}");
        String cmd = template.replace("{args}", args).replace("{player}", p.getName());

        e.setCancelled(true);
        PBBridge.dispatchAsConsole(cmd);
        p.sendMessage(PBBridge.color(PBBridge.get().cfg().getString("messages.success")));
    }
}
