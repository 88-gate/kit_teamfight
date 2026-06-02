package lobby.kit_teamfight.game;

import org.bukkit.ChatColor;
import org.bukkit.Location;

/**
 * 1チームを表すデータ。チケット・スポーン・表示情報を保持する。
 * チーム数は config で任意に増減できるため、ロジックは Team の集合をループして扱う。
 */
public class Team {

    private final String id;
    private final String name;
    private final ChatColor color;
    private Location spawn;
    private int tickets;

    public Team(String id, String name, ChatColor color, Location spawn, int initialTickets) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.spawn = spawn;
        this.tickets = initialTickets;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ChatColor getColor() {
        return color;
    }

    /** カラー付きの表示名。 */
    public String getDisplayName() {
        return color + name + ChatColor.RESET;
    }

    public Location getSpawn() {
        return spawn;
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
    }

    public int getTickets() {
        return tickets;
    }

    public void setTickets(int tickets) {
        this.tickets = Math.max(0, tickets);
    }

    /** チケットを amount 減らす。0未満にはならない。 */
    public void loseTickets(int amount) {
        setTickets(this.tickets - amount);
    }

    public boolean isEliminated() {
        return tickets <= 0;
    }
}
