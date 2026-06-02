package lobby.kit_teamfight.kit;

import lobby.kit_teamfight.game.GameManager;
import lobby.kit_teamfight.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * kit 購入用のチェスト GUI。/kit shop で開く。
 * 自前で InventoryClickEvent を処理する Listener でもある。
 */
public class KitShop implements Listener {

    public static final String TITLE = ChatColor.DARK_AQUA + "Kit Shop";

    private final GameManager game;

    public KitShop(GameManager game) {
        this.game = game;
    }

    public void open(Player player) {
        int size = Math.max(9, ((game.getKitRegistry().all().size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(player, size, TITLE);
        PlayerData data = game.getPlayerData(player);

        for (Kit kit : game.getKitRegistry().all()) {
            ItemStack icon = kit.getIcon().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                boolean affordable = data.getPoints() >= kit.getCost();
                boolean equipped = kit.getId().equalsIgnoreCase(data.getCurrentKitId());
                meta.setDisplayName((equipped ? ChatColor.GREEN + "✔ " : ChatColor.YELLOW.toString())
                        + kit.getDisplayName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "コスト: " + kit.getCost() + " pt");
                lore.add((affordable ? ChatColor.GREEN + "クリックで購入" : ChatColor.RED + "ポイント不足"));
                if (equipped) {
                    lore.add(ChatColor.AQUA + "装備中");
                }
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inv.addItem(icon);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true); // ショップ内のアイテム移動を禁止
        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        // クリックされた kit を表示名から特定する
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String stripped = ChatColor.stripColor(meta.getDisplayName()).replace("✔ ", "").trim();
        for (Kit kit : game.getKitRegistry().all()) {
            if (kit.getDisplayName().equals(stripped)) {
                String result = game.buyKit(player, kit.getId());
                player.sendMessage(result);
                player.closeInventory();
                return;
            }
        }
    }
}
