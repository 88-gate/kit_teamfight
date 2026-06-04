package lobby.kit_teamfight.kit.kits;

import lobby.kit_teamfight.kit.Kit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * アーチャー (50pt): 革(上半身のみ) + 石の剣 + 無限弓。
 * 無限弓は矢が1本以上必要なので矢を1本配る。
 */
public class ArcherKit implements Kit {

    @Override
    public String getId() {
        return "archer";
    }

    @Override
    public String getDisplayName() {
        return "アーチャー";
    }

    @Override
    public int getCost() {
        return 50;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.BOW);
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(new ItemStack(Material.LEATHER_HELMET));
        inv.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        inv.setItemInMainHand(new ItemStack(Material.STONE_SWORD));

        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.INFINITY, 1);
        inv.addItem(bow);
        inv.addItem(new ItemStack(Material.ARROW, 1)); // 無限弓用
    }
}
