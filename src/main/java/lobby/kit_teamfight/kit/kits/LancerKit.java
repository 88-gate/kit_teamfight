package lobby.kit_teamfight.kit.kits;

import lobby.kit_teamfight.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 槍騎兵 (180pt) 【騎兵系】: チェーン全身 + 鉄の槍。
 * スポーン時に鉄馬鎧の馬が出現し自動騎乗 (馬の生成/騎乗は GameManager が担当)。
 */
public class LancerKit implements Kit {

    @Override
    public String getId() {
        return "lancer";
    }

    @Override
    public String getDisplayName() {
        return "槍騎兵";
    }

    @Override
    public int getCost() {
        return 180;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.IRON_SPEAR);
    }

    @Override
    public boolean isCavalry() {
        return true;
    }

    @Override
    public Material horseArmor() {
        return Material.IRON_HORSE_ARMOR;
    }

    @Override
    public double horseMaxHealth() {
        return 33.0; // 通常(22)の3/2 (44から弱体化)
    }

    @Override
    public double horseMovementSpeed() {
        return 0.3375; // 通常(0.225)の3/2
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        inv.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        inv.setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
        inv.setItemInMainHand(new ItemStack(Material.IRON_SPEAR));
    }
}
