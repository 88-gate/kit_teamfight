package lobby.kit_teamfight.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * kit の枠組み。具体的な kit はこのインターフェースを実装して KitRegistry に登録するだけで増やせる。
 *
 * 仕様:
 *  - プレイヤーは同時に 1つの kit のみ保持
 *  - 別 kit 購入時は装備を完全置換 (apply 側で全装備を上書きする)
 */
public interface Kit {

    /** 一意識別子。 */
    String getId();

    /** ショップ等に出す表示名。 */
    String getDisplayName();

    /** 購入に必要なポイント。 */
    int getCost();

    /** ショップ GUI に表示するアイコン。 */
    ItemStack getIcon();

    /**
     * プレイヤーに装備一式を適用する。実装側で armor とメインハンドを完全に上書きすること。
     * 呼び出し前に既存装備・ポーション効果はクリアされている前提。
     * パン2スタック(騎兵系は小麦2スタックと馬)は GameManager 側で共通配布する。
     */
    void apply(Player player);

    /**
     * 騎兵系 kit かどうか。true の場合、スポーン時に馬が出現・自動騎乗し、
     * 馬への騎乗が許可される (騎兵系以外は騎乗不可)。小麦2スタックも追加配布される。
     */
    default boolean isCavalry() {
        return false;
    }

    /**
     * 騎兵系の馬に装備させる馬鎧の Material (例: IRON_HORSE_ARMOR)。
     * null なら馬鎧なし。非騎兵では使われない。
     */
    default Material horseArmor() {
        return null;
    }

    /**
     * 騎兵系の馬の最大体力 (HP)。既定は平均くらいの 22。
     * 非騎兵では使われない。
     */
    default double horseMaxHealth() {
        return 22.0;
    }

    /**
     * 騎兵系の馬の移動速度。既定は平均くらいの 0.225。
     * 非騎兵では使われない。
     */
    default double horseMovementSpeed() {
        return 0.225;
    }
}
