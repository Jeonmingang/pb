
package com.minkang.pbbridge;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PBBridge extends JavaPlugin {

    private static PBBridge instance;
    private FileConfiguration cfg;

    public static PBBridge get() { return instance; }
    public FileConfiguration cfg() { return cfg; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        cfg = getConfig();
        getCommand("pbconvert").setExecutor(new PBConvertCommand());
        getCommand("포켓사진").setExecutor(new PoketPhotoCommand());
        if (cfg.getBoolean("interceptPokeitem", true)) {
            getServer().getPluginManager().registerEvents(new ProxyListener(), this);
        }
        getLogger().info("[PBBridge] Enabled. interceptPokeitem=" + cfg.getBoolean("interceptPokeitem", true));
    }

    @Override
    public void onDisable() { }

    public static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    public static void dispatchAsConsole(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
