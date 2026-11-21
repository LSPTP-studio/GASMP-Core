package net.gasmp.core.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CrateAnimationHolder implements InventoryHolder {
    private final String crateId;
    private Inventory inventory;

    public CrateAnimationHolder(String crateId) { this.crateId = crateId; }
    public String crateId() { return crateId; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
