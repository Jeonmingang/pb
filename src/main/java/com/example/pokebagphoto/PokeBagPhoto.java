
package com.example.pokebagphoto;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.*;

public class PokeBagPhoto extends JavaPlugin implements Listener, TabExecutor {
    public static final String ITEM_FLAG = "pb-photo";
    private NamespacedKey kType, kSNBT, kOrigSlot, kTargetSlot, kName;

    @Override
    public void onEnable() {
        kType = new NamespacedKey(this, "type");
        kSNBT = new NamespacedKey(this, "snbt");
        kOrigSlot = new NamespacedKey(this, "origslot");
        kTargetSlot = new NamespacedKey(this, "targetslot");
        kName = new NamespacedKey(this, "pokename");

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("pb").setExecutor(this);
        getCommand("pb").setTabCompleter(this);
        getLogger().info("PokeBagPhoto(NoPC) enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) sender;
        int slot = 1;
        int targetSlot = 1;
        if (args.length >= 1) try { slot = Integer.parseInt(args[0]); } catch (Exception ignore) {}
        if (args.length >= 2) try { targetSlot = Integer.parseInt(args[1]); } catch (Exception ignore) {}
        if (slot < 1 || slot > 6) { p.sendMessage(ChatColor.RED + "슬롯은 1~6."); return true; }
        if (targetSlot < 1 || targetSlot > 6) targetSlot = slot;

        try {
            // Serialize + remove from party
            PokemonSnap snap = takePhoto(p, slot);
            if (snap == null) { p.sendMessage(ChatColor.RED + "그 슬롯에 포켓몬이 없음."); return true; }
            ItemStack photo = makePhotoItem(snap, targetSlot);
            Map<Integer, ItemStack> left = p.getInventory().addItem(photo);
            if (!left.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), photo);
            p.sendMessage(ChatColor.GREEN + "포켓몬을 사진 아이템으로 저장 완료! (" + snap.displayName + ")");
        } catch (Throwable t) {
            t.printStackTrace();
            p.sendMessage(ChatColor.RED + "실패: Pixelmon NBT 직렬화 호출 오류.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("1","2","3","4","5","6");
        if (args.length == 2) return Arrays.asList("1","2","3","4","5","6");
        return Collections.emptyList();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it == null || it.getType() == Material.AIR) return;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;
        String type = meta.getPersistentDataContainer().get(kType, PersistentDataType.STRING);
        if (!ITEM_FLAG.equals(type)) return;

        e.setCancelled(true);
        String snbt = meta.getPersistentDataContainer().get(kSNBT, PersistentDataType.STRING);
        Integer orig = meta.getPersistentDataContainer().get(kOrigSlot, PersistentDataType.INTEGER);
        Integer targetSlot = meta.getPersistentDataContainer().get(kTargetSlot, PersistentDataType.INTEGER);
        if (targetSlot == null) targetSlot = (orig != null ? orig : 1);

        try {
            boolean ok = restorePhoto(p, snbt, targetSlot);
            if (ok) {
                it.setAmount(it.getAmount() - 1);
                p.sendMessage(ChatColor.GREEN + "사진 복구 완료! 슬롯 " + targetSlot);
            } else {
                p.sendMessage(ChatColor.RED + "복구 실패: 대상 슬롯이 차있거나 데이터 손상.");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            p.sendMessage(ChatColor.RED + "복구 중 오류(콘솔 확인).");
        }
    }

    // ---- Core: serialize/deserialize via reflection ----
    static class PokemonSnap {
        String displayName;
        String snbt; // stringified NBT (SNBT)
        int originalSlot;
        PokemonSnap(String name, String snbt, int slot) { this.displayName = name; this.snbt = snbt; this.originalSlot = slot; }
    }

    private Class<?> storageProxy() throws ClassNotFoundException {
        return Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
    }
    private Class<?> playerPartyStorage() throws ClassNotFoundException {
        return Class.forName("com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage");
    }
    private Class<?> pokemonClass() throws ClassNotFoundException {
        return Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon");
    }
    private Class<?> pokemonFactory() throws ClassNotFoundException {
        return Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory");
    }

    private Object getParty(Player p) throws Throwable {
        Method m = storageProxy().getMethod("getParty", java.util.UUID.class);
        return m.invoke(null, p.getUniqueId());
    }

    private static Class<?> compoundTagClass() throws ClassNotFoundException {
        String[] cands = new String[]{
                "net.minecraft.nbt.CompoundNBT",
                "net.minecraft.nbt.NBTTagCompound",
                "net.minecraft.nbt.CompoundTag"
        };
        for (String s : cands) {
            try { return Class.forName(s); } catch (ClassNotFoundException ignore) {}
        }
        throw new ClassNotFoundException("CompoundNBT/CompoundTag not found");
    }
    private static Class<?> jsonToNbtClass() throws ClassNotFoundException {
        String[] cands = new String[]{"net.minecraft.nbt.JsonToNBT", "net.minecraft.nbt.TagParser"};
        for (String s : cands) { try { return Class.forName(s); } catch (ClassNotFoundException ignore) {} }
        throw new ClassNotFoundException("JsonToNBT/TagParser not found");
    }

    private static Object newCompound() throws Throwable {
        Class<?> c = compoundTagClass();
        try {
            Constructor<?> k = c.getDeclaredConstructor();
            k.setAccessible(true);
            return k.newInstance();
        } catch (NoSuchMethodException e) {
            // Some mappings use static factory: new CompoundTag()
            return c.newInstance();
        }
    }

    private static String compoundToSNBT(Object compound) {
        return String.valueOf(compound); // NBT toString() is SNBT
    }
    private static Object parseSNBT(String s) throws Throwable {
        Class<?> jc = jsonToNbtClass();
        // 1.16 MCP: JsonToNBT.getTagFromJson(String) → returns INBT (CompoundNBT)
        // Mojmap: TagParser.parseTag(String)
        try {
            Method m = jc.getMethod("getTagFromJson", String.class);
            return m.invoke(null, s);
        } catch (NoSuchMethodException ex) {
            Method m = jc.getMethod("parseTag", String.class);
            return m.invoke(null, s);
        }
    }

    private static Object call(Object obj, String name, Class<?>[] sig, Object... args) throws Throwable {
        Method m = obj.getClass().getMethod(name, sig);
        return m.invoke(obj, args);
    }

    private static Method findMethod(Class<?> cls, String[] names, Class<?>... sig) {
        for (String n : names) {
            try { return cls.getMethod(n, sig); } catch (NoSuchMethodException ignore) {}
        }
        return null;
    }

    private PokemonSnap takePhoto(Player p, int slot) throws Throwable {
        Object party = getParty(p);
        Object poke = call(party, "get", new Class<?>[]{int.class}, slot - 1);
        if (poke == null) return null;

        // name for display
        String name;
        try {
            name = String.valueOf(call(poke, "getDisplayName", new Class<?>[]{}));
        } catch (Throwable t) {
            name = String.valueOf(call(poke, "getSpecies", new Class<?>[]{}));
        }

        // write to Compound
        Class<?> pokCls = pokemonClass();
        Class<?> tagCls = compoundTagClass();
        Object tag = newCompound();

        String[] writeNames = new String[]{"writeToNBT", "writeToNBTCompound", "write", "save", "saveToNBT", "toNBT", "serializeNBT"};
        Method writer = null;
        for (String n : writeNames) {
            try {
                writer = pokCls.getMethod(n, tagCls);
                break;
            } catch (NoSuchMethodException ignore) {}
        }
        if (writer == null) {
            // maybe returns a tag
            for (String n : writeNames) {
                try {
                    writer = pokCls.getMethod(n);
                    Object out = writer.invoke(poke);
                    if (tagCls.isInstance(out)) {
                        tag = out;
                        writer = null;
                        break;
                    }
                } catch (NoSuchMethodException ignore) {}
            }
            if (writer != null) writer.invoke(poke, tag);
        } else {
            writer.invoke(poke, tag);
        }

        String snbt = compoundToSNBT(tag);

        // clear party slot
        call(party, "set", new Class<?>[]{int.class, pokCls}, slot - 1, null);

        return new PokemonSnap(name, snbt, slot);
    }

    private boolean restorePhoto(Player p, String snbt, int targetSlot) throws Throwable {
        Object party = getParty(p);
        Object existing = call(party, "get", new Class<?>[]{int.class}, targetSlot - 1);
        if (existing != null) return false;

        // parse SNBT -> Compound
        Object tag = parseSNBT(snbt);

        // create Pokemon from NBT
        Class<?> factory = pokemonFactory();
        Class<?> tagCls = compoundTagClass();
        Object pokemon = null;
        String[] createNames = new String[]{"create", "fromNBT", "recreate", "from"};
        Method ctor = null;
        for (String n : createNames) {
            try {
                ctor = factory.getMethod(n, tagCls);
                pokemon = ctor.invoke(null, tag);
                break;
            } catch (NoSuchMethodException ignore) {}
        }
        if (pokemon == null) {
            // fallback: try instance method on Pokemon like "readFromNBT"
            Class<?> pokCls = pokemonClass();
            Object tmp = pokCls.getMethod("create").invoke(null); // may not exist
            Method reader = findMethod(pokCls, new String[]{"readFromNBT","load","deserializeNBT"}, tagCls);
            if (reader != null && tmp != null) {
                reader.invoke(tmp, tag);
                pokemon = tmp;
            }
        }
        if (pokemon == null) return false;

        call(party, "set", new Class<?>[]{int.class, pokemonClass()}, targetSlot - 1, pokemon);
        return true;
    }

    private ItemStack makePhotoItem(PokemonSnap snap, int targetSlot) {
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.LIGHT_PURPLE + "포켓 사진: " + ChatColor.WHITE + snap.displayName);
        List<String> lore = Arrays.asList(
                ChatColor.GRAY + "SNBT size: " + (snap.snbt != null ? snap.snbt.length() : 0),
                ChatColor.YELLOW + "우클릭: 슬롯 " + targetSlot + "에 복구"
        );
        m.setLore(lore);
        m.getPersistentDataContainer().set(kType, PersistentDataType.STRING, ITEM_FLAG);
        m.getPersistentDataContainer().set(kSNBT, PersistentDataType.STRING, snap.snbt);
        m.getPersistentDataContainer().set(kOrigSlot, PersistentDataType.INTEGER, snap.originalSlot);
        m.getPersistentDataContainer().set(kTargetSlot, PersistentDataType.INTEGER, targetSlot);
        m.getPersistentDataContainer().set(kName, PersistentDataType.STRING, snap.displayName);
        it.setItemMeta(m);
        return it;
    }
}
