package net.gasmp.core.casino;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Holder identity for the GASMP casino challenge, duel and reveal screens. */
public final class CasinoDuelHolder implements InventoryHolder {
    public enum Stage { INVITE, MATCH, ANIMATION }

    private final Stage stage;
    private final UUID referenceId;
    private final UUID viewerId;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    public CasinoDuelHolder(Stage stage, UUID referenceId, UUID viewerId) {
        this.stage = stage;
        this.referenceId = referenceId;
        this.viewerId = viewerId;
    }

    public Stage stage() { return stage; }
    public UUID referenceId() { return referenceId; }
    public UUID viewerId() { return viewerId; }
    public void action(int slot, String action) { actions.put(slot, action); }
    public String action(int slot) { return actions.get(slot); }
    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override public Inventory getInventory() { return inventory; }
}
