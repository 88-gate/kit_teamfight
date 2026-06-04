package lobby.kit_teamfight.kit.kits;

import lobby.kit_teamfight.kit.Kit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 軍兵 (150pt): 鉄装備フルセット + ダイヤの剣 + 木の斧 + 貫通クロスボウ + 矢3スタック。
 * 重装の近接。剣が主武器、木の斧は盾割り用、貫通クロスボウで遠距離も対応。
 */
public class SoldierKit implements Kit {

    @Override
    public String getId() {
        return "soldier";
    }

    @Override
    public String getDisplayName() {
        return "軍兵";
    }

    @Override
    public int getCost() {
        return 150;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(new ItemStack(Material.IRON_HELMET));
        inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        inv.setBoots(new ItemStack(Material.IRON_BOOTS));
        inv.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        inv.addItem(new ItemStack(Material.WOODEN_AXE)); // 盾割り用

        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        crossbow.addEnchantment(Enchantment.PIERCING, 4); // 貫通IV
        inv.addItem(crossbow);
        inv.addItem(new ItemStack(Material.ARROW, 64));
        inv.addItem(new ItemStack(Material.ARROW, 64));
        inv.addItem(new ItemStack(Material.ARROW, 64)); // 計3スタック(192本)
    }
}
