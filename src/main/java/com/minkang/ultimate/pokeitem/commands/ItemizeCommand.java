package com.minkang.ultimate.pokeitem.commands;

import com.minkang.ultimate.pokeitem.Main;
import com.minkang.ultimate.pokeitem.model.PokemonInfo;
import com.minkang.ultimate.pokeitem.service.ItemService;
import com.minkang.ultimate.pokeitem.service.PixelmonHook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ItemizeCommand implements CommandExecutor {

    private final Main plugin;
    private final PixelmonHook hook;
    private final ItemService items;

    public ItemizeCommand(Main plugin, PixelmonHook hook, ItemService items) {
        this.plugin = plugin;
        this.hook = hook;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("플레이어만 사용 가능");
            return true;
        }
        Player p = (Player) sender;

        String name = cmd.getName().toLowerCase();
        if (name.equals("pokeitem") || name.equals("포켓몬아이템화") || name.equals("포켓몬아이템")) {
            if (args.length != 1) {
                p.sendMessage("§c사용법: /포켓몬아이템화 <1~6>");
                return true;
            }
            int slot;
            try { slot = Integer.parseInt(args[0]); } catch (Exception e) { p.sendMessage(plugin.msg("invalid_slot")); return true; }
            if (slot < 1 || slot > 6) { p.sendMessage(plugin.msg("invalid_slot")); return true; }

            if (!hook.isPixelmonPresent()) { p.sendMessage(plugin.msg("no_pixelmon")); return true; }

            PokemonInfo info = hook.readPokemon(p, slot - 1);
            if (info == null) { p.sendMessage(plugin.msg("empty_slot")); return true; }

            items.givePokemonItem(p, info);
            p.sendMessage(plugin.msg("made_item")
                    .replace("{species-ko}", info.getSpeciesKo())
                    .replace("{level}", String.valueOf(info.level)));

            // Remove original (reflection -> command fallback)
            if (hook.removePokemon(p, slot - 1)) {
                p.sendMessage(plugin.msg("released"));
            }
            return true;
        }

        if (name.equals("pokesummon") || name.equals("포켓몬소환")) {
            items.redeemInHand(p);
            return true;
        }

        return true;
    }
}
