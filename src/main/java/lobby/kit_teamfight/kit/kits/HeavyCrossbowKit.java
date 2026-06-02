package lobby.kit_teamfight.kit.kits;

import lobby.kit_teamfight.kit.Kit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 重弩兵 (150pt): 防具なし。貫通(Piercing)クロスボウ×3 + 矢4スタック + 石の剣。常時 鈍足I。
 * クロスボウは装填に時間がかかるため、3丁を持ち替えて連射するバースト型。
 * 鈍足の解除は GameManager 側 (kit切替/死亡時の効果除去) で行うため、ここでは付けるだけ。
 */
public class HeavyCrossbowKit implements Kit {

    @Override
    public String getId() {
        return "arbalest";
    }

    @Override
    public String getDisplayName() {
        return "重弩兵";
    }

    @Override
    public int getCost() {
        return 150;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.CROSSBOW);
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        // 防具なし

        for (int i = 0; i < 3; i++) {
            ItemStack crossbow = new ItemStack(Material.CROSSBOW);
            crossbow.addEnchantment(Enchantment.PIERCING, 4); // 貫通IV
            inv.addItem(crossbow);
        }
        for (int i = 0; i < 4; i++) {
            inv.addItem(new ItemStack(Material.ARROW, 64)); // 矢4スタック
        }

        inv.addItem(new ItemStack(Material.STONE_SWORD)); // 近接用

        // 常時 鈍足I を無限時間で付与
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 0));
    }
}
