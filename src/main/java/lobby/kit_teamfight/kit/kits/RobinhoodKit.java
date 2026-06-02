package lobby.kit_teamfight.kit.kits;

import lobby.kit_teamfight.kit.Kit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * ロビンフット / 弓騎兵 (125pt) 【騎兵系】: 革全身 + 石の剣 + 無限弓。
 * スポーン時に革馬鎧の馬が出現し自動騎乗 (馬の生成/騎乗は GameManager が担当)。
 */
public class RobinhoodKit implements Kit {

    @Override
    public String getId() {
        return "robinhood";
    }

    @Override
    public String getDisplayName() {
        return "ロビンフット";
    }

    @Override
    public int getCost() {
        return 125;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.LEATHER_HORSE_ARMOR);
    }

    @Override
    public boolean isCavalry() {
        return true;
    }

    @Override
    public Material horseArmor() {
        return Material.LEATHER_HORSE_ARMOR;
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(new ItemStack(Material.LEATHER_HELMET));
        inv.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        inv.setBoots(new ItemStack(Material.LEATHER_BOOTS));
        inv.setItemInMainHand(new ItemStack(Material.STONE_SWORD));

        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.INFINITY, 1);
        inv.addItem(bow);
        inv.addItem(new ItemStack(Material.ARROW, 1)); // 無限弓用
    }
}
