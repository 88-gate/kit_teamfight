package lobby.kit_teamfight.command;

import lobby.kit_teamfight.game.GameManager;
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
            sender.sendMessage(ChatColor.GOLD + "/ktf start | stop | reload | status");
            return true;
        }
        // start/stop/reload は OP のみ (status は参照系なので全員可)
        String sub = args[0].toLowerCase();
        if ((sub.equals("start") || sub.equals("stop") || sub.equals("reload")) && !sender.isOp()) {
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
            default -> sender.sendMessage(ChatColor.GOLD + "/ktf start | stop | reload | status");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("start", "stop", "reload", "status")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
