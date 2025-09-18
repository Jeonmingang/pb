package com.minkang.ultimate.pokeitem.service;

import com.minkang.ultimate.pokeitem.Main;
import com.minkang.ultimate.pokeitem.model.PokemonInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
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
            plugin.getLogger().info("Pixelmon API detected. Reflection hook active.");
        } else {
            plugin.getLogger().warning("Pixelmon API NOT found. Only redeem via spec string will work if pokegive command exists.");
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

    public boolean isPixelmonPresent() {
        return present;
    }

    public PokemonInfo readPokemon(Player p, int slot) {
        if (!present) return null;
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = storageProxy.getMethod("getParty", UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return null;

            // Try methods: get, getPokemon
            Method get = null;
            for (String name : new String[]{"get", "getPokemon"}) {
                try {
                    get = party.getClass().getMethod(name, int.class);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (get == null) return null;
            Object pokemon = get.invoke(party, slot);
            if (pokemon == null) return null;

            PokemonInfo info = new PokemonInfo();
            info.ownerName = p.getName();
            info.ownerUUID = p.getUniqueId();

            // species
            try {
                Method getSpecies = pokemon.getClass().getMethod("getSpecies");
                Object speciesObj = getSpecies.invoke(pokemon);
                String speciesName = speciesObj.toString();
                info.species = speciesName.toLowerCase(Locale.ENGLISH);
                info.speciesKo = speciesName; // best-effort; localization not trivial
            } catch (Exception e) {
                info.species = "unknown";
                info.speciesKo = "알수없음";
            }

            // level
            try {
                Method m = null;
                try { m = pokemon.getClass().getMethod("getLevel"); } catch (NoSuchMethodException ignored) {}
                if (m == null) try { m = pokemon.getClass().getMethod("getPokemonLevel"); } catch (NoSuchMethodException ignored) {}
                if (m != null) {
                    Object lv = m.invoke(pokemon);
                    info.level = (lv instanceof Number) ? ((Number) lv).intValue() : 1;
                } else info.level = 1;
            } catch (Exception e) { info.level = 1; }

            // nickname
            try {
                Method m = pokemon.getClass().getMethod("getNickname");
                Object nn = m.invoke(pokemon);
                info.nickname = nn != null ? nn.toString() : "";
            } catch (Exception ignored) {}

            // nature
            try {
                Method m = pokemon.getClass().getMethod("getNature");
                Object nature = m.invoke(pokemon);
                info.nature = nature.toString().toLowerCase(Locale.ENGLISH);
                info.natureKo = nature.toString();
            } catch (Exception ignored) {}

            // gender
            try {
                Method m = pokemon.getClass().getMethod("getGender");
                Object gender = m.invoke(pokemon);
                info.gender = gender.toString().toLowerCase(Locale.ENGLISH);
                info.genderKo = gender.toString();
            } catch (Exception ignored) {}

            // growth
            try {
                Method m = pokemon.getClass().getMethod("getGrowth");
                Object growth = m.invoke(pokemon);
                info.growth = growth.toString().toLowerCase(Locale.ENGLISH);
                info.growthKo = growth.toString();
            } catch (Exception ignored) {}

            // ability
            try {
                Method m = pokemon.getClass().getMethod("getAbility");
                Object ability = m.invoke(pokemon);
                if (ability != null) info.ability = ability.toString();
            } catch (Exception ignored) {}

            // shiny
            try {
                Method m = pokemon.getClass().getMethod("isShiny");
                Object shiny = m.invoke(pokemon);
                info.shiny = shiny instanceof Boolean && (Boolean) shiny;
            } catch (Exception ignored) {}

            // friendship
            try {
                Method m = pokemon.getClass().getMethod("getFriendship");
                Object f = m.invoke(pokemon);
                info.friendship = (f instanceof Number) ? ((Number) f).intValue() : 0;
            } catch (Exception ignored) {}

            // IVs
            fillIvsEvs(info, pokemon, true);
            // EVs
            fillIvsEvs(info, pokemon, false);

            // Moves
            try {
                Method m = pokemon.getClass().getMethod("getMoveset");
                Object moveset = m.invoke(pokemon);
                if (moveset != null) {
                    for (int i=0;i<4;i++) {
                        try {
                            Method getMove = moveset.getClass().getMethod("get", int.class);
                            Object mv = getMove.invoke(moveset, i);
                            if (mv != null) info.moves[i] = mv.toString();
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            return info;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read Pixelmon via reflection: " + e.getMessage());
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
        // Attempt via API; fallback to release command (dangerous but acceptable if spec stored).
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = storageProxy.getMethod("getParty", UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return false;

            Method set = null;
            try {
                set = party.getClass().getMethod("set", int.class, Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon"));
            } catch (Exception ignored) {}
            if (set != null) {
                set.invoke(party, slot, new Object[]{null});
                return true;
            }
        } catch (Exception ignored) { }

        // Fallback: release via command
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pokerelease " + p.getName() + " " + (slot+1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean givePokemon(Player p, PokemonInfo info) {
        String spec = info.toSpecString();
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pokegive " + p.getName() + " " + spec);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to dispatch pokegive: " + e.getMessage());
            return false;
        }
    }
}
