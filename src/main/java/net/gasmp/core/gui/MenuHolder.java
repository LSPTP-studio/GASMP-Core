package net.gasmp.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class MenuHolder implements InventoryHolder {

    private final MenuPage page;
    private final Map<Integer, MenuPage> navigation = new HashMap<>();
    private final Map<Integer, String> notices = new HashMap<>();
    private final Map<Integer, String> commands = new HashMap<>();
    private Inventory inventory;

    public MenuHolder(MenuPage page) {
        this.page = page;
    }

    public MenuPage getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void navigate(int slot, MenuPage target) {
        navigation.put(slot, target);
    }

    public void notice(int slot, String message) {
        notices.put(slot, message);
    }

    public void command(int slot, String command) {
        commands.put(slot, command);
    }

    public String commandAt(int slot) {
        return commands.get(slot);
    }

    public MenuPage navigationAt(int slot) {
        return navigation.get(slot);
    }

    public String noticeAt(int slot) {
        return notices.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
