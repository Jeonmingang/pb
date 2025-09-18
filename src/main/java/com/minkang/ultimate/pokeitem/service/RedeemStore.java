package com.minkang.ultimate.pokeitem.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

class RedeemStore {

    private static File file(Plugin p) {
        return new File(p.getDataFolder(), "redeemed.yml");
    }

    static boolean isUsed(Plugin p, String token) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file(p));
        return cfg.getBoolean("used."+token, false);
    }

    static void markUsed(Plugin p, String token) {
        File f = file(p);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        cfg.set("used."+token, true);
        try { cfg.save(f); } catch (IOException ignored) {}
    }
}
