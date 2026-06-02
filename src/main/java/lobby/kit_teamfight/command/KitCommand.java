package lobby.kit_teamfight.command;

import lobby.kit_teamfight.game.GameManager;
import lobby.kit_teamfight.kit.Kit;
import lobby.kit_teamfight.kit.KitShop;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /kit shop|list|buy <id> — kit ショップを開く / 一覧 / 直接購入。
 */
public class KitCommand implements TabExecutor {

    private final GameManager game;
    private final KitShop shop;

    public KitCommand(GameManager game, KitShop shop) {
        this.game = game;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "プレイヤーのみ実行できます。");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("shop")) {
            shop.open(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list" -> {
                player.sendMessage(ChatColor.GOLD + "=== Kit 一覧 ===");
                player.sendMessage(ChatColor.GRAY + "所持ポイント: " + game.getPlayerData(player).getPoints());
                for (Kit kit : game.getKitRegistry().all()) {
                    player.sendMessage(ChatColor.YELLOW + " - " + kit.getId() + ": "
                            + kit.getDisplayName() + ChatColor.GRAY + " (" + kit.getCost() + " pt)");
                }
            }
            case "buy" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使い方: /kit buy <id>");
                    return true;
                }
                player.sendMessage(game.buyKit(player, args[1]));
            }
            default -> player.sendMessage(ChatColor.GOLD + "/kit shop | list | buy <id>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("shop", "list", "buy")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            for (Kit kit : game.getKitRegistry().all()) {
                if (kit.getId().startsWith(args[1].toLowerCase())) {
                    out.add(kit.getId());
                }
            }
        }
        return out;
    }
}
