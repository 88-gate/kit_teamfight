package lobby.kit_teamfight.command;

import lobby.kit_teamfight.game.GameManager;
import lobby.kit_teamfight.game.Team;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /team join <id>|auto|list — チームへの参加・一覧表示。
 */
public class TeamCommand implements TabExecutor {

    private final GameManager game;

    public TeamCommand(GameManager game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.GOLD + "=== チーム一覧 ===");
            for (Team team : game.getTeams()) {
                sender.sendMessage(" - " + team.getDisplayName() + ChatColor.GRAY
                        + " チケット: " + team.getTickets());
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "プレイヤーのみ実行できます。");
            return true;
        }
        if (args[0].equalsIgnoreCase("setspawn")) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "このコマンドは OP のみ実行できます。");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "使い方: /team setspawn <teamId>");
                return true;
            }
            if (game.getTeam(args[1]) == null) {
                sender.sendMessage(ChatColor.RED + "チーム '" + args[1] + "' は存在しません。");
                return true;
            }
            if (game.setTeamSpawn(args[1], player.getLocation())) {
                var loc = player.getLocation();
                player.sendMessage(ChatColor.GREEN + game.getTeam(args[1]).getDisplayName()
                        + ChatColor.GREEN + " のスポーンを現在地に設定しました ("
                        + (int) loc.getX() + "," + (int) loc.getY() + "," + (int) loc.getZ() + ")。");
            } else {
                player.sendMessage(ChatColor.RED + "スポーンの設定に失敗しました。");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("join")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "使い方: /team join <id|auto>");
                return true;
            }
            if (args[1].equalsIgnoreCase("auto")) {
                Team team = game.autoAssign(player);
                if (team == null) {
                    player.sendMessage(ChatColor.RED + "参加できるチームがありません。");
                } else {
                    player.sendMessage(ChatColor.GREEN + team.getDisplayName() + ChatColor.GREEN + " に配属されました。");
                }
                return true;
            }
            if (game.joinTeam(player, args[1])) {
                player.sendMessage(ChatColor.GREEN + game.getTeam(args[1]).getDisplayName()
                        + ChatColor.GREEN + " に参加しました。");
            } else {
                player.sendMessage(ChatColor.RED + "チーム '" + args[1] + "' は存在しません。");
            }
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "/team join <id|auto> | list | setspawn <id> [OP]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("join", "list", "setspawn")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            out.add("auto");
            for (Team team : game.getTeams()) {
                out.add(team.getId());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setspawn")) {
            for (Team team : game.getTeams()) {
                out.add(team.getId());
            }
        }
        return out;
    }
}
