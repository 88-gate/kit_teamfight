package lobby.kit_teamfight.flag;

import lobby.kit_teamfight.game.GameManager;
import lobby.kit_teamfight.game.Team;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.Collection;

/**
 * 旗の見た目を管理する。
 *  - 旗ブロック (コンクリート) を所有チームの色で設置/更新
 *  - 旗の上にチーム名を出すホログラム (TextDisplay) を表示
 *
 * ホログラムは setPersistent(false) でワールドに保存させないため、再起動で残らない
 * (再読込時に renderAll で貼り直す)。
 */
public class FlagVisualizer {

    private final GameManager game;

    public FlagVisualizer(GameManager game) {
        this.game = game;
    }

    /** 旗ブロックを (再)設置し、ホログラムが無ければ生成する。 */
    public void render(Flag flag) {
        Location loc = flag.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        Block block = loc.getBlock();
        block.setType(materialFor(flag.getOwnerTeamId()));
        ensureHologram(flag);
    }

    public void renderAll(Collection<Flag> flags) {
        for (Flag flag : flags) {
            render(flag);
        }
    }

    /** コマンド削除/破壊時。ブロックを空気に戻しホログラムを消す。 */
    public void remove(Flag flag) {
        removeHologram(flag);
        Location loc = flag.getLocation();
        if (loc != null && loc.getWorld() != null) {
            loc.getBlock().setType(Material.AIR);
        }
    }

    /** 無効化時にホログラムだけ消す (ブロックは残してよい)。 */
    public void removeAllHolograms(Collection<Flag> flags) {
        for (Flag flag : flags) {
            removeHologram(flag);
        }
    }

    // ---- 占領範囲パーティクル -----------------------------------------------

    /**
     * 旗の占領範囲を所有チーム色のリング状パーティクルで描画する。
     * 周期タスクから呼ばれる想定 (見た目用なので試合中以外でも表示される)。
     */
    public void showCaptureRing(Flag flag, double radius) {
        Location loc = flag.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        World world = loc.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(colorFor(flag.getOwnerTeamId()), 1.3f);
        Location center = loc.clone().add(0.5, 0.1, 0.5);
        int points = Math.max(16, (int) (radius * 6)); // 半径に応じて点数を増やす
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            world.spawnParticle(Particle.DUST, x, center.getY(), z, 1, 0, 0, 0, 0, dust);
        }
    }

    // ---- ホログラム ---------------------------------------------------------

    private void ensureHologram(Flag flag) {
        if (flag.getHologramId() != null) {
            Entity existing = Bukkit.getEntity(flag.getHologramId());
            if (existing != null && !existing.isDead()) {
                return; // 既に存在
            }
        }
        Location loc = flag.getLocation();
        World world = loc.getWorld();
        Location holo = loc.clone().add(0.5, 1.4, 0.5);
        TextDisplay display = world.spawn(holo, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER); // 常にプレイヤーへ正対
            d.setPersistent(false);                   // ワールドに保存しない
            d.setSeeThrough(true);
            d.text(Component.text(flag.getId()));
        });
        flag.setHologramId(display.getUniqueId());
    }

    private void removeHologram(Flag flag) {
        if (flag.getHologramId() == null) {
            return;
        }
        Entity e = Bukkit.getEntity(flag.getHologramId());
        if (e != null) {
            e.remove();
        }
        flag.setHologramId(null);
    }

    // ---- 色マッピング -------------------------------------------------------

    private Material materialFor(String ownerTeamId) {
        if (ownerTeamId == null) {
            return Material.WHITE_CONCRETE; // 中立
        }
        Team team = game.getTeam(ownerTeamId);
        if (team == null) {
            return Material.WHITE_CONCRETE;
        }
        DyeColor dye = toDye(team.getColor());
        try {
            return Material.valueOf(dye.name() + "_CONCRETE");
        } catch (IllegalArgumentException ex) {
            return Material.WHITE_CONCRETE;
        }
    }

    /** パーティクル用に所有チーム色を org.bukkit.Color へ変換する。中立は白。 */
    private Color colorFor(String ownerTeamId) {
        if (ownerTeamId == null) {
            return Color.WHITE;
        }
        Team team = game.getTeam(ownerTeamId);
        if (team == null) {
            return Color.WHITE;
        }
        DyeColor dye = toDye(team.getColor());
        return dye.getColor(); // DyeColor は org.bukkit.Color を持つ
    }

    private DyeColor toDye(ChatColor color) {
        return switch (color) {
            case BLACK -> DyeColor.BLACK;
            case DARK_BLUE -> DyeColor.BLUE;
            case DARK_GREEN -> DyeColor.GREEN;
            case DARK_AQUA -> DyeColor.CYAN;
            case DARK_RED -> DyeColor.RED;
            case DARK_PURPLE -> DyeColor.PURPLE;
            case GOLD -> DyeColor.ORANGE;
            case GRAY -> DyeColor.LIGHT_GRAY;
            case DARK_GRAY -> DyeColor.GRAY;
            case BLUE -> DyeColor.BLUE;
            case GREEN -> DyeColor.LIME;
            case AQUA -> DyeColor.LIGHT_BLUE;
            case RED -> DyeColor.RED;
            case LIGHT_PURPLE -> DyeColor.MAGENTA;
            case YELLOW -> DyeColor.YELLOW;
            default -> DyeColor.WHITE;
        };
    }
}
