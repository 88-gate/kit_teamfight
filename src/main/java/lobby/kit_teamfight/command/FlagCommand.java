package lobby.kit_teamfight.command;

import lobby.kit_teamfight.flag.Flag;
import lobby.kit_teamfight.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /flag create|remove|list|setowner — 旗をコマンドで動的に設置/撤去する。
 * 旗は 0個でも試合は成立するため、設置は任意。
 */
public class FlagCommand implements TabExecutor {

    private final GameManager game;

    public FlagCommand(GameManager game) {
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        // 旗の設置/撤去/所有者変更は OP のみ (list は参照系なので全員可)
        if ((sub.equals("create") || sub.equals("remove") || sub.equals("setowner")) && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "このコマンドは OP のみ実行できます。");
            return true;
        }
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "setowner" -> handleSetOwner(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "プレイヤーのみ実行できます。");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "使い方: /flag create <flagId> [ownerTeamId]");
            return;
        }
        String id = args[1];
        if (game.getFlagManager().exists(id)) {
            sender.sendMessage(ChatColor.RED + "旗 '" + id + "' は既に存在します。");
            return;
        }
        String owner = null;
        if (args.length >= 3 && !args[2].equalsIgnoreCase("none")) {
            if (game.getTeam(args[2]) == null) {
                sender.sendMessage(ChatColor.RED + "チーム '" + args[2] + "' は存在しません。");
                return;
            }
            owner = game.getTeam(args[2]).getId();
        }
        // 旗を設置する場所を「見ている」ことを必須にする。見ているブロックの上に旗を立てる。
        org.bukkit.block.Block target = player.getTargetBlockExact(5);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "旗を設置するブロックを見てから実行してください (最大5ブロック先)。");
            return;
        }
        org.bukkit.Location blockLoc = target.getRelative(org.bukkit.block.BlockFace.UP).getLocation();
        game.createFlag(id, blockLoc, owner);
        sender.sendMessage(ChatColor.GREEN + "旗 '" + id + "' を設置しました"
                + (owner != null ? " (所有: " + owner + ")" : " (中立)") + "。");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "使い方: /flag remove <flagId>");
            return;
        }
        Flag removed = game.removeFlag(args[1]);
        if (removed == null) {
            sender.sendMessage(ChatColor.RED + "旗 '" + args[1] + "' は存在しません。");
        } else {
            sender.sendMessage(ChatColor.GREEN + "旗 '" + args[1] + "' を撤去しました。");
        }
    }

    private void handleList(CommandSender sender) {
        if (game.getFlagManager().count() == 0) {
            sender.sendMessage(ChatColor.GRAY + "設置済みの旗はありません (旗なしでも試合は成立します)。");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== 旗一覧 (" + game.getFlagManager().count() + ") ===");
        for (Flag flag : game.getFlagManager().all()) {
            var loc = flag.getLocation();
            String owner = flag.getOwnerTeamId() == null ? "中立" : flag.getOwnerTeamId();
            sender.sendMessage(ChatColor.YELLOW + " - " + flag.getId() + ChatColor.GRAY
                    + " @ " + (loc.getWorld() != null ? loc.getWorld().getName() : "?")
                    + " (" + (int) loc.getX() + "," + (int) loc.getY() + "," + (int) loc.getZ() + ")"
                    + " 所有: " + owner);
        }
    }

    private void handleSetOwner(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "使い方: /flag setowner <flagId> <teamId|none>");
            return;
        }
        Flag flag = game.getFlagManager().get(args[1]);
        if (flag == null) {
            sender.sendMessage(ChatColor.RED + "旗 '" + args[1] + "' は存在しません。");
            return;
        }
        String newOwner;
        if (args[2].equalsIgnoreCase("none")) {
            newOwner = null;
        } else if (game.getTeam(args[2]) == null) {
            sender.sendMessage(ChatColor.RED + "チーム '" + args[2] + "' は存在しません。");
            return;
        } else {
            newOwner = game.getTeam(args[2]).getId();
        }
        game.setFlagOwner(flag, newOwner); // 所有者変更 + ブロック色更新 + 保存
        sender.sendMessage(ChatColor.GREEN + "旗 '" + args[1] + "' の所有者を設定しました。");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "/flag create <id> [team] | remove <id> | list | setowner <id> <team|none>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "remove", "list", "setowner"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("setowner"))) {
            List<String> ids = new ArrayList<>();
            game.getFlagManager().all().forEach(f -> ids.add(f.getId()));
            return filter(ids, args[1]);
        }
        if ((args.length == 3 && args[0].equalsIgnoreCase("setowner"))
                || (args.length == 3 && args[0].equalsIgnoreCase("create"))) {
            List<String> teamIds = new ArrayList<>();
            teamIds.add("none");
            game.getTeams().forEach(t -> teamIds.add(t.getId()));
            return filter(teamIds, args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(prefix.toLowerCase())) {
                out.add(o);
            }
        }
        return out;
    }
}
