package com.minkang.ultimate.pokeitem.service;

import com.minkang.ultimate.pokeitem.Main;
import com.minkang.ultimate.pokeitem.model.PokemonInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public class PixelmonHook {

    private final Main plugin;
    private boolean present;

    public PixelmonHook(Main plugin) {
        this.plugin = plugin;
        this.present = detect();
        if (present) {
            plugin.getLogger().info("Pixelmon API detected (reflection).");
        } else {
            plugin.getLogger().warning("Pixelmon API NOT found.");
        }
    }

    private boolean detect() {
        try {
            Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isPixelmonPresent() { return present; }

    public PokemonInfo readPokemon(Player p, int slot) {
        if (!present) return null;
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = storageProxy.getMethod("getParty", UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return null;

            Method get = null;
            for (String name : new String[]{"get", "getPokemon"}) {
                try { get = party.getClass().getMethod(name, int.class); break; } catch (NoSuchMethodException ignored) {}
            }
            if (get == null) return null;
            Object pokemon = get.invoke(party, slot);
            if (pokemon == null) return null;

            PokemonInfo info = new PokemonInfo();
            info.ownerName = p.getName();
            info.ownerUUID = p.getUniqueId();

            // species & dex
            try {
                Method getSpecies = pokemon.getClass().getMethod("getSpecies");
                Object speciesObj = getSpecies.invoke(pokemon);
                String speciesName = speciesObj.toString();
                info.species = speciesName.toLowerCase(Locale.ENGLISH);
                info.speciesKo = speciesName;
                try {
                    Method getDex = speciesObj.getClass().getMethod("getDex");
                    Object dv = getDex.invoke(speciesObj);
                    if (dv instanceof Number) info.dex = ((Number) dv).intValue();
                } catch (Exception ignored) {}
            } catch (Exception e) {
                info.species = "unknown";
                info.speciesKo = "알수없음";
            }

            // level
            try {
                Method m = null;
                try { m = pokemon.getClass().getMethod("getLevel"); } catch (NoSuchMethodException ignored) {}
                if (m == null) try { m = pokemon.getClass().getMethod("getPokemonLevel"); } catch (NoSuchMethodException ignored) {}
                if (m != null) info.level = ((Number)m.invoke(pokemon)).intValue();
                else info.level = 1;
            } catch (Exception ignored) { info.level = 1; }

            // nickname
            try { Method m = pokemon.getClass().getMethod("getNickname"); Object o = m.invoke(pokemon); info.nickname = o!=null?o.toString():""; } catch (Exception ignored) {}

            // nature/gender/growth/ability/shiny/friendship
            try { Method m = pokemon.getClass().getMethod("getNature"); Object o = m.invoke(pokemon); info.nature = o.toString().toLowerCase(Locale.ENGLISH); info.natureKo = o.toString(); } catch (Exception ignored) {}
            try { Method m = pokemon.getClass().getMethod("getGender"); Object o = m.invoke(pokemon); info.gender = o.toString().toLowerCase(Locale.ENGLISH); info.genderKo = o.toString(); } catch (Exception ignored) {}
            try { Method m = pokemon.getClass().getMethod("getGrowth"); Object o = m.invoke(pokemon); info.growth = o.toString().toLowerCase(Locale.ENGLISH); info.growthKo = o.toString(); } catch (Exception ignored) {}
            try { Method m = pokemon.getClass().getMethod("getAbility"); Object o = m.invoke(pokemon); if (o != null) info.ability = o.toString(); } catch (Exception ignored) {}
            try { Method m = pokemon.getClass().getMethod("isShiny"); Object o = m.invoke(pokemon); info.shiny = (o instanceof Boolean) && ((Boolean)o); } catch (Exception ignored) {}
            try { Method m = pokemon.getClass().getMethod("getFriendship"); Object o = m.invoke(pokemon); info.friendship = (o instanceof Number) ? ((Number)o).intValue() : 0; } catch (Exception ignored) {}

            // IV/EV
            fillIvsEvs(info, pokemon, true);
            fillIvsEvs(info, pokemon, false);

            // Moves (for lore only) - best effort
            try {
                Method m = pokemon.getClass().getMethod("getMoveset");
                Object moveset = m.invoke(pokemon);
                if (moveset != null) {
                    try {
                        Method sizeM = moveset.getClass().getMethod("size");
                        int size = ((Number)sizeM.invoke(moveset)).intValue();
                        Method getM = moveset.getClass().getMethod("get", int.class);
                        for (int i=0;i<Math.min(4, size);i++) {
                            Object mv = getM.invoke(moveset, i);
                            if (mv != null) info.moves[i] = mv.toString();
                        }
                    } catch (Exception ex) {
                        // fallback 0..3
                        Method getM = moveset.getClass().getMethod("get", int.class);
                        for (int i=0;i<4;i++) {
                            try {
                                Object mv = getM.invoke(moveset, i);
                                if (mv != null) info.moves[i] = mv.toString();
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

            return info;
        } catch (Exception e) {
            plugin.getLogger().warning("readPokemon reflection failed: " + e.getMessage());
            return null;
        }
    }

    private void fillIvsEvs(PokemonInfo info, Object pokemon, boolean iv) {
        try {
            Method getter = pokemon.getClass().getMethod(iv ? "getIVs" : "getEVs");
            Object store = getter.invoke(pokemon);
            Class<?> statsEnum = Class.forName("com.pixelmonmod.pixelmon.api.stats.Stats");
            Method getStat = store.getClass().getMethod("getStat", statsEnum);

            Object HP = Enum.valueOf((Class<Enum>) statsEnum, "HP");
            Object ATTACK = Enum.valueOf((Class<Enum>) statsEnum, "ATTACK");
            Object DEFENSE = Enum.valueOf((Class<Enum>) statsEnum, "DEFENSE");
            Object SPECIAL_ATTACK = Enum.valueOf((Class<Enum>) statsEnum, "SPECIAL_ATTACK");
            Object SPECIAL_DEFENSE = Enum.valueOf((Class<Enum>) statsEnum, "SPECIAL_DEFENSE");
            Object SPEED = Enum.valueOf((Class<Enum>) statsEnum, "SPEED");

            int[] arr = new int[6];
            arr[0] = ((Number)getStat.invoke(store, HP)).intValue();
            arr[1] = ((Number)getStat.invoke(store, ATTACK)).intValue();
            arr[2] = ((Number)getStat.invoke(store, DEFENSE)).intValue();
            arr[3] = ((Number)getStat.invoke(store, SPECIAL_ATTACK)).intValue();
            arr[4] = ((Number)getStat.invoke(store, SPECIAL_DEFENSE)).intValue();
            arr[5] = ((Number)getStat.invoke(store, SPEED)).intValue();

            if (iv) info.iv = arr; else info.ev = arr;
        } catch (Exception ignored) {}
    }

    public boolean removePokemon(Player p, int slot) {
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = storageProxy.getMethod("getParty", UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return false;
            Class<?> pkClass = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon");
            Method set = party.getClass().getMethod("set", int.class, pkClass);
            set.invoke(party, slot, new Object[]{null});
            return true;
        } catch (Exception ignored) {}
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pokerelease " + p.getName() + " " + (slot+1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
