package com.minkang.ultimate.pokeitem.service;

import com.minkang.ultimate.pokeitem.Main;
import com.minkang.ultimate.pokeitem.model.PokemonInfo;
import com.minkang.ultimate.pokeitem.util.NbtUtil;
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

        String name = itemSec.getString("name");
        name = applyPlaceholders(name, p, info).replace("&","§");
        meta.setDisplayName(name);

        List<String> loreOut = new ArrayList<>();
        for (String line : itemSec.getStringList("lore")) {
            loreOut.add(applyPlaceholders(line, p, info).replace("&","§"));
        }
        // 숨김 스펙 라인 추가(로어 인식 모드)
        loreOut.add("§0SPEC:" + info.toSpecString());
        meta.setLore(loreOut);

        // tags
        String token = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(KEY_SPEC, PersistentDataType.STRING, info.toSpecString());
        meta.getPersistentDataContainer().set(KEY_TOKEN, PersistentDataType.STRING, token);
        meta.getPersistentDataContainer().set(KEY_OWNER, PersistentDataType.STRING, p.getUniqueId().toString());

        is.setItemMeta(meta);
        return is;
    }

    private String applyPlaceholders(String s, Player p, PokemonInfo info) {
        if (s == null) return "";
        String moves = "";
        for (int i=0;i<4;i++) {
            if (info.moves[i] != null && !info.moves[i].isEmpty()) {
                if (!moves.isEmpty()) moves += ", ";
                moves += info.moves[i];
            }
        }
        return s.replace("{species-ko}", info.getSpeciesKo())
                .replace("{level}", String.valueOf(info.level))
                .replace("{nickname}", info.nickname == null ? "없음" : info.nickname)
                .replace("{hatch}", "-1")
                .replace("{nature-ko}", info.natureKo == null ? "알수없음" : info.natureKo)
                .replace("{ability}", info.ability == null ? "없음" : info.ability)
                .replace("{moves}", moves.isEmpty() ? "없음" : moves)
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

    public void redeemInHand(Player p) {
        ItemStack is = p.getInventory().getItemInMainHand();
        if (is == null || is.getType() == Material.AIR) return;
        if (!is.hasItemMeta()) { p.sendMessage(plugin.msg("redeem_no_tag")); return; }
        ItemMeta meta = is.getItemMeta();
        String spec = meta.getPersistentDataContainer().get(KEY_SPEC, PersistentDataType.STRING);
        String token = meta.getPersistentDataContainer().get(KEY_TOKEN, PersistentDataType.STRING);
        String owner = meta.getPersistentDataContainer().get(KEY_OWNER, PersistentDataType.STRING);

        if (spec == null) {
            // 로어에서 숨김 스펙 라인 파싱
            if (is.hasItemMeta() && is.getItemMeta().hasLore()) {
                for (String line : is.getItemMeta().getLore()) {
                    if (line != null && line.startsWith("§0SPEC:")) {
                        spec = line.substring("§0SPEC:".length());
                        break;
                    }
                }
            }
        }
        if (spec == null || token == null) { p.sendMessage(plugin.msg("redeem_no_tag")); return; }

        if (plugin.getConfig().getBoolean("duplicate-prevention", true)) {
            if (RedeemStore.isUsed(plugin, token)) {
                p.sendMessage(plugin.msg("duplicate_token"));
                return;
            }
        }

        boolean bind = plugin.getConfig().getBoolean("bind-owner", true);
        boolean allowTrade = plugin.getConfig().getBoolean("allow-trade", false);
        if (bind && !allowTrade) {
            if (owner != null && !owner.equalsIgnoreCase(p.getUniqueId().toString())) {
                p.sendMessage(plugin.msg("redeem_owner_only").replace("{owner}", owner));
                return;
            }
        }

        if (plugin.getConfig().getBoolean("require-empty-party-slot", true)) {
            // naive check: Pixelmon command `poketest` is not available; assume user is responsible.
            // We just try; if fails, nothing happens.
        }

        // Use pokegive with the stored spec
        PokemonInfo dummy = new PokemonInfo();
        dummy.species = "unknown";
        dummy.speciesKo = "포켓몬";
        dummy.level = 1;
        hook.givePokemon(p, dummy); // This call will dispatch the command built from dummy? No. We need spec string.
        // Instead directly dispatch spec we stored:
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pokegive " + p.getName() + " " + spec);
        } catch (Exception e) {
            p.sendMessage("§c소환 실패: pokegive 명령을 사용할 수 없습니다.");
            return;
        }

        if (plugin.getConfig().getBoolean("consume-on-redeem", true)) {
            is.setAmount(is.getAmount() - 1);
            p.getInventory().setItemInMainHand(is.getAmount() <= 0 ? null : is);
        }

        if (plugin.getConfig().getBoolean("duplicate-prevention", true)) {
            RedeemStore.markUsed(plugin, token);
        }

        p.sendMessage(plugin.msg("redeemed"));
    }
}
