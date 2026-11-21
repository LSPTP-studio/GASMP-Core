package net.gasmp.core.data;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlayerDataStore implements AutoCloseable {
    public enum ShardChange { GIVE, TAKE, SET }

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws Exception; }

    private final File databaseFile;
    private final ExecutorService executor;
    private Connection connection;

    public PlayerDataStore(File databaseFile) {
        this.databaseFile = databaseFile;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "GASMP-Core-SQLite");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<Void> initialize() {
        return submit(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_players (" +
                        "uuid TEXT PRIMARY KEY, last_name TEXT NOT NULL, shards INTEGER NOT NULL DEFAULT 0, " +
                        "kills INTEGER NOT NULL DEFAULT 0, deaths INTEGER NOT NULL DEFAULT 0, " +
                        "play_ticks INTEGER NOT NULL DEFAULT 0, first_seen_ms INTEGER NOT NULL, last_seen_ms INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_shard_transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, actor_uuid TEXT, " +
                        "change_type TEXT NOT NULL, amount_delta INTEGER NOT NULL, balance_after INTEGER NOT NULL, " +
                        "reason TEXT NOT NULL, created_at_ms INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_rank_audit (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, actor_uuid TEXT NOT NULL, target_uuid TEXT NOT NULL, " +
                        "rank_key TEXT NOT NULL, previous_primary_group TEXT NOT NULL, new_primary_group TEXT NOT NULL, " +
                        "created_at_ms INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_bounties (" +
                        "target_uuid TEXT PRIMARY KEY, target_name TEXT NOT NULL, amount REAL NOT NULL, updated_at_ms INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_combat_log (" +
                        "player_uuid TEXT PRIMARY KEY, created_at_ms INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_shop_transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, " +
                        "item_key TEXT NOT NULL, price REAL NOT NULL, currency_name TEXT NOT NULL, " +
                        "outcome TEXT NOT NULL, reason TEXT NOT NULL, created_at_ms INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_auctions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, seller_uuid TEXT NOT NULL, seller_name TEXT NOT NULL, " +
                        "item_data TEXT NOT NULL, item_name TEXT NOT NULL, price REAL NOT NULL, " +
                        "created_at_ms INTEGER NOT NULL, expires_at_ms INTEGER NOT NULL, " +
                        "status TEXT NOT NULL DEFAULT 'ACTIVE', buyer_uuid TEXT, reserved_at_ms INTEGER NOT NULL DEFAULT 0)");
                // In-place migration for databases created before reservation recovery was added.
                boolean hasReservationTimestamp = false;
                try (ResultSet columns = statement.executeQuery("PRAGMA table_info(gasmp_auctions)")) {
                    while (columns.next()) {
                        if ("reserved_at_ms".equalsIgnoreCase(columns.getString("name"))) {
                            hasReservationTimestamp = true;
                            break;
                        }
                    }
                }
                if (!hasReservationTimestamp) {
                    statement.execute("ALTER TABLE gasmp_auctions ADD COLUMN reserved_at_ms INTEGER NOT NULL DEFAULT 0");
                }
                statement.execute("CREATE INDEX IF NOT EXISTS idx_gasmp_auctions_status ON gasmp_auctions(status, created_at_ms)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_item_mail (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, item_data TEXT NOT NULL, " +
                        "reason TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', created_at_ms INTEGER NOT NULL)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_gasmp_item_mail_player ON gasmp_item_mail(player_uuid,status,id)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_battlepass_progress (" +
                        "player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, season_id TEXT NOT NULL, " +
                        "xp INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(player_uuid, season_id))");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_battlepass_claims (" +
                        "player_uuid TEXT NOT NULL, season_id TEXT NOT NULL, level INTEGER NOT NULL, " +
                        "claimed_at_ms INTEGER NOT NULL, PRIMARY KEY(player_uuid, season_id, level))");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_casino_transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, " +
                        "game_key TEXT NOT NULL, bet REAL NOT NULL, payout REAL NOT NULL, outcome TEXT NOT NULL, " +
                        "created_at_ms INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_moderation_actions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, target_uuid TEXT NOT NULL, target_name TEXT NOT NULL, " +
                        "actor_uuid TEXT, actor_name TEXT NOT NULL, action TEXT NOT NULL, reason TEXT NOT NULL, " +
                        "created_at_ms INTEGER NOT NULL, expires_at_ms INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_gasmp_moderation_target ON gasmp_moderation_actions(target_uuid, created_at_ms DESC)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_teams (" +
                        "team_id TEXT PRIMARY KEY, team_name TEXT NOT NULL UNIQUE, leader_uuid TEXT NOT NULL, " +
                        "created_at_ms INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '', " +
                        "bank_balance REAL NOT NULL DEFAULT 0, level INTEGER NOT NULL DEFAULT 1, " +
                        "xp INTEGER NOT NULL DEFAULT 0, max_members INTEGER NOT NULL DEFAULT 8)");
                ensureColumn(connection, "gasmp_teams", "description", "TEXT NOT NULL DEFAULT ''");
                ensureColumn(connection, "gasmp_teams", "bank_balance", "REAL NOT NULL DEFAULT 0");
                ensureColumn(connection, "gasmp_teams", "level", "INTEGER NOT NULL DEFAULT 1");
                ensureColumn(connection, "gasmp_teams", "xp", "INTEGER NOT NULL DEFAULT 0");
                ensureColumn(connection, "gasmp_teams", "max_members", "INTEGER NOT NULL DEFAULT 8");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_team_members (" +
                        "player_uuid TEXT PRIMARY KEY, team_id TEXT NOT NULL, joined_at_ms INTEGER NOT NULL, " +
                        "role TEXT NOT NULL DEFAULT 'MEMBER', " +
                        "FOREIGN KEY(team_id) REFERENCES gasmp_teams(team_id) ON DELETE CASCADE)");
                ensureColumn(connection, "gasmp_team_members", "role", "TEXT NOT NULL DEFAULT 'MEMBER'");
                statement.execute("UPDATE gasmp_team_members SET role='LEADER' WHERE player_uuid IN " +
                        "(SELECT leader_uuid FROM gasmp_teams)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_team_homes (" +
                        "team_id TEXT NOT NULL, home_key TEXT NOT NULL, world TEXT NOT NULL, " +
                        "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, " +
                        "PRIMARY KEY(team_id,home_key), FOREIGN KEY(team_id) REFERENCES gasmp_teams(team_id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS gasmp_team_allies (" +
                        "team_id TEXT NOT NULL, ally_team_id TEXT NOT NULL, created_at_ms INTEGER NOT NULL, " +
                        "PRIMARY KEY(team_id,ally_team_id), CHECK(team_id <> ally_team_id), " +
                        "FOREIGN KEY(team_id) REFERENCES gasmp_teams(team_id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(ally_team_id) REFERENCES gasmp_teams(team_id) ON DELETE CASCADE)");
            }
            return null;
        }, true);
    }

    private static void ensureColumn(Connection connection, String table, String column, String definition) throws SQLException {
        boolean found = false;
        try (Statement query = connection.createStatement(); ResultSet columns = query.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) if (column.equalsIgnoreCase(columns.getString("name"))) { found = true; break; }
        }
        if (!found) try (Statement alter = connection.createStatement()) {
            alter.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    public CompletableFuture<Void> ensurePlayer(UUID uuid, String name, long startingBalance) {
        return submit(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_players(uuid,last_name,shards,first_seen_ms,last_seen_ms) VALUES(?,?,?,?,?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name,last_seen_ms=excluded.last_seen_ms")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setLong(3, Math.max(0L, startingBalance));
                statement.setLong(4, now);
                statement.setLong(5, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Long> getShards(UUID uuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT shards FROM gasmp_players WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : 0L;
                }
            }
        });
    }

    public CompletableFuture<Long> changeShards(UUID playerUuid, String playerName, UUID actorUuid,
            String changeType, long amount, ShardChange operation, String reason) {
        if (amount < 0L) return CompletableFuture.failedFuture(
                new IllegalArgumentException("Amount must be a non-negative whole number."));

        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement ensure = connection.prepareStatement(
                        "INSERT INTO gasmp_players(uuid,last_name,shards,first_seen_ms,last_seen_ms) VALUES(?,?,0,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name,last_seen_ms=excluded.last_seen_ms")) {
                    ensure.setString(1, playerUuid.toString());
                    ensure.setString(2, playerName);
                    ensure.setLong(3, now);
                    ensure.setLong(4, now);
                    ensure.executeUpdate();
                }

                long current;
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT shards FROM gasmp_players WHERE uuid=?")) {
                    read.setString(1, playerUuid.toString());
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Player profile was not created.");
                        current = rs.getLong(1);
                    }
                }

                long next = switch (operation) {
                    case GIVE -> Math.addExact(current, amount);
                    case TAKE -> {
                        if (amount > current) throw new IllegalStateException("Player does not have enough Shards.");
                        yield current - amount;
                    }
                    case SET -> amount;
                };
                long delta = next - current;

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE gasmp_players SET shards=?,last_name=?,last_seen_ms=? WHERE uuid=?")) {
                    update.setLong(1, next);
                    update.setString(2, playerName);
                    update.setLong(3, now);
                    update.setString(4, playerUuid.toString());
                    update.executeUpdate();
                }

                try (PreparedStatement log = connection.prepareStatement(
                        "INSERT INTO gasmp_shard_transactions(player_uuid,actor_uuid,change_type,amount_delta," +
                        "balance_after,reason,created_at_ms) VALUES(?,?,?,?,?,?,?)")) {
                    log.setString(1, playerUuid.toString());
                    log.setString(2, actorUuid == null ? null : actorUuid.toString());
                    log.setString(3, changeType);
                    log.setLong(4, delta);
                    log.setLong(5, next);
                    log.setString(6, reason == null ? "No reason supplied" : reason);
                    log.setLong(7, now);
                    log.executeUpdate();
                }

                connection.commit();
                return next;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Void> saveStatistics(UUID uuid, String name, int kills, int deaths, long playTicks) {
        return submit(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_players(uuid,last_name,kills,deaths,play_ticks,first_seen_ms,last_seen_ms) " +
                    "VALUES(?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET last_name=excluded.last_name," +
                    "kills=excluded.kills,deaths=excluded.deaths,play_ticks=excluded.play_ticks,last_seen_ms=excluded.last_seen_ms")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setInt(3, kills);
                statement.setInt(4, deaths);
                statement.setLong(5, playTicks);
                statement.setLong(6, now);
                statement.setLong(7, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> recordRankChange(UUID actor, UUID target, String rankKey,
            String oldGroup, String newGroup) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_rank_audit(actor_uuid,target_uuid,rank_key,previous_primary_group," +
                    "new_primary_group,created_at_ms) VALUES(?,?,?,?,?,?)")) {
                statement.setString(1, actor.toString());
                statement.setString(2, target.toString());
                statement.setString(3, rankKey);
                statement.setString(4, oldGroup);
                statement.setString(5, newGroup);
                statement.setLong(6, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> recordShopTransaction(UUID playerUuid, String playerName,
            String itemKey, double price, String currencyName, String outcome, String reason) {
        if (!Double.isFinite(price) || price < 0.0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Shop audit price must be finite and non-negative."));
        }
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_shop_transactions(player_uuid,player_name,item_key,price,currency_name,"
                    + "outcome,reason,created_at_ms) VALUES(?,?,?,?,?,?,?,?)")) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, playerName);
                statement.setString(3, itemKey);
                statement.setDouble(4, price);
                statement.setString(5, currencyName == null ? "Coins" : currencyName);
                statement.setString(6, outcome);
                statement.setString(7, reason == null ? "No reason supplied" : reason);
                statement.setLong(8, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public record BountyRecord(UUID targetUuid, String targetName, double amount) { }

    public CompletableFuture<Void> addBounty(UUID targetUuid, String targetName, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) return CompletableFuture.failedFuture(
                new IllegalArgumentException("Bounty amount must be finite and greater than zero."));
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_bounties(target_uuid,target_name,amount,updated_at_ms) VALUES(?,?,?,?) " +
                    "ON CONFLICT(target_uuid) DO UPDATE SET target_name=excluded.target_name, " +
                    "amount=gasmp_bounties.amount+excluded.amount, updated_at_ms=excluded.updated_at_ms")) {
                statement.setString(1, targetUuid.toString());
                statement.setString(2, targetName);
                statement.setDouble(3, amount);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Double> claimBounty(UUID targetUuid) {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                double amount = 0.0;
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT amount FROM gasmp_bounties WHERE target_uuid=?")) {
                    read.setString(1, targetUuid.toString());
                    try (ResultSet result = read.executeQuery()) {
                        if (result.next()) amount = result.getDouble(1);
                    }
                }
                if (amount > 0.0) {
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM gasmp_bounties WHERE target_uuid=?")) {
                        delete.setString(1, targetUuid.toString());
                        delete.executeUpdate();
                    }
                }
                connection.commit();
                return amount;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<List<BountyRecord>> getTopBounties(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return submit(connection -> {
            List<BountyRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT target_uuid,target_name,amount FROM gasmp_bounties ORDER BY amount DESC LIMIT ?")) {
                statement.setInt(1, safeLimit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) records.add(new BountyRecord(
                            UUID.fromString(result.getString("target_uuid")),
                            result.getString("target_name"), result.getDouble("amount")));
                }
            }
            return records;
        });
    }

    public CompletableFuture<Void> markCombatLogout(UUID playerUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_combat_log(player_uuid,created_at_ms) VALUES(?,?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET created_at_ms=excluded.created_at_ms")) {
                statement.setString(1, playerUuid.toString());
                statement.setLong(2, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> consumeCombatLogout(UUID playerUuid) {
        // DELETE is atomic: two quick reconnects cannot both consume the same flag.
        return submit(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM gasmp_combat_log WHERE player_uuid=?")) {
                delete.setString(1, playerUuid.toString());
                return delete.executeUpdate() > 0;
            }
        });
    }

    public record AuctionRecord(long id, UUID sellerUuid, String sellerName, String itemData,
                                String itemName, double price, long createdAtMs, long expiresAtMs,
                                String status, UUID buyerUuid) { }

    public record ItemMailRecord(long id, UUID playerUuid, String itemData, String reason, long createdAtMs) { }

    public CompletableFuture<Long> addItemMail(UUID playerUuid, String itemData, String reason) {
        if (itemData == null || itemData.isBlank()) return CompletableFuture.failedFuture(
                new IllegalArgumentException("Mailbox item data cannot be empty."));
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_item_mail(player_uuid,item_data,reason,status,created_at_ms) VALUES(?,?,?,'PENDING',?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, itemData);
                statement.setString(3, reason == null ? "GASMP item recovery" : reason);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Item-mail ID was not generated.");
                    return keys.getLong(1);
                }
            }
        });
    }

    public CompletableFuture<List<ItemMailRecord>> getPendingItemMail(UUID playerUuid, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return submit(connection -> {
            List<ItemMailRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id,player_uuid,item_data,reason,created_at_ms FROM gasmp_item_mail " +
                    "WHERE player_uuid=? AND status='PENDING' ORDER BY id ASC LIMIT ?")) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, safeLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) records.add(new ItemMailRecord(rs.getLong("id"),
                            UUID.fromString(rs.getString("player_uuid")), rs.getString("item_data"),
                            rs.getString("reason"), rs.getLong("created_at_ms")));
                }
            }
            return records;
        });
    }

    public CompletableFuture<Boolean> markItemMailDelivered(long mailId, UUID playerUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE gasmp_item_mail SET status='DELIVERED' WHERE id=? AND player_uuid=? AND status='PENDING'")) {
                statement.setLong(1, mailId);
                statement.setString(2, playerUuid.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Long> createAuction(UUID sellerUuid, String sellerName, String itemData,
                                                   String itemName, double price, long expiresAtMs) {
        if (itemData == null || itemData.isBlank() || !Double.isFinite(price) || price <= 0.0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid auction listing."));
        }
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_auctions(seller_uuid,seller_name,item_data,item_name,price,created_at_ms,expires_at_ms,status) " +
                    "VALUES(?,?,?,?,?,?,?,'ACTIVE')", Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, sellerUuid.toString());
                statement.setString(2, sellerName);
                statement.setString(3, itemData);
                statement.setString(4, itemName);
                statement.setDouble(5, price);
                statement.setLong(6, System.currentTimeMillis());
                statement.setLong(7, expiresAtMs);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Auction ID was not generated.");
                    return keys.getLong(1);
                }
            }
        });
    }

    public CompletableFuture<List<AuctionRecord>> getActiveAuctions(int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        return submit(connection -> {
            List<AuctionRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM gasmp_auctions WHERE status='ACTIVE' AND expires_at_ms>? " +
                    "ORDER BY created_at_ms DESC LIMIT ? OFFSET ?")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setInt(2, safeLimit);
                statement.setInt(3, safeOffset);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) records.add(readAuction(rs));
                }
            }
            return records;
        });
    }

    public CompletableFuture<List<AuctionRecord>> getPlayerAuctions(UUID sellerUuid, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return submit(connection -> {
            List<AuctionRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM gasmp_auctions WHERE seller_uuid=? AND status='ACTIVE' AND expires_at_ms>? ORDER BY created_at_ms DESC LIMIT ?")) {
                statement.setString(1, sellerUuid.toString());
                statement.setLong(2, System.currentTimeMillis());
                statement.setInt(3, safeLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) records.add(readAuction(rs));
                }
            }
            return records;
        });
    }

    public CompletableFuture<AuctionRecord> getAuction(long id) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM gasmp_auctions WHERE id=?")) {
                statement.setLong(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? readAuction(rs) : null;
                }
            }
        });
    }

    public CompletableFuture<Boolean> reserveAuction(long id, UUID buyerUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE gasmp_auctions SET status='RESERVED',buyer_uuid=?,reserved_at_ms=? " +
                    "WHERE id=? AND status='ACTIVE' AND expires_at_ms>?")) {
                statement.setString(1, buyerUuid.toString());
                statement.setLong(2, System.currentTimeMillis());
                statement.setLong(3, id);
                statement.setLong(4, System.currentTimeMillis());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> completeAuction(long id, UUID buyerUuid) {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                String itemData;
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT item_data FROM gasmp_auctions WHERE id=? AND status='RESERVED' AND buyer_uuid=?")) {
                    read.setLong(1, id);
                    read.setString(2, buyerUuid.toString());
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) { connection.commit(); return false; }
                        itemData = rs.getString("item_data");
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE gasmp_auctions SET status='SOLD' WHERE id=? AND status='RESERVED' AND buyer_uuid=?")) {
                    update.setLong(1, id);
                    update.setString(2, buyerUuid.toString());
                    if (update.executeUpdate() != 1) { connection.rollback(); return false; }
                }
                try (PreparedStatement mail = connection.prepareStatement(
                        "INSERT INTO gasmp_item_mail(player_uuid,item_data,reason,status,created_at_ms) VALUES(?,?,?,'PENDING',?)")) {
                    mail.setString(1, buyerUuid.toString());
                    mail.setString(2, itemData);
                    mail.setString(3, "Auction purchase #" + id);
                    mail.setLong(4, System.currentTimeMillis());
                    mail.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Boolean> releaseAuction(long id, UUID buyerUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE gasmp_auctions SET status='ACTIVE',buyer_uuid=NULL,reserved_at_ms=0 " +
                    "WHERE id=? AND status='RESERVED' AND buyer_uuid=?")) {
                statement.setLong(1, id);
                statement.setString(2, buyerUuid.toString());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> markAuctionForReview(long id) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE gasmp_auctions SET status='REVIEW' WHERE id=? AND status='RESERVED'")) {
                statement.setLong(1, id);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<AuctionRecord> cancelAuction(long id, UUID sellerUuid) {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                AuctionRecord record = null;
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT * FROM gasmp_auctions WHERE id=? AND seller_uuid=? AND status='ACTIVE'")) {
                    read.setLong(1, id);
                    read.setString(2, sellerUuid.toString());
                    try (ResultSet rs = read.executeQuery()) { if (rs.next()) record = readAuction(rs); }
                }
                if (record == null) { connection.commit(); return null; }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE gasmp_auctions SET status='CANCELLED' WHERE id=? AND seller_uuid=? AND status='ACTIVE'")) {
                    update.setLong(1, id);
                    update.setString(2, sellerUuid.toString());
                    if (update.executeUpdate() != 1) { connection.rollback(); return null; }
                }
                try (PreparedStatement mail = connection.prepareStatement(
                        "INSERT INTO gasmp_item_mail(player_uuid,item_data,reason,status,created_at_ms) VALUES(?,?,?,'PENDING',?)")) {
                    mail.setString(1, sellerUuid.toString());
                    mail.setString(2, record.itemData());
                    mail.setString(3, "Cancelled auction #" + id);
                    mail.setLong(4, System.currentTimeMillis());
                    mail.executeUpdate();
                }
                connection.commit();
                return record;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally { connection.setAutoCommit(true); }
        });
    }

    public CompletableFuture<List<AuctionRecord>> expireAuctions() {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                List<AuctionRecord> candidates = new ArrayList<>();
                List<AuctionRecord> actuallyExpired = new ArrayList<>();
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT * FROM gasmp_auctions WHERE status='ACTIVE' AND expires_at_ms<=? ORDER BY id ASC")) {
                    select.setLong(1, now);
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) candidates.add(readAuction(rs));
                    }
                }
                for (AuctionRecord record : candidates) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE gasmp_auctions SET status='EXPIRED' WHERE id=? AND status='ACTIVE' AND expires_at_ms<=?")) {
                        update.setLong(1, record.id());
                        update.setLong(2, now);
                        if (update.executeUpdate() != 1) continue;
                    }
                    try (PreparedStatement mail = connection.prepareStatement(
                            "INSERT INTO gasmp_item_mail(player_uuid,item_data,reason,status,created_at_ms) VALUES(?,?,?,'PENDING',?)")) {
                        mail.setString(1, record.sellerUuid().toString());
                        mail.setString(2, record.itemData());
                        mail.setString(3, "Expired auction #" + record.id());
                        mail.setLong(4, now);
                        mail.executeUpdate();
                    }
                    actuallyExpired.add(record);
                }
                connection.commit();
                return actuallyExpired;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public record AuctionRecovery(int heldForReview) { }

    /**
     * Abandoned payment reservations are never automatically reopened. A process may have
     * stopped after the buyer debit or seller credit but before the SOLD/mail transaction.
     * Reopening such a listing could sell the same item twice or leave a player charged.
     * Stale reservations therefore move to REVIEW and retain their buyer UUID for an owner
     * to reconcile against the economy provider.
     */
    public CompletableFuture<AuctionRecovery> recoverStaleAuctionReservations(long timeoutMillis) {
        long timeout = Math.max(30_000L, Math.min(timeoutMillis, 30L * 60L * 1000L));
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                long cutoff = System.currentTimeMillis() - timeout;
                int held;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE gasmp_auctions SET status='REVIEW' " +
                        "WHERE status='RESERVED' AND (reserved_at_ms=0 OR reserved_at_ms<=?)")) {
                    update.setLong(1, cutoff);
                    held = update.executeUpdate();
                }
                connection.commit();
                return new AuctionRecovery(held);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<List<AuctionRecord>> getAuctionsUnderReview(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return submit(connection -> {
            List<AuctionRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM gasmp_auctions WHERE status='REVIEW' ORDER BY id ASC LIMIT ?")) {
                statement.setInt(1, safeLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) records.add(readAuction(rs));
                }
            }
            return records;
        });
    }

    /** Owner-confirmed resolution for a REVIEW listing: reopen it, or return it if expired. */
    public CompletableFuture<AuctionRecord> releaseReviewedAuction(long id) {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                AuctionRecord record = null;
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT * FROM gasmp_auctions WHERE id=? AND status='REVIEW'")) {
                    read.setLong(1, id);
                    try (ResultSet rs = read.executeQuery()) {
                        if (rs.next()) record = readAuction(rs);
                    }
                }
                if (record == null) {
                    connection.commit();
                    return null;
                }

                boolean expired = record.expiresAtMs() <= System.currentTimeMillis();
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE gasmp_auctions SET status=?,buyer_uuid=NULL,reserved_at_ms=0 " +
                        "WHERE id=? AND status='REVIEW'")) {
                    update.setString(1, expired ? "EXPIRED" : "ACTIVE");
                    update.setLong(2, id);
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return null;
                    }
                }
                if (expired) {
                    try (PreparedStatement mail = connection.prepareStatement(
                            "INSERT INTO gasmp_item_mail(player_uuid,item_data,reason,status,created_at_ms) " +
                            "VALUES(?,?,?,'PENDING',?)")) {
                        mail.setString(1, record.sellerUuid().toString());
                        mail.setString(2, record.itemData());
                        mail.setString(3, "Reviewed expired auction #" + id);
                        mail.setLong(4, System.currentTimeMillis());
                        mail.executeUpdate();
                    }
                }
                connection.commit();
                return record;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    /** Owner-confirmed resolution for REVIEW after verifying buyer charge and seller credit. */
    public CompletableFuture<AuctionRecord> settleReviewedAuction(long id) {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                AuctionRecord record = null;
                try (PreparedStatement read = connection.prepareStatement(
                        "SELECT * FROM gasmp_auctions WHERE id=? AND status='REVIEW' AND buyer_uuid IS NOT NULL")) {
                    read.setLong(1, id);
                    try (ResultSet rs = read.executeQuery()) {
                        if (rs.next()) record = readAuction(rs);
                    }
                }
                if (record == null) {
                    connection.commit();
                    return null;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE gasmp_auctions SET status='SOLD' WHERE id=? AND status='REVIEW' AND buyer_uuid=?")) {
                    update.setLong(1, id);
                    update.setString(2, record.buyerUuid().toString());
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        return null;
                    }
                }
                try (PreparedStatement mail = connection.prepareStatement(
                        "INSERT INTO gasmp_item_mail(player_uuid,item_data,reason,status,created_at_ms) " +
                        "VALUES(?,?,?,'PENDING',?)")) {
                    mail.setString(1, record.buyerUuid().toString());
                    mail.setString(2, record.itemData());
                    mail.setString(3, "Owner-settled auction #" + id);
                    mail.setLong(4, System.currentTimeMillis());
                    mail.executeUpdate();
                }
                connection.commit();
                return record;
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    private AuctionRecord readAuction(ResultSet rs) throws SQLException {
        String buyer = rs.getString("buyer_uuid");
        return new AuctionRecord(rs.getLong("id"), UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"), rs.getString("item_data"), rs.getString("item_name"),
                rs.getDouble("price"), rs.getLong("created_at_ms"), rs.getLong("expires_at_ms"),
                rs.getString("status"), buyer == null ? null : UUID.fromString(buyer));
    }

    public CompletableFuture<Long> addBattlePassXp(UUID uuid, String playerName, String seasonId, long amount) {
        if (amount <= 0) return CompletableFuture.failedFuture(new IllegalArgumentException("XP amount must be positive."));
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_battlepass_progress(player_uuid,player_name,season_id,xp) VALUES(?,?,?,?) " +
                    "ON CONFLICT(player_uuid,season_id) DO UPDATE SET player_name=excluded.player_name,xp=gasmp_battlepass_progress.xp+excluded.xp")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, playerName);
                statement.setString(3, seasonId);
                statement.setLong(4, amount);
                statement.executeUpdate();
            }
            try (PreparedStatement read = connection.prepareStatement(
                    "SELECT xp FROM gasmp_battlepass_progress WHERE player_uuid=? AND season_id=?")) {
                read.setString(1, uuid.toString()); read.setString(2, seasonId);
                try (ResultSet rs = read.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        });
    }

    public CompletableFuture<Long> getBattlePassXp(UUID uuid, String seasonId) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT xp FROM gasmp_battlepass_progress WHERE player_uuid=? AND season_id=?")) {
                statement.setString(1, uuid.toString()); statement.setString(2, seasonId);
                try (ResultSet rs = statement.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        });
    }

    public CompletableFuture<Boolean> claimBattlePassLevel(UUID uuid, String seasonId, int level) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO gasmp_battlepass_claims(player_uuid,season_id,level,claimed_at_ms) VALUES(?,?,?,?)")) {
                statement.setString(1, uuid.toString()); statement.setString(2, seasonId);
                statement.setInt(3, level); statement.setLong(4, System.currentTimeMillis());
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Void> unclaimBattlePassLevel(UUID uuid, String seasonId, int level) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM gasmp_battlepass_claims WHERE player_uuid=? AND season_id=? AND level=?")) {
                statement.setString(1, uuid.toString()); statement.setString(2, seasonId); statement.setInt(3, level);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Set<Integer>> getClaimedBattlePassLevels(UUID uuid, String seasonId) {
        return submit(connection -> {
            Set<Integer> levels = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT level FROM gasmp_battlepass_claims WHERE player_uuid=? AND season_id=?")) {
                statement.setString(1, uuid.toString()); statement.setString(2, seasonId);
                try (ResultSet rs = statement.executeQuery()) { while (rs.next()) levels.add(rs.getInt(1)); }
            }
            return levels;
        });
    }

    public CompletableFuture<Void> recordCasinoTransaction(UUID playerUuid, String playerName,
                                                              String gameKey, double bet, double payout, String outcome) {
        if (!Double.isFinite(bet) || !Double.isFinite(payout) || bet < 0 || payout < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid casino transaction values."));
        }
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_casino_transactions(player_uuid,player_name,game_key,bet,payout,outcome,created_at_ms) VALUES(?,?,?,?,?,?,?)")) {
                statement.setString(1, playerUuid.toString()); statement.setString(2, playerName);
                statement.setString(3, gameKey); statement.setDouble(4, bet); statement.setDouble(5, payout);
                statement.setString(6, outcome); statement.setLong(7, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public record ModerationRecord(long id, UUID targetUuid, String targetName, UUID actorUuid,
                                   String actorName, String action, String reason,
                                   long createdAtMs, long expiresAtMs, boolean active) { }

    public CompletableFuture<Long> recordModeration(UUID targetUuid, String targetName, UUID actorUuid,
            String actorName, String action, String reason, long expiresAtMs, boolean active) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_moderation_actions(target_uuid,target_name,actor_uuid,actor_name,action,reason,created_at_ms,expires_at_ms,active) VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, targetUuid.toString()); statement.setString(2, targetName);
                statement.setString(3, actorUuid == null ? null : actorUuid.toString());
                statement.setString(4, actorName); statement.setString(5, action);
                statement.setString(6, reason == null ? "No reason supplied" : reason);
                statement.setLong(7, System.currentTimeMillis()); statement.setLong(8, expiresAtMs);
                statement.setInt(9, active ? 1 : 0); statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) { return keys.next() ? keys.getLong(1) : -1L; }
            }
        });
    }

    public CompletableFuture<ModerationRecord> getActiveMute(UUID targetUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM gasmp_moderation_actions WHERE target_uuid=? AND action='MUTE' AND active=1 ORDER BY created_at_ms DESC LIMIT 1")) {
                statement.setString(1, targetUuid.toString());
                try (ResultSet rs = statement.executeQuery()) { return rs.next() ? readModeration(rs) : null; }
            }
        });
    }

    public CompletableFuture<Integer> deactivateMutes(UUID targetUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE gasmp_moderation_actions SET active=0 WHERE target_uuid=? AND action='MUTE' AND active=1")) {
                statement.setString(1, targetUuid.toString()); return statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<ModerationRecord>> getModerationHistory(UUID targetUuid, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return submit(connection -> {
            List<ModerationRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM gasmp_moderation_actions WHERE target_uuid=? ORDER BY created_at_ms DESC LIMIT ?")) {
                statement.setString(1, targetUuid.toString()); statement.setInt(2, safeLimit);
                try (ResultSet rs = statement.executeQuery()) { while (rs.next()) records.add(readModeration(rs)); }
            }
            return records;
        });
    }

    private ModerationRecord readModeration(ResultSet rs) throws SQLException {
        String actor = rs.getString("actor_uuid");
        return new ModerationRecord(rs.getLong("id"), UUID.fromString(rs.getString("target_uuid")),
                rs.getString("target_name"), actor == null ? null : UUID.fromString(actor),
                rs.getString("actor_name"), rs.getString("action"), rs.getString("reason"),
                rs.getLong("created_at_ms"), rs.getLong("expires_at_ms"), rs.getInt("active") != 0);
    }

    public CompletableFuture<List<String>> getLeaderboard(String stat, int limit) {
        String column = switch (stat.toLowerCase(Locale.ROOT)) {
            case "kills" -> "kills";
            case "deaths" -> "deaths";
            case "playtime" -> "play_ticks";
            case "shards" -> "shards";
            default -> null;
        };
        if (column == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown leaderboard statistic."));
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return submit(connection -> {
            List<String> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT last_name," + column + " AS score FROM gasmp_players ORDER BY " + column + " DESC LIMIT ?")) {
                statement.setInt(1, safeLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) rows.add(rs.getString("last_name") + "|" + rs.getLong("score"));
                }
            }
            return rows;
        });
    }

    public record TeamRecord(String teamId, String teamName, UUID leaderUuid, long createdAtMs) { }
    public record TeamMember(UUID playerUuid, String playerName, long joinedAtMs, String role) { }
    public record TeamStats(int members, int maxMembers, int level, int xp, double bank, String description) { }
    public record TeamHome(String world, double x, double y, double z, float yaw, float pitch) { }
    public record TeamHomeEntry(String key, String world, double x, double y, double z, float yaw, float pitch) { }

    public CompletableFuture<TeamRecord> createTeam(String teamId, String teamName, UUID leaderUuid, String leaderName, int initialCapacity) {
        if (initialCapacity < 2 || initialCapacity > 100) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Initial team capacity must be between 2 and 100."));
        }
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO gasmp_teams(team_id,team_name,leader_uuid,created_at_ms,max_members) VALUES(?,?,?,?,?)")) {
                    insert.setString(1, teamId); insert.setString(2, teamName);
                    insert.setString(3, leaderUuid.toString()); insert.setLong(4, now);
                    insert.setInt(5, initialCapacity); insert.executeUpdate();
                }
                try (PreparedStatement member = connection.prepareStatement(
                        "INSERT INTO gasmp_team_members(player_uuid,team_id,joined_at_ms,role) VALUES(?,?,?,'LEADER')")) {
                    member.setString(1, leaderUuid.toString()); member.setString(2, teamId); member.setLong(3, now); member.executeUpdate();
                }
                connection.commit();
                return new TeamRecord(teamId, teamName, leaderUuid, now);
            } catch (Exception ex) {
                connection.rollback(); throw ex;
            } finally { connection.setAutoCommit(true); }
        });
    }

    public CompletableFuture<TeamRecord> getTeamByMember(UUID playerUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT t.* FROM gasmp_teams t JOIN gasmp_team_members m ON t.team_id=m.team_id WHERE m.player_uuid=?")) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? new TeamRecord(rs.getString("team_id"), rs.getString("team_name"),
                            UUID.fromString(rs.getString("leader_uuid")), rs.getLong("created_at_ms")) : null;
                }
            }
        });
    }

    public CompletableFuture<TeamRecord> getTeamByName(String teamName) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM gasmp_teams WHERE team_name=?")) {
                statement.setString(1, teamName);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? new TeamRecord(rs.getString("team_id"), rs.getString("team_name"),
                            UUID.fromString(rs.getString("leader_uuid")), rs.getLong("created_at_ms")) : null;
                }
            }
        });
    }

    public CompletableFuture<Boolean> addTeamMember(String teamId, UUID playerUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO gasmp_team_members(player_uuid,team_id,joined_at_ms,role) " +
                    "SELECT ?,?,?, 'MEMBER' WHERE (SELECT COUNT(*) FROM gasmp_team_members WHERE team_id=?) " +
                    "< (SELECT max_members FROM gasmp_teams WHERE team_id=?)")) {
                statement.setString(1, playerUuid.toString()); statement.setString(2, teamId);
                statement.setLong(3, System.currentTimeMillis()); statement.setString(4, teamId); statement.setString(5, teamId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> removeTeamMember(UUID playerUuid, String teamId) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM gasmp_team_members WHERE player_uuid=? AND team_id=?")) {
                statement.setString(1, playerUuid.toString()); statement.setString(2, teamId);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<List<TeamMember>> getTeamMembers(String teamId) {
        return submit(connection -> {
            List<TeamMember> members = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT m.player_uuid,m.joined_at_ms,m.role,p.last_name FROM gasmp_team_members m " +
                    "LEFT JOIN gasmp_players p ON p.uuid=m.player_uuid WHERE m.team_id=? ORDER BY " +
                    "CASE m.role WHEN 'LEADER' THEN 0 WHEN 'COLEADER' THEN 1 WHEN 'OFFICER' THEN 2 ELSE 3 END, m.joined_at_ms ASC")) {
                statement.setString(1, teamId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) members.add(new TeamMember(UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("last_name") == null ? "Unknown" : rs.getString("last_name"), rs.getLong("joined_at_ms"), rs.getString("role")));
                }
            }
            return members;
        });
    }

    public CompletableFuture<String> getTeamRole(UUID playerUuid) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT role FROM gasmp_team_members WHERE player_uuid=?")) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet rs = statement.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
            }
        });
    }

    public CompletableFuture<Boolean> setTeamRole(String teamId, UUID playerUuid, String role) {
        String normalized = role.toUpperCase(Locale.ROOT);
        if (!Set.of("MEMBER", "OFFICER", "COLEADER").contains(normalized))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid team role."));
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE gasmp_team_members SET role=? WHERE team_id=? AND player_uuid=? AND role<>'LEADER'")) {
                statement.setString(1, normalized); statement.setString(2, teamId);
                statement.setString(3, playerUuid.toString()); return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> transferTeamLeader(String teamId, UUID oldLeader, UUID newLeader) {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM gasmp_team_members WHERE team_id=? AND player_uuid=?")) {
                    check.setString(1, teamId); check.setString(2, newLeader.toString());
                    try (ResultSet rs = check.executeQuery()) { if (!rs.next()) return rollbackFalse(connection); }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE gasmp_teams SET leader_uuid=? WHERE team_id=? AND leader_uuid=?")) {
                    update.setString(1, newLeader.toString()); update.setString(2, teamId); update.setString(3, oldLeader.toString());
                    if (update.executeUpdate() != 1) return rollbackFalse(connection);
                }
                try (PreparedStatement roles = connection.prepareStatement(
                        "UPDATE gasmp_team_members SET role=CASE WHEN player_uuid=? THEN 'LEADER' WHEN player_uuid=? THEN 'COLEADER' ELSE role END WHERE team_id=?")) {
                    roles.setString(1, newLeader.toString()); roles.setString(2, oldLeader.toString()); roles.setString(3, teamId); roles.executeUpdate();
                }
                connection.commit(); return true;
            } catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        });
    }

    private boolean rollbackFalse(Connection connection) throws SQLException { connection.rollback(); return false; }

    public CompletableFuture<TeamStats> getTeamStats(String teamId) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT t.description,t.bank_balance,t.level,t.xp,t.max_members," +
                    "(SELECT COUNT(*) FROM gasmp_team_members m WHERE m.team_id=t.team_id) AS members " +
                    "FROM gasmp_teams t WHERE t.team_id=?")) {
                statement.setString(1, teamId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) return null;
                    return new TeamStats(rs.getInt("members"), rs.getInt("max_members"), rs.getInt("level"),
                            rs.getInt("xp"), rs.getDouble("bank_balance"), rs.getString("description"));
                }
            }
        });
    }

    public CompletableFuture<Boolean> setTeamDescription(String teamId, String description) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE gasmp_teams SET description=? WHERE team_id=?")) {
                statement.setString(1, description); statement.setString(2, teamId); return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> changeTeamBank(String teamId, double amount, boolean deposit) {
        if (!Double.isFinite(amount) || amount <= 0.0)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Amount must be a positive finite number."));
        return submit(connection -> {
            String sql = deposit
                    ? "UPDATE gasmp_teams SET bank_balance=bank_balance+? WHERE team_id=?"
                    : "UPDATE gasmp_teams SET bank_balance=bank_balance-? WHERE team_id=? AND bank_balance>=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDouble(1, amount); statement.setString(2, teamId);
                if (!deposit) statement.setDouble(3, amount);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> upgradeTeamCapacity(String teamId, double cost, int addedSlots, int maximumCapacity) {
        if (!Double.isFinite(cost) || cost <= 0 || addedSlots < 1 || addedSlots > 10
                || maximumCapacity < 2 || maximumCapacity > 100) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid team upgrade."));
        }
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE gasmp_teams SET bank_balance=bank_balance-?,max_members=max_members+?,level=level+1 " +
                    "WHERE team_id=? AND bank_balance>=? AND max_members+?<=?")) {
                statement.setDouble(1, cost); statement.setInt(2, addedSlots);
                statement.setString(3, teamId); statement.setDouble(4, cost);
                statement.setInt(5, addedSlots); statement.setInt(6, maximumCapacity);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> setTeamHome(String teamId, String homeKey, String world,
            double x, double y, double z, float yaw, float pitch) {
        if (!homeKey.matches("[a-zA-Z0-9_-]{1,16}"))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid home name."));
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO gasmp_team_homes(team_id,home_key,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT(team_id,home_key) DO UPDATE SET world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch")) {
                statement.setString(1, teamId); statement.setString(2, homeKey.toLowerCase(Locale.ROOT));
                statement.setString(3, world); statement.setDouble(4, x); statement.setDouble(5, y);
                statement.setDouble(6, z); statement.setFloat(7, yaw); statement.setFloat(8, pitch);
                return statement.executeUpdate() == 1;
            }
        });
    }

    public CompletableFuture<Boolean> setTeamHomeLimited(String teamId, String homeKey, String world,
            double x, double y, double z, float yaw, float pitch, int maximumHomes) {
        if (!homeKey.matches("[a-zA-Z0-9_-]{1,16}") || maximumHomes < 1 || maximumHomes > 10) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid team-home settings."));
        }
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                boolean exists;
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT 1 FROM gasmp_team_homes WHERE team_id=? AND home_key=?")) {
                    query.setString(1, teamId); query.setString(2, homeKey.toLowerCase(Locale.ROOT));
                    try (ResultSet rs = query.executeQuery()) { exists = rs.next(); }
                }
                if (!exists) {
                    try (PreparedStatement count = connection.prepareStatement(
                            "SELECT COUNT(*) FROM gasmp_team_homes WHERE team_id=?")) {
                        count.setString(1, teamId);
                        try (ResultSet rs = count.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) >= maximumHomes) {
                                connection.rollback();
                                return false;
                            }
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gasmp_team_homes(team_id,home_key,world,x,y,z,yaw,pitch) VALUES(?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(team_id,home_key) DO UPDATE SET world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch")) {
                    statement.setString(1, teamId); statement.setString(2, homeKey.toLowerCase(Locale.ROOT));
                    statement.setString(3, world); statement.setDouble(4, x); statement.setDouble(5, y);
                    statement.setDouble(6, z); statement.setFloat(7, yaw); statement.setFloat(8, pitch);
                    boolean saved = statement.executeUpdate() == 1;
                    connection.commit();
                    return saved;
                }
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Integer> getTeamHomeCount(String teamId) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM gasmp_team_homes WHERE team_id=?")) {
                statement.setString(1, teamId);
                try (ResultSet rs = statement.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
            }
        });
    }

    public CompletableFuture<TeamHome> getTeamHome(String teamId, String homeKey) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT world,x,y,z,yaw,pitch FROM gasmp_team_homes WHERE team_id=? AND home_key=?")) {
                statement.setString(1, teamId); statement.setString(2, homeKey.toLowerCase(Locale.ROOT));
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? new TeamHome(rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                            rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")) : null;
                }
            }
        });
    }

    public CompletableFuture<List<TeamHomeEntry>> getTeamHomes(String teamId) {
        return submit(connection -> {
            List<TeamHomeEntry> homes = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT home_key,world,x,y,z,yaw,pitch FROM gasmp_team_homes WHERE team_id=? ORDER BY home_key COLLATE NOCASE")) {
                statement.setString(1, teamId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) homes.add(new TeamHomeEntry(
                            rs.getString("home_key"), rs.getString("world"), rs.getDouble("x"),
                            rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
                }
            }
            return homes;
        });
    }

    public CompletableFuture<Boolean> addTeamAlliance(String teamId, String allyTeamId) {
        if (teamId.equals(allyTeamId)) return CompletableFuture.completedFuture(false);
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO gasmp_team_allies(team_id,ally_team_id,created_at_ms) VALUES(?,?,?)")) {
                    statement.setString(1, teamId); statement.setString(2, allyTeamId); statement.setLong(3, now);
                    int first = statement.executeUpdate();
                    statement.setString(1, allyTeamId); statement.setString(2, teamId); statement.setLong(3, now);
                    int second = statement.executeUpdate();
                    if (first == 0 || second == 0) { connection.rollback(); return false; }
                }
                connection.commit(); return true;
            } catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        });
    }

    public CompletableFuture<Boolean> addTeamAllianceLimited(String teamId, String allyTeamId, int maximumAlliances) {
        if (teamId.equals(allyTeamId)) return CompletableFuture.completedFuture(false);
        if (maximumAlliances < 0 || maximumAlliances > 20) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid alliance limit."));
        }
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                for (String id : List.of(teamId, allyTeamId)) {
                    try (PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM gasmp_teams WHERE team_id=?")) {
                        exists.setString(1, id);
                        try (ResultSet rs = exists.executeQuery()) {
                            if (!rs.next()) { connection.rollback(); return false; }
                        }
                    }
                    try (PreparedStatement count = connection.prepareStatement(
                            "SELECT COUNT(*) FROM gasmp_team_allies WHERE team_id=?")) {
                        count.setString(1, id);
                        try (ResultSet rs = count.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) >= maximumAlliances) {
                                connection.rollback();
                                return false;
                            }
                        }
                    }
                }
                long now = System.currentTimeMillis();
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO gasmp_team_allies(team_id,ally_team_id,created_at_ms) VALUES(?,?,?)")) {
                    statement.setString(1, teamId); statement.setString(2, allyTeamId); statement.setLong(3, now);
                    int first = statement.executeUpdate();
                    statement.setString(1, allyTeamId); statement.setString(2, teamId); statement.setLong(3, now);
                    int second = statement.executeUpdate();
                    if (first != 1 || second != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public CompletableFuture<Boolean> removeTeamAlliance(String teamId, String allyTeamId) {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM gasmp_team_allies WHERE (team_id=? AND ally_team_id=?) OR (team_id=? AND ally_team_id=?)")) {
                statement.setString(1, teamId); statement.setString(2, allyTeamId);
                statement.setString(3, allyTeamId); statement.setString(4, teamId);
                boolean removed = statement.executeUpdate() > 0;
                connection.commit(); return removed;
            } catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        });
    }

    public CompletableFuture<Integer> getTeamAllianceCount(String teamId) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM gasmp_team_allies WHERE team_id=?")) {
                statement.setString(1, teamId);
                try (ResultSet rs = statement.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
            }
        });
    }

    public CompletableFuture<List<String>> getTeamAllies(String teamId) {
        return submit(connection -> {
            List<String> allies = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT t.team_name FROM gasmp_team_allies a JOIN gasmp_teams t ON t.team_id=a.ally_team_id WHERE a.team_id=? ORDER BY t.team_name")) {
                statement.setString(1, teamId);
                try (ResultSet rs = statement.executeQuery()) { while (rs.next()) allies.add(rs.getString(1)); }
            }
            return allies;
        });
    }

    public CompletableFuture<Boolean> disbandTeam(String teamId, UUID leaderUuid) {
        return submit(connection -> {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteMembers = connection.prepareStatement("DELETE FROM gasmp_team_members WHERE team_id=?")) {
                    deleteMembers.setString(1, teamId); deleteMembers.executeUpdate();
                }
                try (PreparedStatement deleteTeam = connection.prepareStatement("DELETE FROM gasmp_teams WHERE team_id=? AND leader_uuid=?")) {
                    deleteTeam.setString(1, teamId); deleteTeam.setString(2, leaderUuid.toString());
                    boolean removed = deleteTeam.executeUpdate() == 1;
                    if (!removed) { connection.rollback(); return false; }
                    connection.commit(); return true;
                }
            } catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        });
    }

    private <T> CompletableFuture<T> submit(SqlWork<T> work) {
        return submit(work, false);
    }

    private <T> CompletableFuture<T> submit(SqlWork<T> work, boolean open) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (open) {
                    File parent = databaseFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new SQLException("Could not create database folder " + parent);
                    }
                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                }
                if (connection == null || connection.isClosed()) {
                    throw new SQLException("GASMP SQLite storage is not initialized.");
                }
                return work.run(connection);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    @Override
    public void close() {
        try {
            executor.submit(() -> {
                try {
                    if (connection != null && !connection.isClosed()) connection.close();
                } catch (SQLException closeError) {
                    throw new CompletionException(closeError);
                }
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Do not prevent the server from shutting down.
        } finally {
            executor.shutdown();
        }
    }
}
