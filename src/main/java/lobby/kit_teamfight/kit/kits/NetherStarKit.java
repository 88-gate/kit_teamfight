package lobby.kit_teamfight.kit.kits;

import lobby.kit_teamfight.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * スコーピオン (50pt): 初期装備 (革装備一式 + 石の剣) にネザースターを手持ちで追加した役職。
 * ネザースターの機能 (能力) は別プラグイン側で定義される。
 */
public class NetherStarKit implements Kit {

    @Override
    public String getId() {
        return "netherstar";
    }

    @Override
    public String getDisplayName() {
        return "スコーピオン";
    }

    @Override
    public int getCost() {
        return 50;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.NETHER_STAR);
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        // 初期装備と同じ一式
        inv.setHelmet(new ItemStack(Material.LEATHER_HELMET));
        inv.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        inv.setBoots(new ItemStack(Material.LEATHER_BOOTS));
        inv.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
        // ネザースターを手持ちで追加
        inv.addItem(new ItemStack(Material.NETHER_STAR));
    }
}
