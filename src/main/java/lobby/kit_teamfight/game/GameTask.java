package lobby.kit_teamfight.game;

import lobby.kit_teamfight.flag.Flag;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

/**
 * 1秒ごとに動く試合ループ。
 *  - 時間経過ポイント加算
 *  - 旗のキャプチャ進捗評価
 *  - 旗保持によるチケット減少 (一定間隔)
 */
public class GameTask extends BukkitRunnable {

    private final GameManager game;
    private long seconds = 0;

    public GameTask(GameManager game) {
        this.game = game;
    }

    @Override
    public void run() {
        // サイドバーは試合中でなくても更新する (チケット残量を常時表示)
        game.updateSidebar();
        if (!game.isRunning()) {
            return;
        }
        seconds++;
        GameConfig cfg = game.getConfig();

        // ポイント加算
        if (seconds % cfg.pointTickRateSeconds == 0) {
            game.tickPoints();
        }

        // 旗キャプチャ評価 (毎秒)
        for (Flag flag : game.getFlagManager().all()) {
            evaluateCapture(flag, cfg);
        }

        // 旗保持によるチケット減少 (一定間隔)
        if (seconds % cfg.flagDrainIntervalSeconds == 0) {
            game.tickFlagDrain();
        }
    }

    /**
     * 旗周囲のプレイヤーを調べてキャプチャ進捗を進める。
     *  - 範囲内に現所有者以外のチームが1つだけ、かつ所有者チームがいない -> 進捗+1
     *  - 所有者チームが範囲内にいる、または複数の敵チームが混在 -> 進捗停止 (巻き戻しなし)
     *  - 必要秒数に達したら所有者を切り替え
     */
    private void evaluateCapture(Flag flag, GameConfig cfg) {
        Location loc = flag.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        double radiusSq = cfg.flagCaptureRadius * cfg.flagCaptureRadius;

        Set<String> teamsPresent = new HashSet<>();
        boolean ownerPresent = false;

        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) > radiusSq) {
                continue;
            }
            Team playerTeam = game.getTeamOf(player);
            if (playerTeam == null) {
                continue;
            }
            String teamId = playerTeam.getId();
            if (flag.isOwnedBy(teamId)) {
                ownerPresent = true;
            } else {
                teamsPresent.add(teamId.toLowerCase());
            }
        }

        // 所有者チームがいる、または敵チームが0/複数 -> 進捗停止
        if (ownerPresent || teamsPresent.size() != 1) {
            return;
        }

        String capturing = teamsPresent.iterator().next();
        if (!capturing.equals(flag.getCapturingTeamId())) {
            // 別チームがキャプチャを始めたので進捗リセット
            flag.setCapturingTeamId(capturing);
            flag.setCaptureTicks(0);
        }
        flag.setCaptureTicks(flag.getCaptureTicks() + 1);

        if (flag.getCaptureTicks() >= cfg.flagCaptureSeconds) {
            // 所有者変更 + ブロックの色更新 + 保存をまとめて行う
            game.setFlagOwner(flag, capturing);
            announceCapture(flag, capturing);
        }
    }

    private void announceCapture(Flag flag, String teamId) {
        var team = game.getTeam(teamId);
        String teamName = team != null ? team.getDisplayName() : teamId;
        org.bukkit.Bukkit.broadcastMessage(ChatColor.YELLOW + "[Flag] 旗 '" + flag.getId()
                + "' を " + teamName + ChatColor.YELLOW + " が占拠した！");
    }
}
