package com.minkang.ultimate.pocketphoto.util;

import org.bukkit.entity.Player;
import java.lang.reflect.Method;

public class PixelmonSpecUtil {

    public static class Result {
        public boolean success;
        public String message;

        public String species = "Unknown";
        public String nickname = "";
        public int level = 1;
        public String gender = "무성";
        public String nature = "불명";
        public String ability = "불명";
        public String ivs = "?";
        public String evs = "0 0 0 0 0 0";
        public String statsLine = "";
        public String growth = "불명";
        public boolean shiny = false;
        public String form = "";
        public int friendship = 0;
        public String ball = "";
        public int hp = 0;
        public int hatchProgress = -1;
        public boolean neutered = false; // 중성화 여부

        public java.util.List<String> moves = new java.util.ArrayList<>();
        public java.util.List<Integer> movePP = new java.util.ArrayList<>();
        public java.util.List<Integer> moveMaxPP = new java.util.ArrayList<>();

        public int slot = 1;
        public String specString = "";
        public String nbtBase64 = "";

        public java.util.Map<String, Object> toJsonForItem() {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("slot", slot);
            m.put("spec", specString);
            m.put("species", species);
            m.put("level", level);
            m.put("nickname", nickname);
            m.put("gender", gender);
            m.put("nature", nature);
            m.put("ability", ability);
            m.put("ivs", ivs);
            m.put("evs", evs);
            m.put("shiny", shiny);
            m.put("moves", moves);
            m.put("friendship", friendship);
            m.put("ball", ball);
            m.put("growth", growth);
            m.put("form", form);
            m.put("hp", hp);
            m.put("hatch", hatchProgress);
            m.put("neutered", neutered);
            m.put("stats", statsLine);
            m.put("pp", movePP);
            m.put("maxpp", moveMaxPP);
            m.put("nbt", nbtBase64);
            return m;
        }
    }

    public static Result extractAndRemovePartyPokemonWithNBT(Player p, int slot1to6) {
        Result r = new Result();
        r.slot = slot1to6;
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = storageProxy.getMethod("getParty", java.util.UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) { r.success=false; r.message="파티를 가져오지 못했습니다."; return r; }
            int index = slot1to6 - 1;
            Method get = party.getClass().getMethod("get", int.class);
            Object pokemon = get.invoke(party, index);
            if (pokemon == null) { r.success=false; r.message="해당 슬롯에 포켓몬이 없습니다."; return r; }

            fillInfoFromPokemon(pokemon, r);
            r.specString = buildSpecStringFromPokemon(pokemon, r);
            r.nbtBase64 = tryWriteNbtBase64(pokemon);

            try {
                Method set = party.getClass().getMethod("set", int.class, Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon"));
                set.invoke(party, new Object[]{index, null});
            } catch (Throwable ignored) {}

            r.success = true; r.message="OK"; return r;
        } catch (Throwable t) {
            r.success=false; r.message="Pixelmon API 접근 실패: " + t.getClass().getSimpleName(); return r;
        }
    }

