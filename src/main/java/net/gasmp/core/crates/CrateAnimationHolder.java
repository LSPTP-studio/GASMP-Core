package net.gasmp.core.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CrateAnimationHolder implements InventoryHolder {
    private final String crateId;
    private final int page;
    private Inventory inventory;

    public CrateAnimationHolder(String crateId) {
        this(crateId, 0);
    }

    public CrateAnimationHolder(String crateId, int page) {
        this.crateId = crateId;
        this.page = Math.max(0, page);
    }

    public String crateId() { return crateId; }
    public int page() { return page; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
