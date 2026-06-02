package lobby.kit_teamfight.kit.kits;

import lobby.kit_teamfight.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 軽散兵 (100pt): 革(上半身のみ) + 石斧 + 盾(オフハンド)。
 */
public class SkirmisherKit implements Kit {

    @Override
    public String getId() {
        return "skirmisher";
    }

    @Override
    public String getDisplayName() {
        return "軽散兵";
    }

    @Override
    public int getCost() {
        return 100;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.STONE_AXE);
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(new ItemStack(Material.LEATHER_HELMET));
        inv.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        inv.setItemInMainHand(new ItemStack(Material.STONE_AXE));
        inv.setItemInOffHand(new ItemStack(Material.SHIELD));
    }
}
