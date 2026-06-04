package lobby.kit_teamfight.game;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * config.yml の値をまとめて保持する。すべての調整可能パラメータはここ経由でアクセスする。
 */
public class GameConfig {

    public int pointTickRateSeconds;
    public int pointAmountPerTick;
    public int pointKillBonus;

    public double flagCaptureRadius;
    public int flagCaptureSeconds;
    public int flagDrainIntervalSeconds;
    public int flagDrainPerFlag;

    public int ticketInitial;
    public int ticketLossPerKill;

    public void load(FileConfiguration c) {
        pointTickRateSeconds = Math.max(1, c.getInt("point.tickRateSeconds", 1));
        pointAmountPerTick = Math.max(0, c.getInt("point.amountPerTick", 1));
        pointKillBonus = Math.max(0, c.getInt("point.killBonus", 10));

        flagCaptureRadius = Math.max(1.0, c.getDouble("flag.captureRadius", 5.0));
        flagCaptureSeconds = Math.max(1, c.getInt("flag.captureSeconds", 5));
        flagDrainIntervalSeconds = Math.max(1, c.getInt("flag.ticketDrainIntervalSeconds", 15));
        flagDrainPerFlag = Math.max(0, c.getInt("flag.ticketDrainPerFlag", 1));

        ticketInitial = Math.max(1, c.getInt("ticket.initial", 100));
        ticketLossPerKill = Math.max(0, c.getInt("ticket.lossPerKill", 1));
    }
}
