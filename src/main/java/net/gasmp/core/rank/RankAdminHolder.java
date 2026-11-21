package net.gasmp.core.rank;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RankAdminHolder implements InventoryHolder {
    public enum Stage { TARGETS, RANKS, CONFIRM }

    private final Stage stage;
    private final UUID targetUuid;
    private final String targetName;
    private final String rankKey;
    private final int page;
    private final Map<Integer, UUID> targets = new HashMap<>();
    private final Map<Integer, String> ranks = new HashMap<>();
    private Inventory inventory;

    public RankAdminHolder(Stage stage, UUID targetUuid, String targetName, String rankKey, int page) {
        this.stage = stage;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.rankKey = rankKey;
        this.page = page;
    }

    public Stage stage() { return stage; }
    public UUID targetUuid() { return targetUuid; }
    public String targetName() { return targetName; }
    public String rankKey() { return rankKey; }
    public int page() { return page; }
    public void targetAt(int slot, UUID uuid) { targets.put(slot, uuid); }
    public UUID targetAt(int slot) { return targets.get(slot); }
    public void rankAt(int slot, String key) { ranks.put(slot, key); }
    public String rankAt(int slot) { return ranks.get(slot); }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
