package lobby.kit_teamfight.flag;

import org.bukkit.Location;

import java.util.UUID;

/**
 * コマンドで設置される旗。所有チーム・キャプチャ進捗を保持する。
 * 旗は 0個でも試合は成立する (その場合チケット減少はキルのみ)。
 * location は設置されたブロックの座標 (キャプチャ判定の中心も兼ねる)。
 */
public class Flag {

    private final String id;
    private Location location;
    private String ownerTeamId;      // null = 中立
    private String capturingTeamId;  // 現在キャプチャ進行中のチーム (null = 進行なし)
    private int captureTicks;        // キャプチャ経過秒数
    private UUID hologramId;         // 旗名ホログラム (TextDisplay) の UUID。永続化しない

    public Flag(String id, Location location, String ownerTeamId) {
        this.id = id;
        this.location = location;
        this.ownerTeamId = ownerTeamId;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getOwnerTeamId() {
        return ownerTeamId;
    }

    public void setOwnerTeamId(String ownerTeamId) {
        this.ownerTeamId = ownerTeamId;
    }

    public boolean isOwnedBy(String teamId) {
        return ownerTeamId != null && ownerTeamId.equals(teamId);
    }

    public String getCapturingTeamId() {
        return capturingTeamId;
    }

    public void setCapturingTeamId(String capturingTeamId) {
        this.capturingTeamId = capturingTeamId;
    }

    public int getCaptureTicks() {
        return captureTicks;
    }

    public void setCaptureTicks(int captureTicks) {
        this.captureTicks = captureTicks;
    }

    /** キャプチャ進捗をリセットする。 */
        public void resetCapture() {
        this.capturingTeamId = null;
        this.captureTicks = 0;
    }

    public UUID getHologramId() {
        return hologramId;
    }

    public void setHologramId(UUID hologramId) {
        this.hologramId = hologramId;
    }
}
