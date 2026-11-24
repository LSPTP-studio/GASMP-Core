package net.gasmp.core.rank;

import net.gasmp.core.GASMPCore;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.WeightNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class RankService {
    public record Access(boolean authorized, boolean owner, Set<String> groups) { }
    public record GrantResult(boolean success, String message, RankDefinition rank,
                              String previousPrimaryGroup, String newPrimaryGroup) { }

    private final GASMPCore plugin;
    private final Map<String, RankDefinition> byKey = new LinkedHashMap<>();
    private final Map<String, RankDefinition> byGroup = new HashMap<>();
    private final Set<String> adminGroups = new HashSet<>();
    private final Set<String> ownerOnlyRanks = new HashSet<>();
    private final Set<String> protectedTargetRanks = new HashSet<>();
    private String ownerGroup = "owner";
    private String developerGroup = "dev";
    private int prefixPriority = 100;
    private LuckPerms luckPerms;
    private CompletableFuture<Void> ready = CompletableFuture.failedFuture(
            new IllegalStateException("Rank system has not initialized."));

    public RankService(GASMPCore plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    public void loadConfiguration() {
        File file = new File(plugin.getDataFolder(), "ranks.yml");
        if (!file.exists()) plugin.saveResource("ranks.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        byKey.clear();
        byGroup.clear();
        adminGroups.clear();
        ownerOnlyRanks.clear();
        protectedTargetRanks.clear();

        ConfigurationSection admin = yaml.getConfigurationSection("rank-admin");
        if (admin != null) {
            ownerGroup = admin.getString("owner-group", "owner").toLowerCase(Locale.ROOT);
            developerGroup = admin.getString("developer-group", "dev").toLowerCase(Locale.ROOT);
            prefixPriority = admin.getInt("prefix-priority", 100);
            adminGroups.addAll(lower(admin.getStringList("admin-groups")));
            ownerOnlyRanks.addAll(lower(admin.getStringList("owner-only-ranks")));
            protectedTargetRanks.addAll(lower(admin.getStringList("protected-target-ranks")));
        }

        ConfigurationSection ranks = yaml.getConfigurationSection("ranks");
        if (ranks == null) {
            plugin.getLogger().severe("ranks.yml has no 'ranks' section.");
            return;
        }

        for (String key : ranks.getKeys(false)) {
            ConfigurationSection row = ranks.getConfigurationSection(key);
            if (row == null) continue;
            String group = row.getString("group", key).toLowerCase(Locale.ROOT);
            String display = row.getString("display-name", key.toUpperCase(Locale.ROOT));
            String track = row.getString("track", "supporter").toLowerCase(Locale.ROOT);
            int weight = row.getInt("weight", 1);
            String prefix = row.getString("prefix", "&7[" + display + "] &f");
            String image = row.getString("image", "gasmp_" + key);
            Material icon = Material.matchMaterial(row.getString("material", "PAPER"));
            if (icon == null) icon = Material.PAPER;

            RankDefinition definition = new RankDefinition(
                    key.toLowerCase(Locale.ROOT), group, display, track, weight, prefix, image, icon
            );
            byKey.put(definition.key(), definition);
            byGroup.put(definition.group(), definition);
        }
    }

    public CompletableFuture<Void> initialize() {
        if (!plugin.getConfig().getBoolean("modules.rank-admin", true)) {
            ready = CompletableFuture.completedFuture(null);
            return ready;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            ready = CompletableFuture.failedFuture(new IllegalStateException("LuckPerms is not installed or enabled."));
            return ready;
        }

        luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            ready = CompletableFuture.failedFuture(new IllegalStateException("LuckPerms API service is unavailable."));
            return ready;
        }

        var manager = luckPerms.getGroupManager();
        Map<String, Set<String>> groupPermissions = new HashMap<>();
        grant(groupPermissions, "owner", "gasmpcore.rankadmin", "gasmpcore.admin", "gasmpcore.staff.vanish", "gasmpcore.maintenance.bypass", "gasmpcore.shards.admin", "gasmpcore.crates.manage", "gasmpcore.casino.admin", "gasmpcore.combat.bypass", "gasmpcore.staff.headdb", "headdb.command.open", "headdb.command.search", "headdb.command.give", "headdb.category.*", "libsdisguises.disguise.*", "libsdisguises.undisguise", "openinv.inventory.open.other", "openinv.enderchest.open.other", "openinv.search.inventory", "openinv.access.online", "skript.admin");
        grant(groupPermissions, "dev", "gasmpcore.rankadmin", "gasmpcore.admin", "gasmpcore.staff.vanish", "gasmpcore.maintenance.bypass", "gasmpcore.shards.admin", "gasmpcore.crates.manage", "gasmpcore.casino.admin", "gasmpcore.staff.headdb", "headdb.command.open", "headdb.command.search", "headdb.command.give", "headdb.category.*", "libsdisguises.disguise.*", "libsdisguises.undisguise", "openinv.inventory.open.other", "openinv.enderchest.open.other", "openinv.search.inventory", "openinv.access.online", "skript.admin");
        grant(groupPermissions, "builder", "gasmpcore.staff.vanish", "gasmpcore.staff.headdb", "headdb.command.open", "headdb.command.search", "headdb.category.*");
        grant(groupPermissions, "manager", "gasmpcore.staff.vanish", "gasmpcore.maintenance.bypass");
        grant(groupPermissions, "admin", "gasmpcore.staff.vanish", "gasmpcore.maintenance.bypass", "gasmpcore.shards.admin", "openinv.inventory.open.other", "openinv.enderchest.open.other", "openinv.search.inventory", "openinv.access.online");
        grant(groupPermissions, "mod", "gasmpcore.staff.vanish");
        grant(groupPermissions, "helper", "gasmpcore.staff.vanish");

        // ItemsAdder rank glyph permissions are synchronized to the matching LuckPerms group
        // has the badge permission. Keep this in sync with ItemsAdder font_images.yml.
        for (RankDefinition definition : byKey.values()) {
            grant(groupPermissions, definition.group(),
                    "gasmp.tag." + definition.key());
        }

        Map<String, RankDefinition> groupsToCreate = new LinkedHashMap<>();
        for (RankDefinition rank : byKey.values()) groupsToCreate.put(rank.group(), rank);
        for (String name : groupPermissions.keySet()) groupsToCreate.putIfAbsent(name, null);

        List<CompletableFuture<?>> tasks = new ArrayList<>();
        for (Map.Entry<String, RankDefinition> entry : groupsToCreate.entrySet()) {
            String groupName = entry.getKey();
            RankDefinition rank = entry.getValue();
            tasks.add(manager.createAndLoadGroup(groupName).thenCompose(group -> {
                if (rank != null) {
                    // GASMP owns only the global permanent rank prefix and weight nodes.
                    group.data().clear(node -> (node instanceof PrefixNode || node instanceof WeightNode)
                            && node.getContexts().isEmpty() && !node.hasExpiry());
                    group.data().add(WeightNode.builder(rank.weight()).build());
                    group.data().add(PrefixNode.builder(rank.prefix(), prefixPriority).build());
                }
                Set<String> wanted = groupPermissions.getOrDefault(groupName, Set.of());
                Set<String> managedPermissions = new HashSet<>(Set.of(
                        "gasmpcore.rankadmin", "gasmpcore.admin", "gasmpcore.staff.vanish",
                        "gasmpcore.maintenance.bypass", "gasmpcore.shards.admin", "gasmpcore.crates.manage",
                        "gasmpcore.casino.admin", "gasmpcore.combat.bypass", "gasmpcore.staff.headdb",
                        "headdb.command.open", "headdb.command.search", "headdb.command.give", "headdb.category.*",
                        "libsdisguises.disguise.*", "libsdisguises.undisguise",
                        "openinv.inventory.open.other", "openinv.inventory.edit.other",
                        "openinv.enderchest.open.other", "openinv.enderchest.edit.other",
                        "openinv.search.inventory", "openinv.access.online", "openinv.access.offline", "skript.admin"
                ));
                for (RankDefinition definition : byKey.values()) {
                    managedPermissions.add("gasmp.tag." + definition.key());
                }
                group.data().clear(node -> node instanceof PermissionNode
                        && (wanted.contains(node.getKey().toLowerCase(Locale.ROOT))
                        || managedPermissions.contains(node.getKey().toLowerCase(Locale.ROOT))
                        || node.getKey().startsWith("OpenInv."))
                        && node.getContexts().isEmpty() && !node.hasExpiry());
                for (String permission : wanted) group.data().add(PermissionNode.builder(permission).build());
                if (Set.of("owner", "dev", "admin").contains(groupName)) {
                    // Exact negations prevent an inherited wildcard/op permission from granting
                    // destructive inventory editing while preserving the explicit view nodes above.
                    group.data().add(PermissionNode.builder("openinv.inventory.edit.other").value(false).build());
                    group.data().add(PermissionNode.builder("openinv.enderchest.edit.other").value(false).build());
                    group.data().add(PermissionNode.builder("openinv.clear.other").value(false).build());
                }
                return manager.saveGroup(group);
            }));
        }

        ready = CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
        ready.whenComplete((ignored, error) -> {
            if (error != null) plugin.getLogger().severe("LuckPerms group setup failed: " + rootMessage(error));
            else plugin.getLogger().info("Synced " + byKey.size() + " GASMP rank groups with LuckPerms; rank panel groups: " + adminGroups);
        });
        return ready;
    }

    private static void grant(Map<String, Set<String>> permissions, String group, String... nodes) {
        permissions.computeIfAbsent(group, ignored -> new HashSet<>()).addAll(Arrays.asList(nodes));
    }

    public CompletableFuture<Access> getAccess(UUID uuid) {
        return ready.thenCompose(ignored -> luckPerms.getUserManager().loadUser(uuid))
                .thenApply(this::accessFor);
    }

    private Access accessFor(User user) {
        Set<String> groups = groupNames(user);
        boolean owner = groups.contains(ownerGroup);
        boolean authorized = adminGroups.stream().anyMatch(groups::contains);
        return new Access(authorized, owner, Set.copyOf(groups));
    }

    public CompletableFuture<GrantResult> grantRank(UUID actorUuid, UUID targetUuid, String rankKey) {
        return ready.thenCompose(ignored -> luckPerms.getUserManager().loadUser(actorUuid))
                .thenCompose(actor -> {
                    Access access = accessFor(actor);
                    if (!access.authorized()) {
                        return CompletableFuture.completedFuture(failure("Only configured Developers and Owners may use rank administration."));
                    }
                    if (actorUuid.equals(targetUuid) && !access.owner()) {
                        return CompletableFuture.completedFuture(failure("Developers cannot change their own rank."));
                    }

                    RankDefinition rank = byKey.get(rankKey.toLowerCase(Locale.ROOT));
                    if (rank == null) {
                        return CompletableFuture.completedFuture(failure("That rank is not configured in ranks.yml."));
                    }
                    if (!access.owner() && (ownerOnlyRanks.contains(rank.key())
                            || ownerOnlyRanks.contains(rank.group())
                            || rank.weight() >= weightOfGroup(developerGroup))) {
                        return CompletableFuture.completedFuture(failure("Only the Owner can grant that rank."));
                    }

                    return luckPerms.getUserManager().loadUser(targetUuid).thenCompose(target -> {
                        Set<String> oldGroups = groupNames(target);
                        if (!access.owner() && oldGroups.stream().anyMatch(protectedTargetRanks::contains)) {
                            return CompletableFuture.completedFuture(failure("Only the Owner can change a top-staff account."));
                        }

                        String previous = target.getPrimaryGroup();
                        Set<String> sameTrack = byKey.values().stream()
                                .filter(r -> r.track().equalsIgnoreCase(rank.track()))
                                .map(RankDefinition::group)
                                .collect(Collectors.toSet());

                        target.data().clear(node -> {
                            if (!(node instanceof InheritanceNode parent)) return false;
                            return sameTrack.contains(parent.getGroupName().toLowerCase(Locale.ROOT))
                                    && node.getContexts().isEmpty() && !node.hasExpiry();
                        });
                        target.data().add(InheritanceNode.builder(rank.group()).build());

                        Optional<RankDefinition> mainRank = target.getNodes().stream()
                                .filter(node -> node instanceof InheritanceNode)
                                .map(node -> ((InheritanceNode) node).getGroupName().toLowerCase(Locale.ROOT))
                                .map(byGroup::get)
                                .filter(Objects::nonNull)
                                .max(Comparator.comparingInt(RankDefinition::weight));

                        String primary = mainRank.map(RankDefinition::group).orElse(rank.group());
                        target.setPrimaryGroup(primary);
                        return luckPerms.getUserManager().saveUser(target).thenApply(nothing -> {
                            return new GrantResult(true, "Rank updated.", rank, previous, primary);
                        });
                    });
                }).exceptionally(error -> failure("LuckPerms update failed: " + rootMessage(error)));
    }

    public boolean canDeveloperGrant(RankDefinition rank) {
        return !ownerOnlyRanks.contains(rank.key())
                && !ownerOnlyRanks.contains(rank.group())
                && rank.weight() < weightOfGroup(developerGroup);
    }

    public void recordAudit(UUID actor, UUID target, GrantResult result) {
        if (result == null || !result.success()) return;
        plugin.getDataStore().recordRankChange(actor, target, result.rank().key(),
                result.previousPrimaryGroup(), result.newPrimaryGroup())
                .exceptionally(error -> {
                    plugin.getLogger().warning("Could not write rank audit record: " + error.getMessage());
                    return null;
                });
    }

    public List<RankDefinition> getRanks() { return List.copyOf(byKey.values()); }
    public RankDefinition getRank(String key) { return byKey.get(key.toLowerCase(Locale.ROOT)); }
    public String imagePlaceholder(RankDefinition rank) { return "%img_" + rank.imageKey() + "%"; }
    public CompletableFuture<Void> readyFuture() { return ready; }
    public boolean isReady() { return luckPerms != null && ready.isDone() && !ready.isCompletedExceptionally(); }

    private int weightOfGroup(String group) {
        return byGroup.values().stream().filter(r -> r.group().equalsIgnoreCase(group))
                .mapToInt(RankDefinition::weight).findFirst().orElse(850);
    }

    private Set<String> groupNames(User user) {
        Set<String> names = new HashSet<>();
        names.add(user.getPrimaryGroup().toLowerCase(Locale.ROOT));
        user.getNodes().stream().filter(node -> node instanceof InheritanceNode)
                .map(node -> ((InheritanceNode) node).getGroupName().toLowerCase(Locale.ROOT))
                .forEach(names::add);
        user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(Group::getName).map(n -> n.toLowerCase(Locale.ROOT)).forEach(names::add);
        return names;
    }

    private Set<String> lower(Collection<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private GrantResult failure(String message) {
        return new GrantResult(false, message, null, "", "");
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
