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
 * /ktf start|stop|reload|status — 試合の制御。
 */
public class GameCommand implements TabExecutor {

    private final GameManager game;

    public GameCommand(GameManager game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD
                    + "/ktf start | stop | reload | status | tickets | initialtickets | flagdrain | shuffle | team <team> | setspawn <team> | setteam <player> <team>");
            return true;
        }
        // 参照系の status 以外は OP のみ
        String sub = args[0].toLowerCase();
        if ((sub.equals("start") || sub.equals("stop") || sub.equals("reload")
                || sub.equals("tickets") || sub.equals("initialtickets")
                || sub.equals("flagdrain") || sub.equals("shuffle")
                || sub.equals("setspawn") || sub.equals("setteam")) && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "このコマンドは OP のみ実行できます。");
            return true;
        }
        switch (sub) {
            case "start" -> {
                if (game.startMatch()) {
                    sender.sendMessage(ChatColor.GREEN + "試合を開始しました。");
                } else {
                    sender.sendMessage(ChatColor.RED + "開始できません (既に進行中、またはチーム未定義)。");
                }
            }
            case "stop" -> {
                game.endMatch(null);
                sender.sendMessage(ChatColor.YELLOW + "試合を停止しました。");
            }
            case "reload" -> {
                game.reload();
                sender.sendMessage(ChatColor.GREEN + "config を再読み込みしました。");
            }
            case "status" -> {
                sender.sendMessage(ChatColor.GOLD + "進行中: " + game.isRunning());
                game.getTeams().forEach(t -> sender.sendMessage(
                        " - " + t.getDisplayName() + ChatColor.GRAY + " チケット: " + t.getTickets()));
                sender.sendMessage(ChatColor.GRAY + "旗の数: " + game.getFlagManager().count());
            }
            case "tickets" -> handleTickets(sender, args);
            case "initialtickets" -> handleInitialTickets(sender, args);
            case "flagdrain" -> handleFlagDrain(sender, args);
            case "shuffle" -> handleShuffle(sender);
            case "team" -> handleTeamSelect(sender, args);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "setteam" -> handleSetTeam(sender, args);
            default -> sender.sendMessage(ChatColor.GOLD
                    + "/ktf start | stop | reload | status | tickets | initialtickets | flagdrain | shuffle | team <team> | setspawn <team> | setteam <player> <team>");
        }
        return true;
    }

    /** /ktf tickets <team> <amount> — チームのチケット数を直接設定する (OP)。 */
    private void handleTickets(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.GOLD + "/ktf tickets <team> <amount>");
            sender.sendMessage(ChatColor.GRAY + "チーム: "
                    + String.join(", ", game.getTeams().stream().map(Team::getId).toList()));
            return;
        }
        Team team = game.getTeam(args[1]);
        if (team == null) {
            sender.sendMessage(ChatColor.RED + "チーム '" + args[1] + "' が見つかりません。");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "チケット数は整数で指定してください。");
            return;
        }
        if (amount < 0) {
            amount = 0;
        }
        team.setTickets(amount);
        game.updateSidebar();
        sender.sendMessage(ChatColor.GREEN + team.getDisplayName() + ChatColor.GREEN
                + " のチケットを " + team.getTickets() + " に設定しました。");
    }

    /** /ktf initialtickets <amount> — 試合開始時の初期チケット数を設定 (OP)。次の試合から反映。 */
    private void handleInitialTickets(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "/ktf initialtickets <amount>");
            sender.sendMessage(ChatColor.GRAY + "現在の初期チケット数: " + game.getConfig().ticketInitial);
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "チケット数は整数で指定してください。");
            return;
        }
        int value = game.setInitialTickets(amount);
        sender.sendMessage(ChatColor.GREEN + "初期チケット数を " + value
                + " に設定しました (次の試合開始時から反映)。");
    }

    /** /ktf flagdrain <seconds> — 旗保持で何秒に1回チケットが減るかを設定 (OP)。即時反映。 */
    private void handleFlagDrain(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GOLD + "/ktf flagdrain <seconds>");
            sender.sendMessage(ChatColor.GRAY + "現在: 旗保持で "
                    + game.getConfig().flagDrainIntervalSeconds + "秒に1回チケット減少");
            return;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "秒数は整数で指定してください。");
            return;
        }
        int value = game.setFlagDrainInterval(seconds);
        sender.sendMessage(ChatColor.GREEN + "旗保持によるチケット減少を "
                + value + "秒に1回に設定しました。");
    }

    /** /ktf shuffle — 15秒のチーム希望受付フェーズを開始する (OP)。終了時に均等化される。 */
    private void handleShuffle(CommandSender sender) {
        if (game.isSelectionActive()) {
            sender.sendMessage(ChatColor.YELLOW + "既にチーム希望受付中です。");
            return;
        }
        if (!game.startTeamSelection()) {
            sender.sendMessage(ChatColor.RED + "開始できません。チームが定義されているか config.yml を確認してください。");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "チーム希望受付を開始しました (15秒)。");
    }

    /** /ktf team <id> — 受付中に自分の希望チームを登録する (全員可)。 */
    private void handleTeamSelect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "プレイヤーのみ実行できます。");
            return;
        }
        if (!game.isSelectionActive()) {
            player.sendMessage(ChatColor.RED + "今はチーム希望を受け付けていません。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "使い方: /ktf team <チーム>");
            return;
        }
        // "random" は「おまかせ (未希望)」扱い。ただし同名の実チームがあればそちらを優先。
        if (args[1].equalsIgnoreCase("random") && game.getTeam("random") == null) {
            game.clearTeamPreference(player);
            return;
        }
        if (!game.setTeamPreference(player, args[1])) {
            player.sendMessage(ChatColor.RED + "チーム '" + args[1] + "' は存在しません。");
        }
    }

    /** /ktf setspawn <team> — 実行者の現在地をチームのスポーンに設定する (OP・Player)。 */
    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "プレイヤーのみ実行できます。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "使い方: /ktf setspawn <team>");
            return;
        }
        Team team = game.getTeam(args[1]);
        if (team == null) {
            sender.sendMessage(ChatColor.RED + "チーム '" + args[1] + "' は存在しません。");
            return;
        }
        if (game.setTeamSpawn(args[1], player.getLocation())) {
            var loc = player.getLocation();
            player.sendMessage(ChatColor.GREEN + team.getDisplayName()
                    + ChatColor.GREEN + " のスポーンを現在地に設定しました ("
                    + (int) loc.getX() + "," + (int) loc.getY() + "," + (int) loc.getZ() + ")。");
        } else {
            player.sendMessage(ChatColor.RED + "スポーンの設定に失敗しました。");
        }
    }

    /** /ktf setteam <player> <team> — 指定プレイヤーを指定チームへ移動する (OP)。 */
    private void handleSetTeam(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "使い方: /ktf setteam <プレイヤー> <チーム>");
            return;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "プレイヤー '" + args[1] + "' が見つかりません。");
            return;
        }
        Team team = game.getTeam(args[2]);
        if (team == null) {
            sender.sendMessage(ChatColor.RED + "チーム '" + args[2] + "' は存在しません。");
            return;
        }
        if (!game.joinTeam(target, team.getId())) {
            sender.sendMessage(ChatColor.RED + "移動に失敗しました。");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + target.getName() + " を "
                + team.getDisplayName() + ChatColor.GREEN + " へ移動しました。");
        target.sendMessage(ChatColor.GREEN + "チームが " + team.getDisplayName()
                + ChatColor.GREEN + " に変更されました。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("start", "stop", "reload", "status",
                    "tickets", "initialtickets", "flagdrain", "shuffle", "team", "setspawn", "setteam")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setteam")) {
            // setteam の第1引数はオンラインプレイヤー名
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 2
                && (args[0].equalsIgnoreCase("tickets") || args[0].equalsIgnoreCase("setspawn")
                    || args[0].equalsIgnoreCase("team"))) {
            for (Team t : game.getTeams()) {
                if (t.getId().startsWith(args[1].toLowerCase())) {
                    out.add(t.getId());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setteam")) {
            // setteam の第2引数はチームID
            for (Team t : game.getTeams()) {
                if (t.getId().startsWith(args[2].toLowerCase())) {
                    out.add(t.getId());
                }
            }
        }
        return out;
    }
}
