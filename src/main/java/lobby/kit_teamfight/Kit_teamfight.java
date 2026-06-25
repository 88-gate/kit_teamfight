package lobby.kit_teamfight;

import lobby.kit_teamfight.command.FlagCommand;
import lobby.kit_teamfight.command.GameCommand;
import lobby.kit_teamfight.command.KitCommand;
import lobby.kit_teamfight.game.FlagParticleTask;
import lobby.kit_teamfight.game.GameManager;
import lobby.kit_teamfight.game.GameTask;
import lobby.kit_teamfight.kit.KitShop;
import lobby.kit_teamfight.kit.kits.ArcherKit;
import lobby.kit_teamfight.kit.kits.HeavyCrossbowKit;
import lobby.kit_teamfight.kit.kits.LancerKit;
import lobby.kit_teamfight.kit.kits.NetherStarKit;
import lobby.kit_teamfight.kit.kits.RobinhoodKit;
import lobby.kit_teamfight.kit.kits.SkirmisherKit;
import lobby.kit_teamfight.kit.kits.SniperKit;
import lobby.kit_teamfight.kit.kits.SoldierKit;
import lobby.kit_teamfight.listener.PlayerListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Kit_teamfight extends JavaPlugin {

    private GameManager game;
    private GameTask gameTask;
    private FlagParticleTask particleTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        game = new GameManager(this);
        game.reload();
        game.getFlagManager().load();

        // kit を登録 (新規追加は register するだけ)
        game.getKitRegistry().register(new ArcherKit());
        game.getKitRegistry().register(new SkirmisherKit());
        game.getKitRegistry().register(new SoldierKit());
        game.getKitRegistry().register(new LancerKit());
        game.getKitRegistry().register(new RobinhoodKit());
        game.getKitRegistry().register(new HeavyCrossbowKit());
        game.getKitRegistry().register(new SniperKit());
        game.getKitRegistry().register(new NetherStarKit());

        // リスナー登録
        KitShop shop = new KitShop(game);
        getServer().getPluginManager().registerEvents(shop, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, game), this);

        // コマンド登録
        register("ktf", new GameCommand(game));
        register("kit", new KitCommand(game, shop));
        register("flag", new FlagCommand(game));

        // 1秒ごとの試合ループ
        gameTask = new GameTask(game);
        gameTask.runTaskTimer(this, 20L, 20L);

        // 占領範囲パーティクル (0.5秒間隔)
        particleTask = new FlagParticleTask(game);
        particleTask.runTaskTimer(this, 20L, 10L);

        // ワールドが完全に準備できてから旗ブロック/ホログラムを貼り直す
        getServer().getScheduler().runTask(this, () -> game.renderAllFlags());

        getLogger().info("kit_teamfight を有効化しました。");
    }

    @Override
    public void onDisable() {
        if (gameTask != null) {
            gameTask.cancel();
        }
        if (particleTask != null) {
            particleTask.cancel();
        }
        if (game != null) {
            game.cancelSelectionTask();     // チーム希望受付タスクが残っていれば止める
            game.removeAllFlagHolograms(); // 保存されないホログラムを掃除
            game.removeAllHorses();        // 騎兵の馬を片付ける
            game.getFlagManager().save();
        }
        getLogger().info("kit_teamfight を無効化しました。");
    }

    private void register(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("コマンド '" + name + "' が plugin.yml に定義されていません。");
        }
    }
}
