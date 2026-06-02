package lobby.kit_teamfight.game;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * 旗の占領範囲をパーティクルで描画する周期タスク。
 * 見た目をなめらかにするため 0.5秒 (10 tick) 間隔で動かす。
 */
public class FlagParticleTask extends BukkitRunnable {

    private final GameManager game;

    public FlagParticleTask(GameManager game) {
        this.game = game;
    }

    @Override
    public void run() {
        game.tickFlagParticles();
    }
}
