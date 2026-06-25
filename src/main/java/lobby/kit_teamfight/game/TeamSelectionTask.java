package lobby.kit_teamfight.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * チーム希望受付フェーズ (15秒) のカウントダウン。1秒ごとに残り秒数をアクションバーへ表示し、
 * 残り0で {@link GameManager#finalizeTeamSelection()} を呼んで均等化する。
 */
public class TeamSelectionTask extends BukkitRunnable {

    private final GameManager game;
    private int remaining;

    TeamSelectionTask(GameManager game, int seconds) {
        this.game = game;
        this.remaining = seconds;
    }

    @Override
    public void run() {
        if (remaining <= 0) {
            game.finalizeTeamSelection();
            cancel();
            return;
        }
        Component bar = Component.text("チーム希望受付中... 残り " + remaining + " 秒", NamedTextColor.AQUA);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(bar);
        }
        if (remaining <= 3) {
            Bukkit.broadcast(Component.text(remaining + "...", NamedTextColor.RED));
        }
        remaining--;
    }
}
