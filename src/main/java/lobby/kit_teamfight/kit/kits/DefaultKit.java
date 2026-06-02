package lobby.kit_teamfight.kit.kits;

import lobby.kit_teamfight.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 初期装備 (デフォルト kit): 革装備一式 + 石の剣。
 * kit 未購入のとき・死亡からの復帰時に適用される。
 *
 * ショップ/一覧には出さないため KitRegistry には登録しない。
 * GameManager が直接保持して使う。パン2スタックは他 kit と同様 GameManager 側で共通配布する。
 */
public class DefaultKit implements Kit {

    @Override
    public String getId() {
        return "default";
    }

    @Override
    public String getDisplayName() {
        return "初期装備";
    }

    @Override
    public int getCost() {
        return 0;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Material.LEATHER_CHESTPLATE);
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(new ItemStack(Material.LEATHER_HELMET));
        inv.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
        inv.setBoots(new ItemStack(Material.LEATHER_BOOTS));
        inv.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
    }
}
