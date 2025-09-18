package com.minkang.ultimate.pokeitem;

import com.minkang.ultimate.pokeitem.commands.ItemizeCommand;
import com.minkang.ultimate.pokeitem.listeners.RedeemListener;
import com.minkang.ultimate.pokeitem.service.ItemService;
import com.minkang.ultimate.pokeitem.service.PixelmonHook;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Main extends JavaPlugin {

    private PixelmonHook pixelmonHook;
    private ItemService itemService;

    private FileConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        pixelmonHook = new PixelmonHook(this);
        itemService = new ItemService(this, pixelmonHook);

        getCommand("포켓몬아이템화").setExecutor(new ItemizeCommand(this, pixelmonHook, itemService));
        getCommand("포켓몬소환").setExecutor(new ItemizeCommand(this, pixelmonHook, itemService));

        getServer().getPluginManager().registerEvents(new RedeemListener(this, itemService), this);
    }

    public String msg(String key) {
        String raw = messages.getString(key, key);
        String prefix = messages.getString("prefix", "");
        return raw.replace("{prefix}", prefix).replace("&", "§");
    }

    public ItemService getItemService() {
        return itemService;
    }
}
