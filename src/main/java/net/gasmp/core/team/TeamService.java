package net.gasmp.core.team;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 team system. All persistent operations are performed by PlayerDataStore's
 * single-thread SQLite executor; Bukkit and Vault operations are returned to the main thread.
 */
public final class TeamService implements CommandExecutor, TabCompleter {
    private record Invite(String teamId, String teamName, UUID leader, long expiry) { }
    private record AllyInvite(String teamId, String teamName, UUID leader, long expiry) { }

    private static final Map<String, Integer> ROLE_POWER = Map.of(
            "MEMBER", 0, "OFFICER", 1, "COLEADER", 2, "LEADER", 3
    );

    private final GASMPCore plugin;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, AllyInvite> allyInvites = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> bankOperations = ConcurrentHashMap.newKeySet();

    private enum EconomyOutcome { APPLIED, REJECTED, UNCERTAIN }

    public TeamService(GASMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run this command in-game.");
            return true;
        }
        if (!plugin.getConfig().getBoolean("modules.teams", true)) {
            player.sendMessage(color("&cTeams are disabled."));
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player);
            case "leave" -> leave(player);
            case "kick" -> kick(player, args);
            case "disband" -> disband(player);
            case "info" -> info(player);
            case "chat" -> chat(player, args);
            case "promote" -> changeRole(player, args, true);
            case "demote" -> changeRole(player, args, false);
            case "transfer" -> transfer(player, args);
            case "description", "desc" -> description(player, args);
            case "bank" -> bank(player);
            case "deposit" -> bankMove(player, args, true);
            case "withdraw" -> bankMove(player, args, false);
            case "upgrade" -> upgrade(player);
            case "sethome" -> setHome(player, args);
            case "home" -> home(player, args);
            case "homes" -> homes(player);
            case "ally" -> ally(player, args);
            case "allyaccept" -> acceptAlly(player, args);
            case "unally" -> unally(player, args);
            case "allies" -> allies(player);
            default -> help(player);
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (args.length != 2 || !args[1].matches("[A-Za-z0-9_]{3,12}")) {
            player.sendMessage(color("&e/team create <3-12 character name>"));
            return;
        }
        int initialCapacity = Math.max(2, Math.min(100, plugin.getConfig().getInt("teams.initial-capacity", 8)));
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(existing -> {
            if (existing != null) return failed("You are already in a team.");
            return plugin.getDataStore().createTeam(UUID.randomUUID().toString(), args[1], player.getUniqueId(), player.getName(), initialCapacity);
        }).whenComplete((team, error) -> main(() -> {
            if (error != null) player.sendMessage(color("&cCould not create team: " + root(error)));
            else player.sendMessage(color("&aCreated team &f" + team.teamName() + "&a. Starting capacity: &f"
                    + initialCapacity + "&a."));
        }));
    }

    private void invite(Player inviter, String[] args) {
        if (args.length != 2) { inviter.sendMessage(color("&e/team invite <player>")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(inviter)) { inviter.sendMessage(color("&cChoose another online player.")); return; }

        plugin.getDataStore().getTeamByMember(inviter.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamRole(inviter.getUniqueId()).thenCompose(role -> {
                if (!canManage(role)) return failed("Only the leader, co-leader or an officer can invite.");
                return plugin.getDataStore().getTeamStats(team.teamId()).thenCompose(stats ->
                        plugin.getDataStore().getTeamByMember(target.getUniqueId()).thenApply(other -> {
                            if (other != null) throw new IllegalStateException("That player is already in a team.");
                            if (stats == null || stats.members() >= stats.maxMembers()) throw new IllegalStateException("Your team is full. Upgrade its capacity first.");
                            return team;
                        }));
            });
        }).whenComplete((team, error) -> main(() -> {
            if (error != null) { inviter.sendMessage(color("&c" + root(error))); return; }
            invites.put(target.getUniqueId(), new Invite(team.teamId(), team.teamName(), inviter.getUniqueId(), System.currentTimeMillis() + Math.max(10, plugin.getConfig().getLong("teams.invite-expiry-seconds", 60)) * 1000L));
            inviter.sendMessage(color("&aInvited " + target.getName() + " to " + team.teamName() + "."));
            long expirySeconds = Math.max(10, plugin.getConfig().getLong("teams.invite-expiry-seconds", 60));
            target.sendMessage(color("&d&lTEAM INVITE &8» &f" + inviter.getName() + " invited you to &d" + team.teamName()
                    + "&f. Use &e/team accept &fwithin " + expirySeconds + " seconds."));
        }));
    }

    private void accept(Player player) {
        Invite invite = invites.get(player.getUniqueId());
        if (invite == null || invite.expiry() < System.currentTimeMillis()) {
            invites.remove(player.getUniqueId());
            player.sendMessage(color("&cNo active team invitation."));
            return;
        }
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(existing -> {
            if (existing != null) return failed("You are already in a team.");
            if (invite.expiry() < System.currentTimeMillis() || invites.get(player.getUniqueId()) != invite)
                return failed("The team invitation expired.");
            return plugin.getDataStore().addTeamMember(invite.teamId(), player.getUniqueId());
        }).whenComplete((joined, error) -> main(() -> {
            invites.remove(player.getUniqueId());
            if (error != null || !Boolean.TRUE.equals(joined)) {
                player.sendMessage(color("&cInvite expired, the team is full, or the team no longer exists."));
                return;
            }
            player.sendMessage(color("&aYou joined team &f" + invite.teamName() + "&a."));
            Player inviter = Bukkit.getPlayer(invite.leader());
            if (inviter != null) inviter.sendMessage(color("&a" + player.getName() + " joined your team."));
            broadcastTeam(invite.teamId(), color("&a» &f" + player.getName() + " joined the team."));
        }));
    }

    private void leave(Player player) {
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            if (team.leaderUuid().equals(player.getUniqueId())) return failed("The leader must transfer leadership or disband the team.");
            return plugin.getDataStore().removeTeamMember(player.getUniqueId(), team.teamId()).thenApply(removed -> team);
        }).whenComplete((team, error) -> main(() -> {
            if (error != null) player.sendMessage(color("&c" + root(error)));
            else {
                player.sendMessage(color("&aYou left team " + team.teamName() + "."));
                broadcastTeam(team.teamId(), color("&e» &f" + player.getName() + " left the team."));
            }
        }));
    }

    private void kick(Player actor, String[] args) {
        if (args.length != 2) { actor.sendMessage(color("&e/team kick <player>")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(actor)) { actor.sendMessage(color("&cChoose another online team member.")); return; }

        plugin.getDataStore().getTeamByMember(actor.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamByMember(target.getUniqueId()).thenCompose(other -> {
                if (other == null || !other.teamId().equals(team.teamId())) return failed("That player is not on your team.");
                return plugin.getDataStore().getTeamRole(actor.getUniqueId()).thenCompose(actorRole ->
                        plugin.getDataStore().getTeamRole(target.getUniqueId()).thenCompose(targetRole -> {
                            if (!canKick(actorRole, targetRole)) return failed("Your role cannot remove that member.");
                            return plugin.getDataStore().removeTeamMember(target.getUniqueId(), team.teamId()).thenApply(ok -> team);
                        }));
            });
        }).whenComplete((team, error) -> main(() -> {
            if (error != null) { actor.sendMessage(color("&c" + root(error))); return; }
            actor.sendMessage(color("&aRemoved " + target.getName() + " from your team."));
            target.sendMessage(color("&cYou were removed from team " + team.teamName() + "."));
            broadcastTeam(team.teamId(), color("&e» &f" + target.getName() + " was removed from the team."));
        }));
    }

    private void disband(Player leader) {
        plugin.getDataStore().getTeamByMember(leader.getUniqueId()).thenCompose(team -> {
            if (team == null || !team.leaderUuid().equals(leader.getUniqueId())) return failed("Only the leader can disband the team.");
            return plugin.getDataStore().getTeamMembers(team.teamId()).thenCompose(members ->
                    plugin.getDataStore().disbandTeam(team.teamId(), leader.getUniqueId()).thenApply(done -> Map.entry(team, members)));
        }).whenComplete((result, error) -> main(() -> {
            if (error != null) { leader.sendMessage(color("&c" + root(error))); return; }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (result.getValue().stream().anyMatch(member -> member.playerUuid().equals(online.getUniqueId()))) {
                    online.sendMessage(color("&cTeam " + result.getKey().teamName() + " has been disbanded."));
                }
            }
        }));
    }

    private void info(Player player) {
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamMembers(team.teamId()).thenCombine(
                    plugin.getDataStore().getTeamStats(team.teamId()), (members, stats) -> Map.entry(team, Map.entry(members, stats)));
        }).whenComplete((result, error) -> main(() -> {
            if (error != null) { player.sendMessage(color("&c" + root(error))); return; }
            var team = result.getKey();
            var members = result.getValue().getKey();
            var stats = result.getValue().getValue();
            if (stats == null) { player.sendMessage(color("&cTeam data unavailable.")); return; }
            String role = "MEMBER";
            for (PlayerDataStore.TeamMember member : members) if (member.playerUuid().equals(player.getUniqueId())) role = member.role();
            player.sendMessage(color("&d&lTEAM " + team.teamName() + " &8• &fLevel " + stats.level()));
            player.sendMessage(color("&7Role: &f" + role + " &8| &7Members: &f" + stats.members() + "/" + stats.maxMembers()));
            player.sendMessage(color("&7Bank: &6" + money(stats.bank()) + " Coins"));
            player.sendMessage(color("&7Description: &f" + (stats.description().isBlank() ? "No description set." : stats.description())));
            player.sendMessage(color("&7Leader: &f" + Bukkit.getOfflinePlayer(team.leaderUuid()).getName()));
            for (PlayerDataStore.TeamMember member : members) {
                player.sendMessage(color("&8- &f" + member.playerName() + " &7[" + member.role() + "]"));
            }
        }));
    }

    private void chat(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(color("&e/team chat <message>")); return; }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (message.length() > 180) { player.sendMessage(color("&cTeam messages are limited to 180 characters.")); return; }
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamMembers(team.teamId()).thenApply(members -> Map.entry(team, members));
        }).whenComplete((result, error) -> main(() -> {
            if (error != null) { player.sendMessage(color("&c" + root(error))); return; }
            String formatted = color("&d[" + result.getKey().teamName() + "] &f" + player.getName() + "&7: &f" + message);
            for (PlayerDataStore.TeamMember member : result.getValue()) {
                Player online = Bukkit.getPlayer(member.playerUuid());
                if (online != null) online.sendMessage(formatted);
            }
        }));
    }

    private void changeRole(Player actor, String[] args, boolean promote) {
        if (args.length != 2) { actor.sendMessage(color("&e/team " + (promote ? "promote" : "demote") + " <player>")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(actor)) { actor.sendMessage(color("&cChoose another online team member.")); return; }
        plugin.getDataStore().getTeamByMember(actor.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamByMember(target.getUniqueId()).thenCompose(other -> {
                if (other == null || !other.teamId().equals(team.teamId())) return failed("That player is not on your team.");
                return plugin.getDataStore().getTeamRole(actor.getUniqueId()).thenCompose(actorRole ->
                        plugin.getDataStore().getTeamRole(target.getUniqueId()).thenCompose(targetRole -> {
                            if ("LEADER".equals(targetRole) || !canManage(actorRole)) return failed("Your role cannot change that member's rank.");
                            int current = rolePower(targetRole);
                            if (current < 0) return failed("The target's team role is missing or invalid; contact staff.");
                            String newRole;
                            if (promote) {
                                if ("LEADER".equals(actorRole)) newRole = current == 0 ? "OFFICER" : current == 1 ? "COLEADER" : null;
                                else if ("COLEADER".equals(actorRole) && current == 0) newRole = "OFFICER";
                                else newRole = null;
                            } else {
                                if ("LEADER".equals(actorRole)) newRole = current == 2 ? "OFFICER" : current == 1 ? "MEMBER" : null;
                                else if ("COLEADER".equals(actorRole) && current == 1) newRole = "MEMBER";
                                else newRole = null;
                            }
                            if (newRole == null) return failed("That role change is not allowed.");
                            return plugin.getDataStore().setTeamRole(team.teamId(), target.getUniqueId(), newRole)
                                    .thenApply(ok -> Map.entry(team, newRole));
                        }));
            });
        }).whenComplete((result, error) -> main(() -> {
            if (error != null) { actor.sendMessage(color("&c" + root(error))); return; }
            actor.sendMessage(color("&a" + target.getName() + " is now " + result.getValue() + "."));
            target.sendMessage(color("&dYour team role is now &f" + result.getValue() + "&d."));
            broadcastTeam(result.getKey().teamId(), color("&e» &f" + target.getName() + " is now " + result.getValue() + "."));
        }));
    }

    private void transfer(Player leader, String[] args) {
        if (args.length != 2) { leader.sendMessage(color("&e/team transfer <player>")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(leader)) { leader.sendMessage(color("&cChoose another online team member.")); return; }
        plugin.getDataStore().getTeamByMember(leader.getUniqueId()).thenCompose(team -> {
            if (team == null || !team.leaderUuid().equals(leader.getUniqueId())) return failed("Only the current leader can transfer leadership.");
            return plugin.getDataStore().getTeamByMember(target.getUniqueId()).thenCompose(other -> {
                if (other == null || !other.teamId().equals(team.teamId())) return failed("That player is not in your team.");
                return plugin.getDataStore().transferTeamLeader(team.teamId(), leader.getUniqueId(), target.getUniqueId())
                        .thenApply(ok -> team);
            });
        }).whenComplete((team, error) -> main(() -> {
            if (error != null) leader.sendMessage(color("&c" + root(error)));
            else {
                leader.sendMessage(color("&aLeadership transferred to " + target.getName() + "."));
                target.sendMessage(color("&6You are now the leader of " + team.teamName() + "!"));
                broadcastTeam(team.teamId(), color("&e» &fTeam leadership was transferred to " + target.getName() + "."));
            }
        }));
    }

    private void description(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(color("&e/team description <text>")); return; }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (text.length() > Math.max(16, Math.min(200, plugin.getConfig().getInt("teams.description-max-length", 100)))) { player.sendMessage(color("&cTeam descriptions are limited to 100 characters.")); return; }
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamRole(player.getUniqueId()).thenCompose(role -> {
                if (!canManage(role)) return failed("Only leaders, co-leaders or officers can edit the description.");
                return plugin.getDataStore().setTeamDescription(team.teamId(), text).thenApply(ok -> {
                    if (!ok) throw new IllegalStateException("Team no longer exists.");
                    return team;
                });
            });
        }).whenComplete((team, error) -> main(() -> player.sendMessage(color(error == null
                ? "&aTeam description updated." : "&c" + root(error)))));
    }

    private void bank(Player player) {
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamStats(team.teamId()).thenApply(stats -> Map.entry(team, stats));
        }).whenComplete((result, error) -> main(() -> {
            if (error != null) player.sendMessage(color("&c" + root(error)));
            else if (result.getValue() == null) player.sendMessage(color("&cTeam data unavailable."));
            else player.sendMessage(color("&d&lTEAM BANK &8» &6" + money(result.getValue().bank()) + " Coins"));
        }));
    }

    private void bankMove(Player player, String[] args, boolean deposit) {
        if (args.length != 2) {
            player.sendMessage(color("&e/team " + (deposit ? "deposit" : "withdraw") + " <amount>"));
            return;
        }
        double amount;
        try { amount = Double.parseDouble(args[1]); }
        catch (NumberFormatException ex) { player.sendMessage(color("&cEnter a valid amount.")); return; }
        if (!Double.isFinite(amount) || amount <= 0 || amount > 1_000_000_000) {
            player.sendMessage(color("&cAmount must be between 0 and 1,000,000,000."));
            return;
        }
        if (!plugin.getEconomyBridge().isAvailable()) {
            player.sendMessage(color("&cCoins economy is unavailable."));
            return;
        }
        if (!bankOperations.add(player.getUniqueId())) {
            player.sendMessage(color("&cYour previous team-bank transaction is still processing."));
            return;
        }

        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            if (!deposit) return plugin.getDataStore().getTeamRole(player.getUniqueId()).thenApply(role -> {
                if (!plugin.getConfig().getStringList("teams.bank-withdrawal-roles").contains(role)) {
                    throw new IllegalStateException("Your team role cannot withdraw from the team bank.");
                }
                return team;
            });
            return CompletableFuture.completedFuture(team);
        }).whenComplete((team, error) -> main(() -> {
            if (error != null) {
                bankOperations.remove(player.getUniqueId());
                player.sendMessage(color("&c" + root(error)));
                return;
            }

            if (deposit) {
                EconomyOutcome debit = changeEconomyBalanceSafely(player, amount, true);
                if (debit != EconomyOutcome.APPLIED) {
                    bankOperations.remove(player.getUniqueId());
                    player.sendMessage(color(debit == EconomyOutcome.REJECTED
                            ? "&cYour Coins payment was rejected."
                            : "&cYour economy provider returned an uncertain result. Please contact staff before retrying."));
                    return;
                }
                plugin.getDataStore().changeTeamBank(team.teamId(), amount, true).whenComplete((ok, dbError) -> main(() -> {
                    if (dbError != null || !Boolean.TRUE.equals(ok)) {
                        EconomyOutcome refund = changeEconomyBalanceSafely(player, amount, false);
                        if (refund != EconomyOutcome.APPLIED) {
                            plugin.getLogger().severe("TEAM BANK REFUND NEEDS REVIEW: player=" + player.getUniqueId()
                                    + ", amount=" + amount + ", team=" + team.teamId() + ", result=" + refund);
                            player.sendMessage(color("&cDeposit failed and the refund needs staff review. Please contact an administrator."));
                        } else {
                            player.sendMessage(color("&cDeposit failed. Your Coins were refunded."));
                        }
                    } else {
                        player.sendMessage(color("&aDeposited &6" + money(amount) + " Coins&a into the team bank."));
                    }
                    bankOperations.remove(player.getUniqueId());
                }));
                return;
            }

            plugin.getDataStore().changeTeamBank(team.teamId(), amount, false).whenComplete((ok, dbError) -> main(() -> {
                if (dbError != null || !Boolean.TRUE.equals(ok)) {
                    bankOperations.remove(player.getUniqueId());
                    player.sendMessage(color("&cThe team bank does not have enough Coins or the transaction failed."));
                    return;
                }
                EconomyOutcome payout = changeEconomyBalanceSafely(player, amount, false);
                if (payout == EconomyOutcome.APPLIED) {
                    player.sendMessage(color("&aWithdrew &6" + money(amount) + " Coins&a from the team bank."));
                } else if (payout == EconomyOutcome.REJECTED) {
                    plugin.getDataStore().changeTeamBank(team.teamId(), amount, true).whenComplete((refunded, refundError) -> main(() -> {
                        if (refundError != null || !Boolean.TRUE.equals(refunded)) {
                            plugin.getLogger().severe("TEAM BANK LEDGER REFUND FAILED: team=" + team.teamId() + ", player=" + player.getUniqueId() + ", amount=" + amount);
                            player.sendMessage(color("&cPayout failed. The bank refund needs Owner review."));
                        } else {
                            player.sendMessage(color("&cPayout failed; the team bank has been refunded."));
                        }
                        bankOperations.remove(player.getUniqueId());
                    }));
                    return;
                } else {
                    // Never automatically credit the bank back when Vault's resulting balance is ambiguous:
                    // the player may already have received the Coins, creating a duplication exploit.
                    plugin.getLogger().severe("TEAM BANK PAYOUT NEEDS OWNER REVIEW: team=" + team.teamId()
                            + ", player=" + player.getUniqueId() + ", amount=" + amount);
                    player.sendMessage(color("&cThe economy provider returned an uncertain payout. The amount is held for Owner review."));
                }
                bankOperations.remove(player.getUniqueId());
            }));
        }));
    }

    /**
     * Verifies the actual balance delta instead of trusting a nullable/incorrect Vault response.
     * Partial changes are compensated where possible. UNCERTAIN means staff reconciliation is required.
     */
    private EconomyOutcome changeEconomyBalanceSafely(Player player, double amount, boolean withdraw) {
        try {
            double before = plugin.getEconomyBridge().getBalance(player);
            if (!Double.isFinite(before)) return EconomyOutcome.UNCERTAIN;
            EconomyResponse response = withdraw
                    ? plugin.getEconomyBridge().withdraw(player, amount)
                    : plugin.getEconomyBridge().deposit(player, amount);
            double after = plugin.getEconomyBridge().getBalance(player);
            if (!Double.isFinite(after)) return EconomyOutcome.UNCERTAIN;

            double observed = withdraw ? before - after : after - before;
            double tolerance = Math.max(0.000001, Math.abs(amount) * 0.0000001);
            if (Math.abs(observed - amount) <= tolerance) return EconomyOutcome.APPLIED;
            if (Math.abs(observed) <= tolerance) return EconomyOutcome.REJECTED;

            if (observed > 0.0) {
                EconomyResponse compensation = withdraw
                        ? plugin.getEconomyBridge().deposit(player, observed)
                        : plugin.getEconomyBridge().withdraw(player, observed);
                double afterCompensation = plugin.getEconomyBridge().getBalance(player);
                if (compensation != null && compensation.transactionSuccess()
                        && Math.abs(afterCompensation - before) <= tolerance) {
                    return EconomyOutcome.REJECTED;
                }
            }

            plugin.getLogger().severe("Vault balance delta mismatch for " + player.getUniqueId()
                    + ": requested=" + amount + ", observed=" + observed + ", response="
                    + (response == null ? "null" : response.type));
            return EconomyOutcome.UNCERTAIN;
        } catch (Exception exception) {
            plugin.getLogger().severe("Vault operation failed for " + player.getUniqueId() + ": " + exception.getMessage());
            return EconomyOutcome.UNCERTAIN;
        }
    }

    private void upgrade(Player player) {
        double cost = plugin.getConfig().getDouble("teams.capacity-upgrade-cost", 5000.0);
        int slots = plugin.getConfig().getInt("teams.capacity-upgrade-slots", 2);
        int maximum = Math.max(2, Math.min(100, plugin.getConfig().getInt("teams.maximum-capacity", 100)));
        if (!Double.isFinite(cost) || cost <= 0 || slots < 1 || slots > 10) {
            player.sendMessage(color("&cTeam upgrade configuration is invalid."));
            return;
        }
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null || !team.leaderUuid().equals(player.getUniqueId()))
                return failed("Only the leader can buy team upgrades.");
            return plugin.getDataStore().upgradeTeamCapacity(team.teamId(), cost, slots, maximum)
                    .thenApply(success -> Map.entry(team, success));
        }).whenComplete((result, error) -> main(() -> {
            if (error != null) player.sendMessage(color("&c" + root(error)));
            else if (result == null || !result.getValue()) {
                player.sendMessage(color("&cUpgrade unavailable: check the team bank and maximum capacity of " + maximum + " members."));
            } else {
                player.sendMessage(color("&aTeam capacity upgraded by " + slots + " slots for &6" + money(cost)
                        + " Coins&a from the team bank."));
            }
        }));
    }

    private void setHome(Player player, String[] args) {
        String key = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "main";
        if (!key.matches("[a-z0-9_-]{1,16}")) { player.sendMessage(color("&cHome name must be 1-16 letters, numbers, _ or -.")); return; }
        Location loc = player.getLocation().clone();
        String worldName = loc.getWorld().getName();
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        float yaw = loc.getYaw(), pitch = loc.getPitch();
        int maximumHomes = Math.max(1, Math.min(10, plugin.getConfig().getInt("teams.max-homes-per-team", 3)));
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamRole(player.getUniqueId()).thenCompose(role -> {
                if (!plugin.getConfig().getStringList("teams.home-set-roles").contains(role))
                    return failed("Your team role cannot set team homes.");
                return plugin.getDataStore().setTeamHomeLimited(team.teamId(), key, worldName, x, y, z,
                        yaw, pitch, maximumHomes).thenApply(ok -> {
                    if (!ok) throw new IllegalStateException("Your team reached its " + maximumHomes + " home limit.");
                    return key;
                });
            });
        }).whenComplete((saved, error) -> main(() -> player.sendMessage(color(error == null && saved != null
                ? "&aTeam home &f" + saved + " &asaved." : "&c" + (error == null ? "Could not save team home." : root(error))))));
    }

    private void home(Player player, String[] args) {
        String key = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "main";
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamHome(team.teamId(), key);
        }).whenComplete((home, error) -> main(() -> {
            if (error != null || home == null) { player.sendMessage(color(error == null ? "&cThat team home does not exist." : "&c" + root(error))); return; }
            World world = Bukkit.getWorld(home.world());
            if (world == null) { player.sendMessage(color("&cThe team home world is not loaded.")); return; }
            Location destination = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
            world.getChunkAtAsync(destination, true).whenComplete((chunk, chunkError) -> main(() -> {
                if (chunkError != null || chunk == null) {
                    player.sendMessage(color("&cCould not load the team-home chunk."));
                    return;
                }
                if (plugin.getCombatService() != null && plugin.getCombatService().isTagged(player.getUniqueId())) {
                    player.sendMessage(color("&cYou cannot use a team home while combat-tagged."));
                    return;
                }
                player.teleportAsync(destination).whenComplete((success, teleportError) -> main(() -> {
                    if (teleportError != null || !Boolean.TRUE.equals(success)) player.sendMessage(color("&cTeam home teleport failed."));
                    else player.sendMessage(color("&aTeleported to team home &f" + key + "&a."));
                }));
            }));
        }));
    }

    private void homes(Player player) {
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamHomes(team.teamId());
        }).whenComplete((homes, error) -> main(() -> {
            if (error != null) { player.sendMessage(color("&c" + root(error))); return; }
            player.sendMessage(color("&d&lTEAM HOMES &8» &f" + homes.size() + "/"
                    + Math.max(1, Math.min(10, plugin.getConfig().getInt("teams.max-homes-per-team", 3)))));
            if (homes.isEmpty()) { player.sendMessage(color("&7No team homes have been set.")); return; }
            for (PlayerDataStore.TeamHomeEntry home : homes) {
                player.sendMessage(color("&8- &d" + home.key() + " &7— &f" + home.world()
                        + " &8(" + (int) home.x() + ", " + (int) home.y() + ", " + (int) home.z() + ")"));
            }
            player.sendMessage(color("&7Use &e/team home <name> &7to teleport."));
        }));
    }

    private void ally(Player player, String[] args) {
        if (args.length != 2) { player.sendMessage(color("&e/team ally <team-name>")); return; }
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null || !team.leaderUuid().equals(player.getUniqueId())) return failed("Only the leader can request an alliance.");
            return plugin.getDataStore().getTeamByName(args[1]).thenCompose(target -> {
                if (target == null) return failed("That team does not exist.");
                if (target.teamId().equals(team.teamId())) return failed("You cannot ally with your own team.");
                int maxAlliances = Math.max(0, Math.min(20, plugin.getConfig().getInt("teams.max-alliances-per-team", 5)));
                return plugin.getDataStore().getTeamAllies(team.teamId()).thenCompose(existing -> {
                    if (existing.stream().anyMatch(name -> name.equalsIgnoreCase(target.teamName())))
                        return failed("You are already allied with that team.");
                    if (existing.size() >= maxAlliances) return failed("Your team reached its alliance limit of " + maxAlliances + ".");
                    return plugin.getDataStore().getTeamAllies(target.teamId()).thenApply(targetAllies -> {
                        if (targetAllies.size() >= maxAlliances) throw new IllegalStateException("That team reached its alliance limit.");
                        return Map.entry(team, target);
                    });
                });
            });
        }).whenComplete((pair, error) -> main(() -> {
            if (error != null) { player.sendMessage(color("&c" + root(error))); return; }
            Player targetLeader = Bukkit.getPlayer(pair.getValue().leaderUuid());
            if (targetLeader == null) { player.sendMessage(color("&cThe other team's leader must be online to receive the request.")); return; }
            allyInvites.put(targetLeader.getUniqueId(), new AllyInvite(pair.getKey().teamId(), pair.getKey().teamName(), player.getUniqueId(), System.currentTimeMillis() + Math.max(10, plugin.getConfig().getLong("teams.ally-request-expiry-seconds", 60)) * 1000L));
            player.sendMessage(color("&aAlliance request sent to " + pair.getValue().teamName() + "."));
            targetLeader.sendMessage(color("&d&lALLIANCE REQUEST &8» &f" + pair.getKey().teamName() + " requested an alliance. Use &e/team allyaccept " + pair.getKey().teamName()));
        }));
    }

    private void acceptAlly(Player player, String[] args) {
        AllyInvite invite = allyInvites.get(player.getUniqueId());
        if (invite == null || invite.expiry() < System.currentTimeMillis()) { allyInvites.remove(player.getUniqueId()); player.sendMessage(color("&cNo active alliance request.")); return; }
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null || !team.leaderUuid().equals(player.getUniqueId())) return failed("Only the team leader can accept an alliance.");
            if (invite.expiry() < System.currentTimeMillis() || allyInvites.get(player.getUniqueId()) != invite)
                return failed("The alliance request expired.");
            int maxAlliances = Math.max(0, Math.min(20, plugin.getConfig().getInt("teams.max-alliances-per-team", 5)));
            return plugin.getDataStore().getTeamAllianceCount(team.teamId()).thenCompose(ownCount ->
                    plugin.getDataStore().getTeamAllianceCount(invite.teamId()).thenCompose(otherCount -> {
                        if (ownCount >= maxAlliances || otherCount >= maxAlliances)
                            return failed("One of the teams has reached its alliance limit.");
                        return plugin.getDataStore().addTeamAllianceLimited(team.teamId(), invite.teamId(), maxAlliances)
                                .thenApply(ok -> Map.entry(team, ok));
                    }));
        }).whenComplete((result, error) -> main(() -> {
            allyInvites.remove(player.getUniqueId());
            if (error != null || result == null || !result.getValue()) { player.sendMessage(color("&cAlliance could not be created; check for an existing alliance.")); return; }
            player.sendMessage(color("&aYour team is now allied with " + invite.teamName() + "."));
            Player requester = Bukkit.getPlayer(invite.leader());
            if (requester != null) requester.sendMessage(color("&aAlliance accepted by " + result.getKey().teamName() + "."));
        }));
    }

    private void unally(Player player, String[] args) {
        if (args.length != 2) { player.sendMessage(color("&e/team unally <team-name>")); return; }
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null || !team.leaderUuid().equals(player.getUniqueId())) return failed("Only the leader can end an alliance.");
            return plugin.getDataStore().getTeamByName(args[1]).thenCompose(other -> {
                if (other == null) return failed("That team does not exist.");
                return plugin.getDataStore().removeTeamAlliance(team.teamId(), other.teamId()).thenApply(ok -> ok);
            });
        }).whenComplete((removed, error) -> main(() -> player.sendMessage(color(error == null && Boolean.TRUE.equals(removed)
                ? "&aAlliance ended." : "&cNo alliance found or request failed."))));
    }

    private void allies(Player player) {
        plugin.getDataStore().getTeamByMember(player.getUniqueId()).thenCompose(team -> {
            if (team == null) return failed("You are not in a team.");
            return plugin.getDataStore().getTeamAllies(team.teamId());
        }).whenComplete((allies, error) -> main(() -> {
            if (error != null) { player.sendMessage(color("&c" + root(error))); return; }
            player.sendMessage(color("&d&lTEAM ALLIES"));
            if (allies.isEmpty()) player.sendMessage(color("&7No active alliances."));
            else allies.forEach(name -> player.sendMessage(color("&8- &f" + name)));
        }));
    }

    private void broadcastTeam(String teamId, String message) {
        plugin.getDataStore().getTeamMembers(teamId).thenAccept(members -> main(() -> {
            for (PlayerDataStore.TeamMember member : members) {
                Player online = Bukkit.getPlayer(member.playerUuid());
                if (online != null) online.sendMessage(message);
            }
        }));
    }

    private boolean canManage(String role) {
        return "LEADER".equals(role) || "COLEADER".equals(role) || "OFFICER".equals(role);
    }

    private int rolePower(String role) {
        if (role == null) return -1;
        return ROLE_POWER.getOrDefault(role.toUpperCase(Locale.ROOT), -1);
    }

    private boolean canKick(String actorRole, String targetRole) {
        return rolePower(actorRole) > rolePower(targetRole) && canManage(actorRole);
    }

    private <T> CompletableFuture<T> failed(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }

    private void main(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    private String money(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private String root(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void help(Player player) {
        player.sendMessage(color("&d&lGASMP TEAMS"));
        player.sendMessage(color("&e/team create <name> &7| &einvite <player> &7| &eaccept"));
        player.sendMessage(color("&e/team info &7| &eleave &7| &ekick <player> &7| &edisband"));
        player.sendMessage(color("&e/team promote/demote <player> &7| &etransfer <player>"));
        player.sendMessage(color("&e/team description <text> &7| &ebank &7| &edeposit/withdraw <amount>"));
        player.sendMessage(color("&e/team sethome [name] &7| &ehome [name] &7| &ehomes &7| &eupgrade"));
        player.sendMessage(color("&e/team ally <team> &7| &eallyaccept &7| &eunally <team> &7| &eallies"));
        player.sendMessage(color("&e/team chat <message>"));
        player.sendMessage(color("&7Roles: Leader > Co-Leader > Officer > Member."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("create", "invite", "accept", "leave", "kick", "disband", "info", "chat",
                "promote", "demote", "transfer", "description", "bank", "deposit", "withdraw", "upgrade",
                "sethome", "home", "homes", "ally", "allyaccept", "unally", "allies").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && List.of("invite", "kick", "promote", "demote", "transfer").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
