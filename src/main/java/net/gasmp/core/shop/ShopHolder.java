package net.gasmp.core.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class ShopHolder implements InventoryHolder {
    public enum Stage { LIST, CONFIRM }

    private final Stage stage;
    private final int page;
    private final String itemKey;
    private final String category;
    private final Map<Integer, String> entries = new HashMap<>();
    private final Map<Integer, String> categories = new HashMap<>();
    private Inventory inventory;

    public ShopHolder(Stage stage, int page, String itemKey, String category) {
        this.stage = stage;
        this.page = page;
        this.itemKey = itemKey;
        this.category = category == null ? "all" : category;
    }

    public Stage stage() { return stage; }
    public int page() { return page; }
    public String itemKey() { return itemKey; }
    public String category() { return category; }
    public void entryAt(int slot, String key) { entries.put(slot, key); }
    public String entryAt(int slot) { return entries.get(slot); }
    public void categoryAt(int slot, String key) { categories.put(slot, key); }
    public String categoryAt(int slot) { return categories.get(slot); }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
