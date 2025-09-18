package com.minkang.ultimate.pokeitem.listeners;

import com.minkang.ultimate.pokeitem.Main;
import com.minkang.ultimate.pokeitem.service.ItemService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class RedeemListener implements Listener {

    private final Main plugin;
    private final ItemService items;

    public RedeemListener(Main plugin, ItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getItem() == null || e.getItem().getType() == Material.AIR) return;
        // Right click to redeem
        switch (e.getAction()) {
            case RIGHT_CLICK_AIR:
            case RIGHT_CLICK_BLOCK:
                items.redeemInHand(e.getPlayer());
                break;
            default:
                break;
        }
    }
}
