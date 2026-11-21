package net.gasmp.core.casino;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class CasinoMenuHolder implements InventoryHolder {
    public enum Stage { MENU, CHOICES, SPIN }
    private final Stage stage;
    private final String game;
    private final double bet;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public CasinoMenuHolder(Stage stage, String game, double bet) {
        this.stage = stage;
        this.game = game;
        this.bet = bet;
    }

    public Stage stage() { return stage; }
    public String game() { return game; }
    public double bet() { return bet; }
    public void action(int slot, String action) { actions.put(slot, action); }
    public String action(int slot) { return actions.get(slot); }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
