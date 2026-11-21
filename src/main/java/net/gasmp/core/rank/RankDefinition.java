package net.gasmp.core.rank;

import org.bukkit.Material;

public record RankDefinition(
        String key, String group, String displayName, String track,
        int weight, String prefix, String imageKey, Material iconMaterial
) { }
