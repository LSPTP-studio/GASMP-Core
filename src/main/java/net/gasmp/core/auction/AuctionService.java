package net.gasmp.core.auction;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.gui.ScreenUi;
import net.gasmp.core.data.PlayerDataStore;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionService implements CommandExecutor, TabCompleter, Listener {
    private static final int AUCTION_PAGE_SIZE = 21;
    private static final int[] AUCTION_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final class AuctionMenuHolder implements InventoryHolder {
        enum Stage { LIST, CONFIRM }
        final Stage stage;
        final int page;
        final long auctionId;
        final Map<Integer, Long> listings = new HashMap<>();
        Inventory inventory;
        AuctionMenuHolder(Stage stage, int page, long auctionId) {
            this.stage = stage;
            this.page = page;
            this.auctionId = auctionId;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final long LISTING_DURATION_MS = 7L * 24 * 60 * 60 * 1000;
    private final GASMPCore plugin;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> collectingMail = ConcurrentHashMap.newKeySet();
    private BukkitTask expiryTask;

    public AuctionService(GASMPCore plugin) { this.plugin = plugin; }

    /** Starts periodic expiry processing. Expired items are mailed, never dropped on the floor. */
    public void startExpiryTask() {
        // Reconcile abandoned reservations once on startup. Repeating this during a live
        // payment operation could incorrectly quarantine a slow but active Vault transaction.
        expireListings(true);
        if (expiryTask != null) expiryTask.cancel();
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> expireListings(false),
                20L * 60L, 20L * 60L * 5L);
    }

    public void shutdown() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    private void expireListings(boolean recoverStaleReservations) {
        long timeoutSeconds = Math.max(30L, plugin.getConfig().getLong("auction-house.reservation-timeout-seconds", 120L));
        java.util.concurrent.CompletableFuture<PlayerDataStore.AuctionRecovery> recoveryFuture =
                recoverStaleReservations
                        ? plugin.getDataStore().recoverStaleAuctionReservations(timeoutSeconds * 1000L)
                        : java.util.concurrent.CompletableFuture.completedFuture(new PlayerDataStore.AuctionRecovery(0));
        recoveryFuture.thenCompose(recovery -> plugin.getDataStore().expireAuctions()
                        .thenApply(expired -> Map.entry(recovery, expired)))
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().warning("Auction expiry/recovery sweep failed: " + error.getMessage());
                        return;
                    }
                    if (result.getKey().heldForReview() > 0) {
                        plugin.getLogger().severe("Held " + result.getKey().heldForReview()
                                + " abandoned auction reservation(s) for OWNER REVIEW. "
                                + "No listing was automatically reopened because a Coins transfer may have occurred. "
                                + "Use /ah review.");
                    }
                    List<PlayerDataStore.AuctionRecord> expired = result.getValue();
                    if (expired == null || expired.isEmpty()) return;
                    for (PlayerDataStore.AuctionRecord listing : expired) {
                        Player seller = Bukkit.getPlayer(listing.sellerUuid());
                        if (seller != null) {
                            seller.sendMessage(color("&eYour auction #" + listing.id()
                                    + " expired. Its item is waiting in &d/ah collect&e."));
                            deliverPendingItems(seller, false);
                        }
                    }
                    plugin.getLogger().info("Expired " + expired.size() + " auction listing(s); items moved to player mailboxes.");
                }));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Auction commands must be run in-game.");
            return true;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("review")
                || args[0].equalsIgnoreCase("review-release")
                || args[0].equalsIgnoreCase("review-settle"))) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "review" -> reviewListings(player);
                case "review-release" -> resolveReview(player, args, false);
                case "review-settle" -> resolveReview(player, args, true);
            }
            return true;
        }
        if (!plugin.getConfig().getBoolean("modules.auction-house", true)) {
            player.sendMessage(color("&cThe auction house is disabled."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            int page = 0;
            if (args.length > 1) {
                try { page = Math.max(0, Math.min(1000, Integer.parseInt(args[1]) - 1)); }
                catch (NumberFormatException ex) { player.sendMessage(color("&cPage must be a number.")); return true; }
            }
            list(player, page); return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sell(player, args);
            case "buy" -> buy(player, args);
            case "cancel" -> cancel(player, args);
            case "mine" -> mine(player);
            case "collect" -> deliverPendingItems(player, true);
            case "help" -> help(player);
            default -> help(player);
        }
        return true;
    }

    private void sell(Player player, String[] args) {
        if (args.length != 2) { player.sendMessage(color("&e/ah sell <price>")); return; }
        double price = parsePrice(args[1]);
        double minimum = plugin.getConfig().getDouble("auction-house.minimum-price", 1.0);
        double maximum = plugin.getConfig().getDouble("auction-house.maximum-price", 100000000.0);
        if (!Double.isFinite(price) || price < minimum || price > maximum) {
            player.sendMessage(color("&cPrice must be between " + minimum + " and " + maximum + " Coins.")); return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) { player.sendMessage(color("&cHold the item you want to list.")); return; }
        if (busy.contains(player.getUniqueId())) { player.sendMessage(color("&cYour previous auction action is processing.")); return; }
        ItemStack offered = held.clone();
        String encoded;
        try { encoded = encode(offered); }
        catch (Exception ex) { player.sendMessage(color("&cThat item could not be serialized.")); return; }
        if (!busy.add(player.getUniqueId())) return;
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        long hours = Math.max(1L, Math.min(24L * 30L, plugin.getConfig().getLong("auction-house.listing-hours", 168L)));
        long expires = System.currentTimeMillis() + hours * 60L * 60L * 1000L;
        plugin.getDataStore().getPlayerAuctions(player.getUniqueId(), 100).thenCompose(existing -> {
            int listingLimit = Math.max(1, plugin.getConfig().getInt("auction-house.max-active-listings-per-player", 10));
            if (existing.size() >= listingLimit) return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("You reached the active listing limit (" + listingLimit + ")."));
            return plugin.getDataStore().createAuction(player.getUniqueId(), player.getName(), encoded,
                offered.getType().name(), price, expires);
        }).whenComplete((id, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    busy.remove(player.getUniqueId());
                    if (error != null) {
                        plugin.getDataStore().addItemMail(player.getUniqueId(), encoded,
                                "Failed auction listing recovery")
                                .whenComplete((mailId, mailError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (mailError != null) {
                                        plugin.getLogger().severe("Auction item recovery mailbox failed for "
                                                + player.getUniqueId() + ": " + mailError.getMessage());
                                        if (player.isOnline()) returnItem(player, offered);
                                        player.sendMessage(color("&cListing failed. Recovery needs staff attention."));
                                    } else {
                                        player.sendMessage(color("&eListing failed. Your item was saved to &d/ah collect&e."));
                                        if (player.isOnline()) deliverPendingItems(player, false);
                                    }
                                }));
                        plugin.getLogger().warning("Auction create failed: " + error.getMessage());
                        return;
                    }
                    player.sendMessage(color("&aListed &f" + offered.getAmount() + "x " + offered.getType()
                            + " &afor &6" + price + " &aCoins. ID: &e#" + id));
                }));
    }

    private void list(Player player, int page) {
        int safePage = Math.max(0, page);
        plugin.getDataStore().getActiveAuctions(AUCTION_PAGE_SIZE, safePage * AUCTION_PAGE_SIZE)
                .whenComplete((records, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        player.sendMessage(color("&cCould not load auctions."));
                        plugin.getLogger().warning("Auction GUI load failed: " + error.getMessage());
                        return;
                    }
                    openAuctionList(player, safePage, records == null ? List.of() : records);
                }));
    }

    private void openAuctionList(Player player, int page, List<PlayerDataStore.AuctionRecord> records) {
        AuctionMenuHolder holder = new AuctionMenuHolder(AuctionMenuHolder.Stage.LIST, page, -1L);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ScreenUi.title("auction", "&8GASMP • Auction House"));
        holder.inventory = inventory;
        fillAuctionFrame(inventory);

        setAuctionItem(inventory, 4, Material.EMERALD, "&a&lGASMP AUCTION HOUSE",
                "&7Browse player listings and purchase with Coins.",
                "&7Click a listing to review it before buying.",
                "&8Items are delivered to your /ah collect mailbox.");
        setAuctionItem(inventory, 47, Material.GOLD_INGOT, "&6&lSELL AN ITEM",
                "&7Hold an item in your main hand, then use:",
                "&e/ah sell <price>");

        int index = 0;
        for (PlayerDataStore.AuctionRecord record : records) {
            if (index >= AUCTION_SLOTS.length) break;
            int slot = AUCTION_SLOTS[index++];
            ItemStack display;
            try {
                display = decode(record.itemData());
                if (display == null || display.getType().isAir()) display = new ItemStack(Material.PAPER);
            } catch (Exception decodeError) {
                display = new ItemStack(Material.BARRIER);
            }
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                String itemName = record.itemName() == null || record.itemName().isBlank()
                        ? display.getType().name().replace('_', ' ') : record.itemName();
                meta.setDisplayName(color("&f&l" + itemName));
                List<String> lore = new ArrayList<>();
                lore.add(color("&7Seller: &f" + record.sellerName()));
                lore.add(color("&7Price: &6" + money(record.price()) + " Coins"));
                lore.add(color("&7Listing: &e#" + record.id()));
                lore.add(color("&8"));
                lore.add(color(record.sellerUuid().equals(player.getUniqueId())
                        ? "&cYour own listing" : "&aClick to review purchase"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(slot, display);
            holder.listings.put(slot, record.id());
        }

        if (records.isEmpty()) {
            setAuctionItem(inventory, 22, Material.BARREL, "&7&lNO ACTIVE LISTINGS",
                    "&7There are currently no auctions on this page.");
        }
        if (page > 0) setAuctionItem(inventory, 45, Material.ARROW, "&f&lPREVIOUS PAGE");
        setAuctionItem(inventory, 49, Material.BARRIER, "&c&lCLOSE");
        if (records.size() >= AUCTION_PAGE_SIZE) setAuctionItem(inventory, 53, Material.ARROW, "&f&lNEXT PAGE");
        player.openInventory(inventory);
    }

    private void openAuctionConfirm(Player player, int page, long auctionId) {
        plugin.getDataStore().getAuction(auctionId).whenComplete((record, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null || record == null || !"ACTIVE".equalsIgnoreCase(record.status())
                            || record.expiresAtMs() <= System.currentTimeMillis()) {
                        player.sendMessage(color("&cThat auction is no longer available."));
                        list(player, page);
                        return;
                    }
                    ItemStack display;
                    try {
                        display = decode(record.itemData());
                        if (display == null || display.getType().isAir()) display = new ItemStack(Material.PAPER);
                    } catch (Exception decodeError) {
                        player.sendMessage(color("&cThis auction contains invalid item data."));
                        return;
                    }
                    AuctionMenuHolder holder = new AuctionMenuHolder(AuctionMenuHolder.Stage.CONFIRM, page, auctionId);
                    Inventory inventory = Bukkit.createInventory(holder, 27,
                            ScreenUi.title("auction_confirm", "&8Review Auction"));
                    holder.inventory = inventory;
                    setAuctionFrame27(inventory);
                    ItemMeta meta = display.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(color("&f&l" + record.itemName()));
                        meta.setLore(List.of(
                                color("&7Seller: &f" + record.sellerName()),
                                color("&7Price: &6" + money(record.price()) + " Coins"),
                                color("&7Listing: &e#" + record.id())
                        ));
                        display.setItemMeta(meta);
                    }
                    inventory.setItem(13, display);
                    setAuctionItem(inventory, 11, Material.LIME_DYE, "&a&lCONFIRM PURCHASE",
                            "&7Pay &6" + money(record.price()) + " Coins",
                            "&7The item goes to your mailbox.");
                    setAuctionItem(inventory, 15, Material.RED_DYE, "&c&lCANCEL",
                            "&7Return to the auction listings.");
                    player.openInventory(inventory);
                }));
    }

    private void fillAuctionFrame(Inventory inventory) {
        ItemStack filler = namedAuctionPane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = namedAuctionPane(Material.LIME_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9, column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) inventory.setItem(slot, filler.clone());
        }
        for (int slot : new int[]{2, 3, 5, 6, 47, 48, 50, 51}) inventory.setItem(slot, accent.clone());
    }

    private void setAuctionFrame27(Inventory inventory) {
        ItemStack filler = namedAuctionPane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack accent = namedAuctionPane(Material.LIME_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 27; slot++) {
            int row = slot / 9, column = slot % 9;
            if (row == 0 || row == 2 || column == 0 || column == 8) inventory.setItem(slot, filler.clone());
        }
        inventory.setItem(2, accent.clone());
        inventory.setItem(6, accent.clone());
    }

    private ItemStack namedAuctionPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private void setAuctionItem(Inventory inventory, int slot, Material material,
                                String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList());
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    @EventHandler
    public void onAuctionMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (holder.stage == AuctionMenuHolder.Stage.LIST) {
            if (slot == 49) { player.closeInventory(); return; }
            if (slot == 45 && holder.page > 0) { list(player, holder.page - 1); return; }
            if (slot == 53 && holder.inventory.getItem(53) != null) { list(player, holder.page + 1); return; }
            Long id = holder.listings.get(slot);
            if (id != null) {
                openAuctionConfirm(player, holder.page, id);
                return;
            }
            return;
        }

        if (slot == 15) { list(player, holder.page); return; }
        if (slot == 11) {
            player.closeInventory();
            buy(player, new String[]{"buy", Long.toString(holder.auctionId)});
        }
    }

    @EventHandler
    public void onAuctionMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AuctionMenuHolder) event.setCancelled(true);
    }

    private void mine(Player player) {
        plugin.getDataStore().getPlayerAuctions(player.getUniqueId(), 30).whenComplete((records, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) { player.sendMessage(color("&cCould not load your listings.")); return; }
                    player.sendMessage(color("&d&lYOUR ACTIVE LISTINGS"));
                    if (records.isEmpty()) player.sendMessage(color("&7You have no active listings."));
                    for (var record : records) player.sendMessage(color("&e#" + record.id() + " &f" + record.itemName()
                            + " &8- &6" + money(record.price()) + " Coins &7(/ah cancel " + record.id() + ")"));
                }));
    }

    private void buy(Player buyer, String[] args) {
        if (args.length != 2) { buyer.sendMessage(color("&e/ah buy <id>")); return; }
        long id;
        try { id = Long.parseLong(args[1]); if (id <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { buyer.sendMessage(color("&cInvalid auction ID.")); return; }
        if (!plugin.getEconomyBridge().isAvailable()) { buyer.sendMessage(color("&cCoins economy unavailable (Vault provider missing).")); return; }
        if (!busy.add(buyer.getUniqueId())) { buyer.sendMessage(color("&cYour previous auction action is processing.")); return; }
        plugin.getDataStore().getAuction(id).whenComplete((record, loadError) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (loadError != null || record == null || !record.status().equals("ACTIVE") || record.expiresAtMs() <= System.currentTimeMillis()) {
                busy.remove(buyer.getUniqueId()); buyer.sendMessage(color("&cThat listing is no longer available.")); return;
            }
            if (record.sellerUuid().equals(buyer.getUniqueId())) {
                busy.remove(buyer.getUniqueId()); buyer.sendMessage(color("&cYou cannot buy your own listing.")); return;
            }
            ItemStack offered;
            try { offered = decode(record.itemData()); }
            catch (Exception ex) {
                busy.remove(buyer.getUniqueId()); buyer.sendMessage(color("&cThis listing has corrupted item data. Staff have been notified."));
                plugin.getLogger().severe("Invalid item data in auction #" + id + ": " + ex.getMessage()); return;
            }
            if (offered == null || offered.getType().isAir()) {
                busy.remove(buyer.getUniqueId()); buyer.sendMessage(color("&cThis listing contains an invalid item.")); return;
            }
            plugin.getDataStore().reserveAuction(id, buyer.getUniqueId()).whenComplete((reserved, reserveError) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (reserveError != null || !Boolean.TRUE.equals(reserved)) {
                            busy.remove(buyer.getUniqueId());
                            if (buyer.isOnline()) buyer.sendMessage(color("&cSomeone else bought this listing first."));
                            return;
                        }
                        if (!buyer.isOnline()) {
                            release(record.id(), buyer.getUniqueId());
                            busy.remove(buyer.getUniqueId());
                            return;
                        }
                        executePayment(buyer, record, offered);
                    }));
        }));
    }

    private enum TransferState { APPLIED, NOT_APPLIED, UNKNOWN }

    private void executePayment(Player buyer, PlayerDataStore.AuctionRecord listing, ItemStack item) {
        OfflinePlayer buyerAccount = Bukkit.getOfflinePlayer(buyer.getUniqueId());
        OfflinePlayer sellerAccount = Bukkit.getOfflinePlayer(listing.sellerUuid());
        final double buyerBalanceBefore;
        final double sellerBalanceBefore;

        try {
            if (!plugin.getEconomyBridge().has(buyerAccount, listing.price())) {
                release(listing.id(), buyer.getUniqueId());
                busy.remove(buyer.getUniqueId());
                buyer.sendMessage(color("&cYou cannot afford this listing."));
                return;
            }
            buyerBalanceBefore = plugin.getEconomyBridge().getBalance(buyerAccount);
            // Check the seller balance before charging the buyer so later credit reconciliation
            // has a trustworthy baseline, including when the seller is offline.
            sellerBalanceBefore = plugin.getEconomyBridge().getBalance(sellerAccount);
            if (!Double.isFinite(buyerBalanceBefore) || !Double.isFinite(sellerBalanceBefore)) {
                throw new IllegalStateException("Economy provider returned a non-finite balance.");
            }
        } catch (RuntimeException preflightError) {
            release(listing.id(), buyer.getUniqueId());
            busy.remove(buyer.getUniqueId());
            buyer.sendMessage(color("&cThe economy provider could not verify this transaction. No purchase was completed."));
            plugin.getLogger().warning("Auction preflight failed for #" + listing.id() + ": " + preflightError.getMessage());
            return;
        }

        TransferState buyerDebit = transferVerified(buyerAccount, listing.price(), buyerBalanceBefore, false);
        if (buyerDebit == TransferState.NOT_APPLIED) {
            release(listing.id(), buyer.getUniqueId());
            busy.remove(buyer.getUniqueId());
            buyer.sendMessage(color("&cYour payment was not applied. The listing is available again."));
            return;
        }
        if (buyerDebit == TransferState.UNKNOWN) {
            busy.remove(buyer.getUniqueId());
            buyer.sendMessage(color("&cYour payment state is unclear. The listing has been held for staff review; do not retry."));
            plugin.getLogger().severe("Auction #" + listing.id() + " buyer debit is ambiguous for " + buyer.getUniqueId()
                    + ". Reservation retained to prevent a duplicate sale.");
            return;
        }

        final double buyerBalanceAfterDebit;
        try {
            buyerBalanceAfterDebit = plugin.getEconomyBridge().getBalance(buyerAccount);
            if (!Double.isFinite(buyerBalanceAfterDebit)
                    || Math.abs((buyerBalanceBefore - buyerBalanceAfterDebit) - listing.price()) > 0.005d) {
                busy.remove(buyer.getUniqueId());
                buyer.sendMessage(color("&cYour payment needs staff reconciliation. The listing has been held; do not retry."));
                plugin.getLogger().severe("Auction #" + listing.id() + " buyer post-debit balance does not match expected charge.");
                return;
            }
        } catch (RuntimeException balanceError) {
            busy.remove(buyer.getUniqueId());
            buyer.sendMessage(color("&cYour payment may have been taken. The listing has been held for staff review; do not retry."));
            plugin.getLogger().severe("Could not verify buyer balance after debit for auction #" + listing.id()
                    + ": " + balanceError.getMessage());
            return;
        }

        TransferState sellerCredit = transferVerified(sellerAccount, listing.price(), sellerBalanceBefore, true);
        if (sellerCredit == TransferState.NOT_APPLIED) {
            TransferState refund = transferVerified(buyerAccount, listing.price(), buyerBalanceAfterDebit, true);
            busy.remove(buyer.getUniqueId());
            if (refund == TransferState.APPLIED) {
                release(listing.id(), buyer.getUniqueId());
                if (buyer.isOnline()) buyer.sendMessage(color("&cSeller payment failed; your Coins were refunded and the listing is available again."));
            } else {
                if (buyer.isOnline()) buyer.sendMessage(color("&4Seller payment failed and your refund needs staff review. Auction #" + listing.id()));
                plugin.getLogger().severe("Auction #" + listing.id() + " seller credit was not applied but buyer refund is " + refund
                        + "; reservation retained.");
            }
            return;
        }
        if (sellerCredit == TransferState.UNKNOWN) {
            busy.remove(buyer.getUniqueId());
            if (buyer.isOnline()) buyer.sendMessage(color("&cSeller payment state is unclear. The listing is held for staff review; do not retry."));
            plugin.getLogger().severe("Auction #" + listing.id() + " seller credit is ambiguous. Reservation retained to prevent duplicate sale.");
            return;
        }

        plugin.getDataStore().completeAuction(listing.id(), buyer.getUniqueId()).whenComplete((completed, dbError) -> {
            if (dbError == null && Boolean.TRUE.equals(completed)) {
                Bukkit.getScheduler().runTask(plugin, () -> finishSuccessfulPurchase(buyer, listing));
                return;
            }

            // A commit can succeed even if the JDBC caller observes an exception immediately
            // afterward. Inspect the durable row before reversing any Coins or re-opening it.
            plugin.getDataStore().getAuction(listing.id()).whenComplete((state, inspectError) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (inspectError != null || state == null) {
                            keepForOwnerReview(buyer, listing.id(), "database finalization state could not be read", inspectError);
                            return;
                        }
                        if ("SOLD".equalsIgnoreCase(state.status())
                                && buyer.getUniqueId().equals(state.buyerUuid())) {
                            finishSuccessfulPurchase(buyer, listing);
                            return;
                        }
                        if (!"RESERVED".equalsIgnoreCase(state.status())
                                || !buyer.getUniqueId().equals(state.buyerUuid())) {
                            keepForOwnerReview(buyer, listing.id(), "unexpected auction state " + state.status(), dbError);
                            return;
                        }
                        compensateFailedFinalization(buyer, listing, sellerAccount, buyerAccount,
                                sellerBalanceBefore, buyerBalanceAfterDebit);
                    }));
        });
    }

    private void finishSuccessfulPurchase(Player buyer, PlayerDataStore.AuctionRecord listing) {
        Player seller = Bukkit.getPlayer(listing.sellerUuid());
        if (seller != null) seller.sendMessage(color("&aYour auction #" + listing.id()
                + " was sold for &6" + money(listing.price()) + " Coins."));
        if (buyer.isOnline()) {
            buyer.sendMessage(color("&aPurchased auction #" + listing.id()
                    + " for &6" + money(listing.price()) + " Coins."));
            deliverPendingItems(buyer, false);
        }
        busy.remove(buyer.getUniqueId());
    }

    private void compensateFailedFinalization(Player buyer, PlayerDataStore.AuctionRecord listing,
            OfflinePlayer sellerAccount, OfflinePlayer buyerAccount,
            double sellerBalanceBefore, double buyerBalanceAfterDebit) {
        // Reverse seller credit first. Never issue the buyer refund if the seller clawback
        // cannot be confirmed; doing so could create Coins from an ambiguous provider state.
        double sellerAfterCredit = sellerBalanceBefore + listing.price();
        TransferState clawback = transferVerified(sellerAccount, listing.price(), sellerAfterCredit, false);
        if (clawback != TransferState.APPLIED) {
            markForOwnerReview(buyer, listing.id(), "seller clawback=" + clawback);
            return;
        }
        TransferState refund = transferVerified(buyerAccount, listing.price(), buyerBalanceAfterDebit, true);
        if (refund == TransferState.APPLIED) {
            plugin.getDataStore().releaseAuction(listing.id(), buyer.getUniqueId()).whenComplete((released, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        busy.remove(buyer.getUniqueId());
                        if (error == null && Boolean.TRUE.equals(released)) {
                            if (buyer.isOnline()) buyer.sendMessage(color("&cPurchase finalization failed. Your Coins were refunded and the listing reopened."));
                        } else {
                            keepForOwnerReview(buyer, listing.id(), "refund applied but reservation could not be reopened", error);
                        }
                    }));
        } else {
            markForOwnerReview(buyer, listing.id(), "buyer refund=" + refund);
        }
    }

    private void markForOwnerReview(Player buyer, long auctionId, String reason) {
        plugin.getDataStore().markAuctionForReview(auctionId).whenComplete((changed, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    busy.remove(buyer.getUniqueId());
                    if (buyer.isOnline()) buyer.sendMessage(color("&4Auction #" + auctionId
                            + " needs Owner review. Do not retry. Reference: " + reason));
                    plugin.getLogger().severe("Auction #" + auctionId + " requires Owner review: " + reason
                            + (error == null ? " (review flag=" + changed + ")" : " (could not mark review: " + error.getMessage() + ")"));
                }));
    }

    private void keepForOwnerReview(Player buyer, long auctionId, String reason, Throwable error) {
        markForOwnerReview(buyer, auctionId, reason + (error == null ? "" : ": " + error.getMessage()));
    }

    private void reviewListings(Player actor) {
        requireOwner(actor, () -> plugin.getDataStore().getAuctionsUnderReview(50)
                .whenComplete((records, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        actor.sendMessage(color("&cCould not read auction review queue."));
                        return;
                    }
                    actor.sendMessage(color("&4&lGASMP AUCTION REVIEW &8- &7" + records.size() + " listing(s)"));
                    if (records.isEmpty()) {
                        actor.sendMessage(color("&aNo auctions require review."));
                        return;
                    }
                    for (PlayerDataStore.AuctionRecord record : records) {
                        actor.sendMessage(color("&c#" + record.id() + " &f" + record.itemName()
                                + " &7price=&6" + money(record.price())
                                + " &7seller=&f" + record.sellerName()
                                + " &7buyer=&f" + (record.buyerUuid() == null ? "unknown" : record.buyerUuid())
                                + " &8If only one transfer moved, reconcile Coins manually first. Then use /ah review-release "
                                + record.id() + " confirm-unpaid only when both sides are restored, or /ah review-settle "
                                + record.id() + " confirm-paid only after buyer debit + seller credit are confirmed."));
                    }
                })));
    }

    private void resolveReview(Player actor, String[] args, boolean settle) {
        String requiredConfirmation = settle ? "confirm-paid" : "confirm-unpaid";
        if (args.length != 3 || !args[2].equalsIgnoreCase(requiredConfirmation)) {
            actor.sendMessage(color("&e/ah " + (settle ? "review-settle" : "review-release")
                    + " <id> " + requiredConfirmation));
            return;
        }
        long id;
        try {
            id = Long.parseLong(args[1]);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            actor.sendMessage(color("&cInvalid auction ID."));
            return;
        }
        requireOwner(actor, () -> {
            var action = settle
                    ? plugin.getDataStore().settleReviewedAuction(id)
                    : plugin.getDataStore().releaseReviewedAuction(id);
            action.whenComplete((record, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || record == null) {
                    actor.sendMessage(color("&cAuction not in the review queue or resolution failed."));
                    return;
                }
                if (settle) {
                    actor.sendMessage(color("&aAuction #" + id + " settled. Item placed in buyer mailbox."));
                    if (record.buyerUuid() != null) {
                        Player buyer = Bukkit.getPlayer(record.buyerUuid());
                        if (buyer != null) deliverPendingItems(buyer, false);
                    }
                    Player seller = Bukkit.getPlayer(record.sellerUuid());
                    if (seller != null) seller.sendMessage(color("&aYour auction #" + id + " was settled by an Owner."));
                } else {
                    actor.sendMessage(color("&aAuction #" + id + " released. If it expired, the item was returned to seller mail."));
                    Player seller = Bukkit.getPlayer(record.sellerUuid());
                    if (seller != null) deliverPendingItems(seller, false);
                }
                plugin.getLogger().warning("Auction #" + id + " manually resolved by Owner " + actor.getName()
                        + " using " + (settle ? "SETTLE" : "RELEASE") + ". Verify Vault audit records.");
            }));
        });
    }

    private void requireOwner(Player actor, Runnable action) {
        plugin.getRankService().getAccess(actor.getUniqueId()).whenComplete((access, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || access == null || !access.owner()) {
                        actor.sendMessage(color("&cOnly the configured Owner group can resolve auction reviews."));
                        return;
                    }
                    action.run();
                }));
    }

    /**
     * Performs one Vault transfer and reconciles a null/failed/thrown response against
     * the known pre-transaction balance. UNKNOWN is deliberately not auto-retried.
     */
    private TransferState transferVerified(OfflinePlayer account, double amount,
                                           double balanceBefore, boolean deposit) {
        try {
            EconomyResponse response = deposit
                    ? plugin.getEconomyBridge().deposit(account, amount)
                    : plugin.getEconomyBridge().withdraw(account, amount);
            if (response != null && response.transactionSuccess()) return TransferState.APPLIED;
        } catch (RuntimeException transferError) {
            plugin.getLogger().warning("Vault transfer threw for " + account.getUniqueId()
                    + ": " + transferError.getMessage());
        }

        try {
            double balanceAfter = plugin.getEconomyBridge().getBalance(account);
            if (!Double.isFinite(balanceAfter)) return TransferState.UNKNOWN;
            double delta = deposit ? balanceAfter - balanceBefore : balanceBefore - balanceAfter;
            if (Math.abs(delta - amount) <= 0.005d) return TransferState.APPLIED;
            if (Math.abs(delta) <= 0.005d) return TransferState.NOT_APPLIED;
            return TransferState.UNKNOWN;
        } catch (RuntimeException balanceError) {
            plugin.getLogger().severe("Vault transfer state is unknown for " + account.getUniqueId()
                    + ": " + balanceError.getMessage());
            return TransferState.UNKNOWN;
        }
    }

    private void cancel(Player player, String[] args) {
        if (args.length != 2) { player.sendMessage(color("&e/ah cancel <id>")); return; }
        long id;
        try { id = Long.parseLong(args[1]); if (id <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { player.sendMessage(color("&cInvalid auction ID.")); return; }
        plugin.getDataStore().cancelAuction(id, player.getUniqueId()).whenComplete((record, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || record == null) { player.sendMessage(color("&cListing not found or it is no longer cancellable.")); return; }
                    player.sendMessage(color("&aAuction #" + id + " cancelled. Its item was placed in your auction mailbox."));
                    deliverPendingItems(player, false);
                }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) deliverPendingItems(player, false);
        }, 40L);
    }

    private void deliverPendingItems(Player player, boolean manual) {
        UUID playerId = player.getUniqueId();
        if (!collectingMail.add(playerId)) {
            if (manual) player.sendMessage(color("&eYour auction mailbox is already being processed."));
            return;
        }
        plugin.getDataStore().getPendingItemMail(playerId, 40).whenComplete((mail, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (!player.isOnline()) return;
                        if (error != null) {
                            player.sendMessage(color("&cCould not load your auction mailbox. Try again later."));
                            plugin.getLogger().warning("Auction mailbox lookup failed for " + playerId + ": " + error.getMessage());
                            return;
                        }
                        if (mail == null || mail.isEmpty()) {
                            if (manual) player.sendMessage(color("&7Your auction mailbox is empty."));
                            return;
                        }
                        int delivered = 0;
                        for (PlayerDataStore.ItemMailRecord record : mail) {
                            ItemStack item;
                            try {
                                item = decode(record.itemData());
                                if (item == null || item.getType().isAir()) throw new IllegalArgumentException("Mailbox item is empty.");
                            } catch (Exception ex) {
                                plugin.getLogger().severe("Invalid auction mailbox item #" + record.id()
                                        + " for " + playerId + ": " + ex.getMessage());
                                continue; // Keep the record for staff recovery.
                            }

                            ItemStack[] before = cloneContents(player.getInventory().getStorageContents());
                            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                            if (!leftover.isEmpty()) {
                                player.getInventory().setStorageContents(before);
                                if (manual) player.sendMessage(color("&eYour inventory is full. Make space and run &f/ah collect&e."));
                                break;
                            }

                            // The database executor is single-threaded; this status update is queued before
                            // any later /ah collect lookup can run, and the per-player lock prevents duplicate
                            // concurrent inventory inserts within this server process.
                            plugin.getDataStore().markItemMailDelivered(record.id(), playerId)
                                    .whenComplete((marked, markError) -> {
                                        if (markError != null || !Boolean.TRUE.equals(marked)) {
                                            plugin.getLogger().severe("Item-mail delivery marker failed for record #"
                                                    + record.id() + ". Manual inventory/database reconciliation is required.");
                                        }
                                    });
                            delivered++;
                        }
                        if (delivered > 0) player.sendMessage(color("&aCollected " + delivered + " item(s) from your auction mailbox."));
                        else if (manual) player.sendMessage(color("&7No items could be collected. Check inventory space or contact staff."));
                    } finally {
                        collectingMail.remove(playerId);
                    }
                }));
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }

    private void release(long id, UUID buyer) {
        plugin.getDataStore().releaseAuction(id, buyer).exceptionally(error -> {
            plugin.getLogger().severe("Could not release auction reservation #" + id + ": " + error.getMessage()); return false;
        });
    }

    private void returnItem(Player player, ItemStack item) {
        if (player.isOnline()) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(item);
            left.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
    }

    private String encode(ItemStack item) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("item", item);
        return Base64.getEncoder().encodeToString(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    private ItemStack decode(String encoded) throws Exception {
        String text = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        YamlConfiguration yaml = new YamlConfiguration(); yaml.loadFromString(text); return yaml.getItemStack("item");
    }

    private double parsePrice(String value) {
        try { double price = Double.parseDouble(value); return Double.isFinite(price) ? price : -1; }
        catch (NumberFormatException ex) { return -1; }
    }

    private String money(double value) { return String.format(Locale.US, "%.2f", value); }
    private void help(Player player) {
        player.sendMessage(color("&6&lGASMP AUCTION HOUSE"));
        player.sendMessage(color("&e/ah list [page] &7- browse listings"));
        player.sendMessage(color("&e/ah sell <price> &7- list the item in your hand"));
        player.sendMessage(color("&e/ah buy <id> &7- purchase a listing"));
        player.sendMessage(color("&e/ah cancel <id> &7- cancel your listing"));
        player.sendMessage(color("&e/ah mine &7- view your active listings"));
        player.sendMessage(color("&e/ah collect &7- collect delivered/cancelled auction items"));
        player.sendMessage(color("&c/ah review &7- Owner-only: inspect ambiguous payments"));
        player.sendMessage(color("&c/ah review-release <id> confirm-unpaid &7- Owner-only: release after checking no payment"));
        player.sendMessage(color("&c/ah review-settle <id> confirm-paid &7- Owner-only: settle after checking both balances"));
        player.sendMessage(color("&7If only one Coins transfer moved, reconcile it manually before resolving the listing."));
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "sell", "buy", "cancel", "mine", "collect", "review", "review-release", "review-settle", "help").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
