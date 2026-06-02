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
 * スナイパー (240pt): 防具なし・近接なし。射撃ダメージ増加III + 無限弓。常時 鈍足II。
 * 鈍足は無限時間で付与する。kit 切替/死亡時の効果除去は GameManager 側で行うため、
 * ここでは効果を付けるだけでよい。
 */
public class SniperKit implements Kit {

    @Override
    public String getId() {
        return "sniper";
    }

    @Override
    public String getDisplayName() {
        return "スナイパー";
    }

    @Override
    public int getCost() {
        return 240;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.SPECTRAL_ARROW);
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        // 防具なし・近接なし

        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.POWER, 3);
        bow.addEnchantment(Enchantment.INFINITY, 1);
        inv.addItem(bow);
        inv.addItem(new ItemStack(Material.ARROW, 1)); // 無限弓用

        // 常時 鈍足II (移動速度低下 II) を無限時間で付与
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 1));
    }
}
