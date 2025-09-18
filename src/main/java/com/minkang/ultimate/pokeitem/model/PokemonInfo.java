package com.minkang.ultimate.pokeitem.model;

import java.util.UUID;

public class PokemonInfo {
    public int dex = 0;
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

    public String getSpeciesKo() { return speciesKo != null ? speciesKo : species; }
    public int ivSum() { int s=0; for (int v:iv) s+=v; return s; }
    public int evSum() { int s=0; for (int v:ev) s+=v; return s; }
}
