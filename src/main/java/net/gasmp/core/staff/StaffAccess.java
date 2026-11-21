package net.gasmp.core.staff;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Strict role checks: OP or a wildcard permission is not a substitute for a staff group. */
public final class StaffAccess {
    private final LuckPerms luckPerms;

    public StaffAccess() {
        this.luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
    }

    public boolean hasAny(Player player, String... expectedGroups) {
        return hasAny(player, Set.of(expectedGroups));
    }

    public boolean hasAny(Player player, Collection<String> expectedGroups) {
        if (luckPerms == null || player == null) return false;
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;
        Set<String> expected = expectedGroups.stream()
                .map(group -> group.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (expected.contains(user.getPrimaryGroup().toLowerCase(Locale.ROOT))) return true;
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(Group::getName).map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(expected::contains);
    }

    public boolean isOwnerOrDev(Player player) {
        return hasAny(player, "owner", "dev");
    }

    public boolean isStaff(Player player) {
        return hasAny(player, "owner", "dev", "admin", "manager", "mod", "helper", "builder");
    }
}
