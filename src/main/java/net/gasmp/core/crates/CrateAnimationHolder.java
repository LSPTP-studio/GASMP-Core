package net.gasmp.core.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CrateAnimationHolder implements InventoryHolder {
    private final String crateId;
    private final int page;
    private final boolean preview;
    private Inventory inventory;

    /** A non-preview holder is used for the opening cinematic. */
    public CrateAnimationHolder(String crateId) {
        this.crateId = crateId;
        this.page = 0;
        this.preview = false;
    }

    /** A paged holder is used for the crate reward preview. */
    public CrateAnimationHolder(String crateId, int page) {
        this.crateId = crateId;
        this.page = Math.max(0, page);
        this.preview = true;
    }

    public String crateId() { return crateId; }
    public int page() { return page; }
    public boolean isPreview() { return preview; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
