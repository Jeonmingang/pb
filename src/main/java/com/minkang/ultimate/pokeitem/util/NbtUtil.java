package com.minkang.ultimate.pokeitem.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class NbtUtil {
    public static void setString(ItemMeta meta, NamespacedKey key, String value) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key, PersistentDataType.STRING, value);
    }
    public static String getString(ItemMeta meta, NamespacedKey key) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(key, PersistentDataType.STRING) ? pdc.get(key, PersistentDataType.STRING) : null;
    }
}
