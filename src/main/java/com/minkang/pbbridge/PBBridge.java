package com.minkang.pbbridge;

import com.minkang.pbbridge.command.PBCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class PBBridge extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("pb").setExecutor(new PBCommand(this));
    }
}
