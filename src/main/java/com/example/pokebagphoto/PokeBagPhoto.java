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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PokeBagPhoto v1.1.6
 * - /pb <1-6> [targetSlot] : party slot -> photo item (no PC)
 * - Right click photo item: restore to target slot (default: original slot)
 * - Tries to use Pixelmon sprite item via reflection; falls back to map item.
 * - No Pixelmon compile-time dependency. Works on 1.16.5 (Pixelmon 9.1.x).
 */
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
        if (getCommand("pb") != null) {
            getCommand("pb").setExecutor(this);
            getCommand("pb").setTabCompleter(this);
        }
        getLogger().info("PokeBagPhoto v1.1.6 enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
        Player p = (Player) sender;
        int slot = 1, targetSlot = 1;
        if (args.length >= 1) try { slot = Integer.parseInt(args[0]); } catch (Exception ignore) {}
        if (args.length >= 2) try { targetSlot = Integer.parseInt(args[1]); } catch (Exception ignore) {}
        if (slot < 1 || slot > 6) { p.sendMessage(ChatColor.RED + "슬롯은 1~6."); return true; }
        if (targetSlot < 1 || targetSlot > 6) targetSlot = slot;

        try {
            PokemonSnap snap = takePhoto(p, slot);
            if (snap == null) { p.sendMessage(ChatColor.RED + "그 슬롯에 포켓몬이 없음."); return true; }
            ItemStack photo = makePhotoItem(snap, targetSlot); // sprite if possible
            Map<Integer, ItemStack> left = p.getInventory().addItem(photo);
            if (!left.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), photo);
            p.sendMessage(ChatColor.GREEN + "포켓몬을 사진 아이템으로 저장 완료! (" + snap.displayName + ")");
        } catch (Throwable t) {
            getLogger().warning("takePhoto failed: " + t.getClass().getName() + " " + t.getMessage());
            t.printStackTrace();
            p.sendMessage(ChatColor.RED + "실패: 직렬화 오류(콘솔 확인).");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 || args.length == 2) return Arrays.asList("1","2","3","4","5","6");
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
            getLogger().warning("restorePhoto failed: " + t.getClass().getName() + " " + t.getMessage());
            t.printStackTrace();
            p.sendMessage(ChatColor.RED + "복구 중 오류(콘솔 확인).");
        }
    }

    // ---------------- Reflection utils ----------------
    private static Object call(Object obj, String name, Class<?>[] sig, Object... args) throws Throwable {
        Method m = obj.getClass().getMethod(name, sig);
        return m.invoke(obj, args);
    }
    private static Method findMethod(Class<?> cls, String[] names, Class<?>... sig) {
        for (String n : names) { try { return cls.getMethod(n, sig); } catch (NoSuchMethodException ignore) {} }
        return null;
    }

    // Pixelmon classes
    private Class<?> storageProxy() throws ClassNotFoundException { return Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy"); }
    private Class<?> playerPartyStorage() throws ClassNotFoundException { return Class.forName("com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage"); }
    private Class<?> pokemonClass()   throws ClassNotFoundException { return Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon"); }
    private Class<?> pokemonFactory() throws ClassNotFoundException { return Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory"); }
    private Class<?> speciesEnum()    throws ClassNotFoundException {
        String[] cands = {
            "com.pixelmonmod.pixelmon.api.pokemon.species.Species",
            "com.pixelmonmod.pixelmon.entities.pixelmon.EnumSpecies"
        };
        for (String s : cands) try { return Class.forName(s); } catch (ClassNotFoundException ignore) {}
        throw new ClassNotFoundException("Species enum not found");
    }

    // NBT names bridging (MCP / Mojang)
    private static Class<?> compoundTagClass() throws ClassNotFoundException {
        String[] c = { "net.minecraft.nbt.CompoundNBT", "net.minecraft.nbt.NBTTagCompound", "net.minecraft.nbt.CompoundTag" };
        for (String s : c) try { return Class.forName(s); } catch (ClassNotFoundException ignore) {}
        throw new ClassNotFoundException("CompoundNBT/CompoundTag not found");
    }
    private static Class<?> jsonToNbtClass() throws ClassNotFoundException {
        String[] c = { "net.minecraft.nbt.JsonToNBT", "net.minecraft.nbt.TagParser" };
        for (String s : c) try { return Class.forName(s); } catch (ClassNotFoundException ignore) {}
        throw new ClassNotFoundException("JsonToNBT/TagParser not found");
    }
    private static Object parseSNBT(String s) throws Throwable {
        Class<?> jc = jsonToNbtClass();
        try { return jc.getMethod("getTagFromJson", String.class).invoke(null, s); }                      // MCP
        catch (NoSuchMethodException ex) { return jc.getMethod("parseTag", String.class).invoke(null, s);} // Mojang
    }

    private Object getParty(Player p) throws Throwable {
        Method m = storageProxy().getMethod("getParty", java.util.UUID.class);
        return m.invoke(null, p.getUniqueId());
    }

    static class PokemonSnap {
        String displayName, snbt, speciesUpper;
        int originalSlot;
        PokemonSnap(String name, String snbt, int slot, String speciesUpper) {
            this.displayName = name; this.snbt = snbt; this.originalSlot = slot; this.speciesUpper = speciesUpper;
        }
    }

    // ---------- TAKE PHOTO ----------
    private PokemonSnap takePhoto(Player p, int slot) throws Throwable {
        Object party = getParty(p);
        Object poke = call(party, "get", new Class<?>[]{int.class}, slot - 1);
        if (poke == null) return null;

        String name;
        try { name = String.valueOf(call(poke, "getDisplayName", new Class<?>[]{})); }
        catch (Throwable t) { name = String.valueOf(call(poke, "getSpecies", new Class<?>[]{})); }

        String speciesUpper = "UNKNOWN";
        try { Object sp = call(poke, "getSpecies", new Class<?>[]{}); speciesUpper = String.valueOf(sp).toUpperCase(Locale.ROOT); } catch (Throwable ignore) {}

        // serialize
        Class<?> pokCls = pokemonClass();
        Class<?> tagCls = compoundTagClass();
        Object tag;
        try { tag = tagCls.getDeclaredConstructor().newInstance(); }
        catch (Throwable x) { tag = tagCls.newInstance(); }

        String[] writeNames = {"writeToNBT", "writeToNBTCompound", "write", "save", "saveToNBT", "toNBT", "serializeNBT"};
        Method writer = null;
        for (String n : writeNames) { try { writer = pokCls.getMethod(n, tagCls); break; } catch (NoSuchMethodException ignore) {} }
        if (writer == null) {
            for (String n : writeNames) {
                try {
                    Method m = pokCls.getMethod(n);
                    Object out = m.invoke(poke);
                    if (tagCls.isInstance(out)) { tag = out; writer = null; break; }
                } catch (NoSuchMethodException ignore) {}
            }
            if (writer != null) writer.invoke(poke, tag);
        } else writer.invoke(poke, tag);

        String snbt = String.valueOf(tag);
        // remove from party
        call(party, "set", new Class<?>[]{int.class, pokCls}, slot - 1, null);

        return new PokemonSnap(name, snbt, slot, speciesUpper);
    }

    // ---------- RESTORE ----------
    private boolean restorePhoto(Player p, String snbt, int targetSlot) throws Throwable {
        if (snbt == null || snbt.isEmpty()) return false;
        Object party = getParty(p);
        Object existing = call(party, "get", new Class<?>[]{int.class}, targetSlot - 1);
        if (existing != null) return false;

        Object tag = parseSNBT(snbt);
        Class<?> tagCls = compoundTagClass();

        // 1) PokemonFactory.*(CompoundNBT) 시도
        Object pokemon = tryFactoryFromNBT(tag, tagCls);
        if (pokemon == null) {
            // 2) 종으로 생성 후 readFromNBT 로 덮기
            pokemon = tryCreateThenRead(tag, tagCls);
        }
        if (pokemon == null) return false;

        call(party, "set", new Class<?>[]{int.class, pokemonClass()}, targetSlot - 1, pokemon);
        return true;
    }

    private Object tryFactoryFromNBT(Object tag, Class<?> tagCls) throws Throwable {
        Class<?> factory = pokemonFactory();
        String[] createNames = {"fromNBT", "create", "recreate", "from"};
        for (String n : createNames) {
            try { Method m = factory.getMethod(n, tagCls); return m.invoke(null, tag); }
            catch (NoSuchMethodException ignore) {}
        }
        return null;
    }

    private Object tryCreateThenRead(Object tag, Class<?> tagCls) throws Throwable {
        String text = String.valueOf(tag);
        String speciesName = null;
        Matcher m1 = Pattern.compile("Species\s*:\s*"([^"]+)"").matcher(text);
        if (m1.find()) speciesName = m1.group(1);
        if (speciesName == null) {
            Matcher m2 = Pattern.compile("species\s*:\s*"?([A-Za-z_]+)"?").matcher(text);
            if (m2.find()) speciesName = m2.group(1);
        }
        if (speciesName == null) speciesName = "PIKACHU"; // 안전 기본값

        Class<?> sp = speciesEnum();
        Object speciesObj;
        try { speciesObj = sp.getMethod("valueOf", String.class).invoke(null, speciesName.toUpperCase(Locale.ROOT)); }
        catch (Throwable t) {
            Object tmp = null;
            String[] helpers = {"getFromName","getByName","fromName"};
            for (String h: helpers) {
                try { tmp = sp.getMethod(h, String.class).invoke(null, speciesName); break; }
                catch (NoSuchMethodException ignore) {}
            }
            speciesObj = (tmp != null) ? tmp : sp.getEnumConstants()[0];
        }

        // create(species)
        Object pokemon = null;
        Class<?> factory = pokemonFactory();
        try { pokemon = factory.getMethod("create", sp).invoke(null, speciesObj); }
        catch (NoSuchMethodException e) {
            for (Method mm: factory.getMethods())
                if (mm.getName().equals("create") && mm.getParameterCount()==1 && mm.getParameterTypes()[0].isEnum())
                    { pokemon = mm.invoke(null, speciesObj); break; }
        }
        if (pokemon == null) return null;

        // readFromNBT / load / deserializeNBT
        Class<?> pokCls = pokemonClass();
        Method reader = findMethod(pokCls, new String[]{"readFromNBT","load","deserializeNBT"}, tagCls);
        if (reader != null) { reader.invoke(pokemon, tag); return pokemon; }
        return null;
    }

    // ---------- Sprite photo ----------
    private ItemStack makePhotoItem(PokemonSnap snap, int targetSlot) throws Throwable {
        try {
            ItemStack sprite = tryMakeSpriteFromPixelmon(snap); // 성공하면 포켓몬 사진 아이템
            if (sprite != null) return decorate(sprite, snap, targetSlot);
        } catch (Throwable ignore) {}
        // 실패 시 지도 아이템
        return decorate(new ItemStack(Material.FILLED_MAP), snap, targetSlot);
    }

    private ItemStack decorate(ItemStack it, PokemonSnap snap, int targetSlot) {
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.LIGHT_PURPLE + "포켓 사진: " + ChatColor.WHITE + snap.displayName);
        m.setLore(Arrays.asList(
            ChatColor.GRAY + "SNBT size: " + (snap.snbt != null ? snap.snbt.length() : 0),
            ChatColor.YELLOW + "우클릭: 슬롯 " + targetSlot + "에 복구"
        ));
        m.getPersistentDataContainer().set(kType, PersistentDataType.STRING, ITEM_FLAG);
        m.getPersistentDataContainer().set(kSNBT, PersistentDataType.STRING, snap.snbt);
        m.getPersistentDataContainer().set(kOrigSlot, PersistentDataType.INTEGER, snap.originalSlot);
        m.getPersistentDataContainer().set(kTargetSlot, PersistentDataType.INTEGER, targetSlot);
        m.getPersistentDataContainer().set(kName, PersistentDataType.STRING, snap.displayName);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack tryMakeSpriteFromPixelmon(PokemonSnap snap) throws Throwable {
        Object tag = parseSNBT(snap.snbt);
        Object pokemon = tryFactoryFromNBT(tag, compoundTagClass());
        if (pokemon == null) pokemon = tryCreateThenRead(tag, compoundTagClass());
        if (pokemon == null) return null;

        String[][] cands = {
            {"com.pixelmonmod.pixelmon.api.util.helpers.SpriteItemHelper", "getPhoto"},
            {"com.pixelmonmod.pixelmon.items.SpriteItemHelper", "getPhoto"},
            {"com.pixelmonmod.pixelmon.items.ItemPixelmonSprite", "getPhoto"}
        };
        Object nmsItem = null;
        for (String[] c : cands) {
            try {
                Class<?> cls = Class.forName(c[0]);
                Method m = null;
                try { m = cls.getMethod(c[1], pokemonClass()); } catch (NoSuchMethodException ignore) {}
                if (m != null) { nmsItem = m.invoke(null, pokemon); if (nmsItem != null) break; }
            } catch (ClassNotFoundException ignore) {}
        }
        if (nmsItem == null) return null;

        // NMS → Bukkit 변환
        String pkg = Bukkit.getServer().getClass().getPackage().getName(); // org.bukkit.craftbukkit.v1_16_R3
        Class<?> craft = Class.forName(pkg + ".inventory.CraftItemStack");
        Method asBukkitCopy = null;
        for (Method m : craft.getMethods())
            if (m.getName().equals("asBukkitCopy") && m.getParameterCount()==1) { asBukkitCopy = m; break; }
        if (asBukkitCopy == null) return null;
        return (ItemStack) asBukkitCopy.invoke(null, nmsItem);
    }
}
