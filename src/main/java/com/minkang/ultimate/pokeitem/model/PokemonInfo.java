package com.minkang.ultimate.pokeitem.model;

import java.util.Arrays;
import java.util.UUID;

public class PokemonInfo {

    public String species;
    public String speciesKo;
    public int level;
    public String nature;
    public String natureKo;
    public String gender;
    public String genderKo;
    public String growth;
    public String growthKo;
    public String ability;
    public boolean shiny;
    public int friendship;
    public String nickname;
    public String[] moves = new String[4];
    public int[] iv = new int[6];
    public int[] ev = new int[6];
    public String ownerName;
    public UUID ownerUUID;

    public String toSpecString() {
        // Build a Pixelmon spec string (best-effort)
        StringBuilder sb = new StringBuilder();
        sb.append("species:").append(species);
        sb.append(",level:").append(level);
        if (nature != null) sb.append(",nature:").append(nature);
        if (gender != null) sb.append(",gender:").append(gender.toLowerCase());
        if (growth != null) sb.append(",growth:").append(growth.toLowerCase());
        if (ability != null) sb.append(",ability:").append(ability.replace(" ", ""));
        if (shiny) sb.append(",shiny:true");
        // IVs/EVs per stat
        String[] statKeys = {"hp","atk","def","spa","spd","spe"};
        for (int i=0;i<6;i++) {
            sb.append(",iv").append(statKeys[i]).append(":").append(iv[i]);
        }
        for (int i=0;i<6;i++) {
            sb.append(",ev").append(statKeys[i]).append(":").append(ev[i]);
        }
        // Moves
        for (int i=0;i<4;i++) {
            if (moves[i] != null && !moves[i].isEmpty()) {
                sb.append(",move:").append(moves[i].replace(" ", ""));
            }
        }
        if (nickname != null && !nickname.isEmpty()) {
            sb.append(",nickname:").append(nickname.replace(" ", ""));
        }
        return sb.toString();
    }

    public int ivSum() {
        int s=0; for (int v:iv) s+=v; return s;
    }
    public int evSum() {
        int s=0; for (int v:ev) s+=v; return s;
    }

    public String getSpeciesKo() { return speciesKo != null ? speciesKo : species; }
    public int getLevel() { return level; }
}
