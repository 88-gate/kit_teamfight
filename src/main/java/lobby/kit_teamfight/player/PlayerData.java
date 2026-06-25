package lobby.kit_teamfight.player;

import java.util.UUID;

/**
 * プレイヤーごとの試合中データ。ポイント・保有 kit を持つ。
 * 所属チームはバニラのメインスコアボード (GameManager.getTeamOf) で管理する。
 * ポイントは死亡では消えず、試合終了時にリセットされる。
 */
public class PlayerData {

    private final UUID uuid;
    private int points;
    private int kills; // その試合のキル数 (試合開始でリセット)
    private String currentKitId; // null = kit 未保有 (初期装備)

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getPoints() {
        return points;
    }

    public void addPoints(int amount) {
        this.points += amount;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public boolean spendPoints(int amount) {
        if (points < amount) {
            return false;
        }
        points -= amount;
        return true;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    public String getCurrentKitId() {
        return currentKitId;
    }

    public void setCurrentKitId(String currentKitId) {
        this.currentKitId = currentKitId;
    }

    public boolean hasKit() {
        return currentKitId != null;
    }

    /** 試合終了時のリセット。チーム所属はバニラ側で保持されるためここでは触らない。 */
    public void resetForNewMatch() {
        this.points = 0;
        this.kills = 0;
        this.currentKitId = null;
    }
}
