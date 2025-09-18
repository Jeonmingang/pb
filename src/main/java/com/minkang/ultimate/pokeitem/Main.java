package com.minkang.ultimate.pokeitem;

import com.minkang.ultimate.pokeitem.commands.ItemizeCommand;
import com.minkang.ultimate.pokeitem.listeners.RedeemListener;
import com.minkang.ultimate.pokeitem.service.ItemService;
import com.minkang.ultimate.pokeitem.service.PixelmonHook;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Main extends JavaPlugin {

    private PixelmonHook pixelmonHook;
    private ItemService itemService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        pixelmonHook = new PixelmonHook(this);
        itemService = new ItemService(this, pixelmonHook);

        if (getCommand("pokeitem") != null)
            getCommand("pokeitem").setExecutor(new ItemizeCommand(this, pixelmonHook, itemService));
        if (getCommand("pokesummon") != null)
            getCommand("pokesummon").setExecutor(new ItemizeCommand(this, pixelmonHook, itemService));

        getServer().getPluginManager().registerEvents(new RedeemListener(this, itemService), this);
    }

    public String msg(String key) {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        FileConfiguration messages = YamlConfiguration.loadConfiguration(messagesFile);
        String raw = messages.getString(key, key);
        String prefix = messages.getString("prefix", "");
        return raw.replace("{prefix}", prefix).replace("&", "§");
    }

    public ItemService getItemService() {
        return itemService;
    }
}
