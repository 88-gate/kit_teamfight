package lobby.kit_teamfight.command;

import lobby.kit_teamfight.game.GameManager;
import lobby.kit_teamfight.game.Team;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

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
                    + "/ktf start | stop | reload | status | tickets | initialtickets | flagdrain");
            return true;
        }
        // start/stop/reload/tickets/initialtickets/flagdrain は OP のみ (status は参照系なので全員可)
        String sub = args[0].toLowerCase();
        if ((sub.equals("start") || sub.equals("stop") || sub.equals("reload")
                || sub.equals("tickets") || sub.equals("initialtickets")
                || sub.equals("flagdrain")) && !sender.isOp()) {
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
            default -> sender.sendMessage(ChatColor.GOLD
                    + "/ktf start | stop | reload | status | tickets | initialtickets | flagdrain");
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("start", "stop", "reload", "status",
                    "tickets", "initialtickets", "flagdrain")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tickets")) {
            for (Team t : game.getTeams()) {
                if (t.getId().startsWith(args[1].toLowerCase())) {
                    out.add(t.getId());
                }
            }
        }
        return out;
    }
}