    public static boolean restoreFromNBT(Player p, String base64, int slot) {
        try {
            byte[] data = java.util.Base64.getDecoder().decode(base64);
            Class<?> nbtCls = Class.forName("net.minecraft.nbt.CompoundNBT");
            Object tag;
            try {
                Class<?> cst = Class.forName("net.minecraft.nbt.CompressedStreamTools");
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
                Method read = cst.getMethod("readCompressed", java.io.InputStream.class);
                tag = read.invoke(null, bais);
            } catch (Throwable t) {
                tag = nbtCls.getConstructor().newInstance();
            }

            Object poke = null;
            try {
                Class<?> factory = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonFactory");
                Method m = factory.getMethod("create", nbtCls);
                poke = m.invoke(null, tag);
            } catch (Throwable t1) {
                try {
                    Class<?> pokemonCls = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon");
                    Method m = pokemonCls.getMethod("readFromNBT", nbtCls);
                    poke = m.invoke(null, tag);
                } catch (Throwable ignored) {}
            }
            if (poke == null) return false;

            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = storageProxy.getMethod("getParty", java.util.UUID.class);
            Object party = getParty.invoke(null, p.getUniqueId());
            if (party == null) return false;
            Method set = party.getClass().getMethod("set", int.class, Class.forName("com.pixelmonmod.pixelmon.api.pokemon.Pokemon"));
            set.invoke(party, slot - 1, poke);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String tryWriteNbtBase64(Object pokemon) {
        try {
            Class<?> nbtCls = Class.forName("net.minecraft.nbt.CompoundNBT");
            Object tag = nbtCls.getConstructor().newInstance();
            boolean wrote = false;

            String[] methods = new String[]{"writeToNBT", "writeNBT", "save", "write"};
            for (String mName : methods) {
                try {
                    Method m = pokemon.getClass().getMethod(mName, nbtCls);
                    m.invoke(pokemon, tag);
                    wrote = true; break;
                } catch (Throwable ignored) {}
            }
            if (!wrote) {
                try {
                    Class<?> serializer = Class.forName("com.pixelmonmod.pixelmon.api.pokemon.PokemonSerializer");
                    Method m = serializer.getMethod("write", pokemon.getClass(), nbtCls);
                    m.invoke(null, pokemon, tag);
                    wrote = true;
                } catch (Throwable ignored) {}
            }
            if (!wrote) return "";

            try {
                Class<?> cst = Class.forName("net.minecraft.nbt.CompressedStreamTools");
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                Method write = cst.getMethod("writeCompressed", nbtCls, java.io.OutputStream.class);
                write.invoke(null, tag, baos);
                return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Throwable t) {
                return "";
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private static void fillInfoFromPokemon(Object pokemon, Result r) throws Exception {
        try { Object o = pokemon.getClass().getMethod("getSpecies").invoke(pokemon);
            String name = o.toString();
            try { Object nm = o.getClass().getMethod("getName").invoke(o); if (nm != null) name = nm.toString(); } catch (Throwable ignored) {}
            r.species = name;
        } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getNickname").invoke(pokemon); if (o != null) r.nickname = o.toString(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getLevel").invoke(pokemon); if (o instanceof Number) r.level = ((Number)o).intValue(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getGender").invoke(pokemon); if (o != null) r.gender = o.toString(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getNature").invoke(pokemon); if (o != null) r.nature = o.toString(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getAbility").invoke(pokemon); if (o != null) r.ability = o.toString(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("isShiny").invoke(pokemon); if (o instanceof Boolean) r.shiny = (Boolean) o; } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getFriendship").invoke(pokemon); if (o instanceof Number) r.friendship = ((Number) o).intValue(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getGrowth").invoke(pokemon); if (o != null) r.growth = o.toString(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getForm").invoke(pokemon); if (o != null) r.form = o.toString(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getCaughtBall").invoke(pokemon); if (o != null) r.ball = o.toString(); } catch (Throwable ignored) {}
        try { Object o = pokemon.getClass().getMethod("getEggSteps").invoke(pokemon); if (o instanceof Number) r.hatchProgress = ((Number) o).intValue(); } catch (Throwable ignored) {}
        // 중성화 여부 추출: 여러 메서드명을 시도 (서버/버전에 따라 다름)
        String[] neuters = new String[]{"isNeutered","getNeutered","isSterilized","getSterilized","isInfertile","isUnbreedable"};
        for (String m : neuters) {
            try {
                Object v = pokemon.getClass().getMethod(m).invoke(pokemon);
                if (v instanceof Boolean) { r.neutered = ((Boolean) v); break; }
            } catch (Throwable ignored) {}
        }

        r.statsLine = readStatsLine(pokemon);
        r.hp = readStat(pokemon, "getHP", "getHealth");
        r.ivs = readStats(pokemon, true);
        r.evs = readStats(pokemon, false);
        readMoves(pokemon, r);
    }

    private static int readStat(Object pokemon, String... methodNames) {
        for (String mName : methodNames) {
            try { Object o = pokemon.getClass().getMethod(mName).invoke(pokemon);
                if (o instanceof Number) return ((Number) o).intValue();
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static String readStats(Object pokemon, boolean iv) {
        try {
            Method getObj = pokemon.getClass().getMethod(iv? "getIVs" : "getEVs");
            Object stats = getObj.invoke(pokemon);
            int[] arr = new int[6];
            try {
                Method m = stats.getClass().getMethod("getArray");
                Object o = m.invoke(stats);
                if (o != null && o.getClass().isArray()) {
                    for (int i = 0; i < Math.min(java.lang.reflect.Array.getLength(o), 6); i++) {
                        Object v = java.lang.reflect.Array.get(o, i);
                        if (v instanceof Number) arr[i] = ((Number) v).intValue();
                    }
                    return join6(arr);
                }
            } catch (Throwable ignored) {}
            try {
                Method m = stats.getClass().getMethod("get", int.class);
                for (int i = 0; i < 6; i++) {
                    Object v = m.invoke(stats, i);
                    if (v instanceof Number) arr[i] = ((Number) v).intValue();
                }
                return join6(arr);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        return iv ? "31 31 31 31 31 31" : "0 0 0 0 0 0";
    }

    private static void readMoves(Object pokemon, Result r) {
        try {
            Method getMoveset = pokemon.getClass().getMethod("getMoveset");
            Object moveset = getMoveset.invoke(pokemon);
            for (int i = 0; i < 4; i++) {
                try {
                    Method get = moveset.getClass().getMethod("get", int.class);
                    Object slot = get.invoke(moveset, i);
                    if (slot == null) continue;
                    String name = null;
                    try {
                        Method getAttack = slot.getClass().getMethod("getAttack");
                        Object atk = getAttack.invoke(slot);
                        if (atk != null) {
                            try { Method getName = atk.getClass().getMethod("getDisplayName"); Object nm = getName.invoke(atk); if (nm != null) name = nm.toString(); }
                            catch (Throwable t1) { name = atk.toString(); }
                        }
                        try { Object pp = slot.getClass().getMethod("getPp").invoke(slot); if (pp instanceof Number) r.movePP.add(((Number) pp).intValue()); } catch (Throwable ignored) {}
                        try { Object max = slot.getClass().getMethod("getMaxPp").invoke(slot); if (max instanceof Number) r.moveMaxPP.add(((Number) max).intValue()); } catch (Throwable ignored) {}
                    } catch (Throwable t2) { name = slot.toString(); }
                    if (name != null) r.moves.add(name);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static String readStatsLine(Object pokemon) {
        try {
            Method getStats = pokemon.getClass().getMethod("getStats");
            Object stats = getStats.invoke(pokemon);
            int[] arr = new int[6];
            String[] names = new String[]{"getHp","getAttack","getDefense","getSpecialAttack","getSpecialDefense","getSpeed"};
            for (int i = 0; i < names.length; i++) {
                try { Object v = stats.getClass().getMethod(names[i]).invoke(stats);
                    if (v instanceof Number) arr[i] = ((Number) v).intValue();
                } catch (Throwable ignored) {}
            }
            return join6(arr);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String join6(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static String buildSpecStringFromPokemon(Object pokemon, Result r) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        parts.add(r.species);
        parts.add("level:" + r.level);
        if (r.nickname != null && !r.nickname.isEmpty()) parts.add("nickname:" + esc(r.nickname));
        if (r.gender != null && !r.gender.isEmpty()) parts.add("gender:" + r.gender);
        if (r.nature != null && !r.nature.isEmpty()) parts.add("nature:" + r.nature);
        if (r.ability != null && !r.ability.isEmpty()) parts.add("ability:" + r.ability);
        if (r.shiny) parts.add("shiny");
        if (r.ball != null && !r.ball.isEmpty()) parts.add("ball:" + r.ball);
        if (!r.moves.isEmpty()) {
            StringBuilder mv = new StringBuilder();
            for (int i = 0; i < r.moves.size(); i++) { if (i > 0) mv.append('/'); mv.append(r.moves.get(i)); }
            parts.add("moves:" + mv);
        }
        return String.join(" ", parts);
    }

    private static String esc(String s) { return '\"' + s.replace("\\\"", "'") + '\"'; }
}
