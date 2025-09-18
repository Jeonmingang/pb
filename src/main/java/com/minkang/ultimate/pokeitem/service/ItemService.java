package com.minkang.ultimate.pokeitem.service;

import com.minkang.ultimate.pokeitem.Main;
import com.minkang.ultimate.pokeitem.model.PokemonInfo;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemService {

    private final Main plugin;
    private final PixelmonHook hook;

    private final NamespacedKey KEY_SPEC;
    private final NamespacedKey KEY_TOKEN;
    private final NamespacedKey KEY_OWNER;

    public ItemService(Main plugin, PixelmonHook hook) {
        this.plugin = plugin;
        this.hook = hook;
        KEY_SPEC = new NamespacedKey(plugin, "poke_spec");
        KEY_TOKEN = new NamespacedKey(plugin, "token");
        KEY_OWNER = new NamespacedKey(plugin, "owner");
    }

    public void givePokemonItem(Player p, PokemonInfo info) {
        ItemStack is = makeItem(p, info);
        p.getInventory().addItem(is);
    }

    private ItemStack makeItem(Player p, PokemonInfo info) {
        ConfigurationSection itemSec = plugin.getConfig().getConfigurationSection("item");
        Material material = Material.valueOf(itemSec.getString("material","BOOK"));
        ItemStack is = new ItemStack(material);
        ItemMeta meta = is.getItemMeta();
        if (itemSec.getInt("custom-model-data",0) != 0) {
            meta.setCustomModelData(itemSec.getInt("custom-model-data"));
        }

        String name = applyPlaceholders(itemSec.getString("name"), p, info).replace("&","§");
        meta.setDisplayName(name);

        List<String> loreOut = new ArrayList<>();
        for (String line : itemSec.getStringList("lore")) {
            loreOut.add(applyPlaceholders(line, p, info).replace("&","§"));
        }
        // 숨김 SPEC 백업(로어 인식용)
        String spec = toSafeSpec(info);
        loreOut.add("§0SPEC:" + spec);
        meta.setLore(loreOut);

        // NBT 태그
        String token = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(KEY_SPEC, PersistentDataType.STRING, spec);
        meta.getPersistentDataContainer().set(KEY_TOKEN, PersistentDataType.STRING, token);
        meta.getPersistentDataContainer().set(KEY_OWNER, PersistentDataType.STRING, p.getUniqueId().toString());

        is.setItemMeta(meta);
        return is;
    }

    // 스펙은 최소만(안전) : dex/level/nature/gender/growth/ivs/evs/shiny
    private String toSafeSpec(PokemonInfo info) {
        StringBuilder sb = new StringBuilder();
        if (info.dex > 0) sb.append("dex:").append(info.dex);
        else if (info.species != null) sb.append("species:").append(info.species);
        else sb.append("random");
        sb.append(",level:").append(Math.max(1, info.level));
        if (info.nature != null) sb.append(",nature:").append(info.nature);
        if (info.gender != null) sb.append(",gender:").append(info.gender);
        if (info.growth != null) sb.append(",growth:").append(info.growth);
        sb.append(",ivs:").append(info.iv[0]).append(",").append(info.iv[1]).append(",").append(info.iv[2]).append(",")
                .append(info.iv[3]).append(",").append(info.iv[4]).append(",").append(info.iv[5]);
        sb.append(",evs:").append(info.ev[0]).append(",").append(info.ev[1]).append(",").append(info.ev[2]).append(",")
                .append(info.ev[3]).append(",").append(info.ev[4]).append(",").append(info.ev[5]);
        if (info.shiny) sb.append(",shiny:true");
        return sb.toString();
    }

    private String applyPlaceholders(String s, Player p, PokemonInfo info) {
        if (s == null) return "";
        StringBuilder mv = new StringBuilder();
        for (int i=0;i<4;i++) {
            if (info.moves[i] != null && !info.moves[i].isEmpty()) {
                if (mv.length() > 0) mv.append(", ");
                mv.append(clean(info.moves[i]));
            }
        }
        String abilityClean = clean(info.ability);
        return s.replace("{species-ko}", info.getSpeciesKo())
                .replace("{level}", String.valueOf(info.level))
                .replace("{nickname}", info.nickname == null ? "없음" : info.nickname)
                .replace("{hatch}", "-1")
                .replace("{nature-ko}", info.natureKo == null ? "알수없음" : info.natureKo)
                .replace("{ability}", info.ability == null ? "없음" : info.ability)
                .replace("{ability-clean}", abilityClean == null || abilityClean.isEmpty() ? "없음" : abilityClean)
                .replace("{moves}", mv.length()==0 ? "없음" : mv.toString())
                .replace("{moves-clean}", mv.length()==0 ? "없음" : mv.toString())
                .replace("{iv-hp}", String.valueOf(info.iv[0]))
                .replace("{iv-atk}", String.valueOf(info.iv[1]))
                .replace("{iv-def}", String.valueOf(info.iv[2]))
                .replace("{iv-spa}", String.valueOf(info.iv[3]))
                .replace("{iv-spd}", String.valueOf(info.iv[4]))
                .replace("{iv-spe}", String.valueOf(info.iv[5]))
                .replace("{iv-sum}", String.valueOf(info.ivSum()))
                .replace("{ev-hp}", String.valueOf(info.ev[0]))
                .replace("{ev-atk}", String.valueOf(info.ev[1]))
                .replace("{ev-def}", String.valueOf(info.ev[2]))
                .replace("{ev-spa}", String.valueOf(info.ev[3]))
                .replace("{ev-spd}", String.valueOf(info.ev[4]))
                .replace("{ev-spe}", String.valueOf(info.ev[5]))
                .replace("{ev-sum}", String.valueOf(info.evSum()))
                .replace("{gender-ko}", info.genderKo == null ? "무성" : info.genderKo)
                .replace("{growth-ko}", info.growthKo == null ? "없음" : info.growthKo)
                .replace("{friendship}", String.valueOf(info.friendship))
                .replace("{owner}", p.getName());
    }

    private String clean(String raw) {
        if (raw == null) return null;
        String s = raw;
        int b = s.indexOf('['); if (b > 0) s = s.substring(0, b);
        s = s.replaceAll("^.*\\.", ""); // package prefix 제거
        s = s.replaceAll("@.*$", "");   // @hash 제거
        s = s.replaceAll("\\(.*?\\)", ""); // 괄호 내용 제거
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    public void redeemInHand(Player p) {
        ItemStack is = p.getInventory().getItemInMainHand();
        if (is == null || is.getType() == Material.AIR || !is.hasItemMeta()) { p.sendMessage(plugin.msg("redeem_no_tag")); return; }
        ItemMeta meta = is.getItemMeta();
        String spec = meta.getPersistentDataContainer().get(KEY_SPEC, PersistentDataType.STRING);
        String token = meta.getPersistentDataContainer().get(KEY_TOKEN, PersistentDataType.STRING);
        String owner = meta.getPersistentDataContainer().get(KEY_OWNER, PersistentDataType.STRING);

        // 로어 백업 스펙 사용
        if (spec == null && meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (line != null && line.startsWith("§0SPEC:")) { spec = line.substring("§0SPEC:".length()); break; }
            }
        }
        if (spec == null || token == null) { p.sendMessage(plugin.msg("redeem_no_tag")); return; }

        boolean bind = plugin.getConfig().getBoolean("bind-owner", true);
        boolean allowTrade = plugin.getConfig().getBoolean("allow-trade", false);
        if (bind && !allowTrade) {
            if (owner != null && !owner.equalsIgnoreCase(p.getUniqueId().toString())) {
                p.sendMessage(plugin.msg("redeem_owner_only").replace("{owner}", owner));
                return;
            }
        }

        // 소환: pokegive로 안전하게
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pokegive " + p.getName() + " " + spec);
        } catch (Exception e) {
            p.sendMessage("§c소환 실패: pokegive 명령을 사용할 수 없습니다.");
            return;
        }

        if (plugin.getConfig().getBoolean("consume-on-redeem", true)) {
            int amt = is.getAmount() - 1;
            if (amt <= 0) p.getInventory().setItemInMainHand(null);
            else { is.setAmount(amt); p.getInventory().setItemInMainHand(is); }
        }

        if (plugin.getConfig().getBoolean("duplicate-prevention", true)) {
            RedeemStore.markUsed(plugin, token);
        }

        p.sendMessage(plugin.msg("redeemed").replace("{species-ko}", "포켓몬"));
    }
}
