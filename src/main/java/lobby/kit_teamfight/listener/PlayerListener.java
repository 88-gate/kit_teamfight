package lobby.kit_teamfight.listener;

import lobby.kit_teamfight.game.GameManager;
import lobby.kit_teamfight.game.Team;
import lobby.kit_teamfight.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import lobby.kit_teamfight.flag.Flag;

/**
 * 参加時のチーム振り分け・装備付与、死亡時のチケット減少、リスポーン時の装備再付与を扱う。
 */
public class PlayerListener implements Listener {

    private final GameManager game;
    private final Plugin plugin;

    public PlayerListener(Plugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = game.getPlayerData(player);
        if (data.getTeamId() == null) {
            game.autoAssign(player);
        }
        game.applyNameTag(player);
        Team team = game.getTeamOf(player);
        if (team != null) {
            player.sendMessage(ChatColor.GREEN + "あなたのチーム: " + team.getDisplayName());
        }
        // 試合中ならその場で装備を整える
        if (game.isRunning()) {
            game.equip(player);
        } else {
            game.giveStartingLoadout(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        game.removeBoard(event.getPlayer());
        game.removeHorse(event.getPlayer());
    }

    /** 試合中はアイテムを捨てられないようにする (kit の装備を投棄させない)。 */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (game.isRunning()) {
            event.setCancelled(true);
        }
    }

    /**
     * 馬の騎乗制限: 騎兵系 kit のプレイヤーだけが馬に乗れる。
     * 馬の所有者は問わない(敵の馬でも騎兵系なら乗れる)。
     */
    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(event.getMount() instanceof AbstractHorse)) {
            return; // 馬系以外(ボート/トロッコ等)は制限しない
        }
        if (!game.isCavalry(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "騎兵系の役職でないと馬に乗れません。");
        }
    }

    /**
     * フレンドリーファイア無効化: 同じチームのプレイヤー同士のダメージをキャンセルする。
     * 近接攻撃に加え、矢などの飛び道具(撃ったプレイヤーで判定)も対象。
     */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        Team victimTeam = game.getTeamOf(victim);
        Team attackerTeam = game.getTeamOf(attacker);
        if (victimTeam != null && victimTeam == attackerTeam) {
            event.setCancelled(true);
        }
    }

    /** ダメージ源からプレイヤーの攻撃者を特定する。飛び道具なら撃った主を返す。 */
    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        game.onDeath(victim);
        // 死亡で保有 kit を解除 (次は初期装備)
        game.resetKitOnDeath(victim);
        // 馬は復活しないので、死亡時に馬を片付ける
        game.removeHorse(victim);

        // keepInventory 前提。リスポーン後にサーバが「保持インベントリ」を復元して
        // こちらの装備を上書きしてしまうため、装備の積み替えは死亡イベントで行う。
        // ドロップを消し、インベントリ保持を有効化した上で初期装備を積む
        // → 保持されるインベントリ自体が初期装備になり、復活後それで出る。
        event.getDrops().clear();
        event.setKeepInventory(true);
        game.equip(victim); // クリア → 初期装備(革+石剣) + パン
    }

    /**
     * 旗ブロックが壊されたら、その旗を無効化 (削除) する。
     * バグ防止用で、ゲーム中に破壊される運用は想定していない。
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Flag flag = game.getFlagAtBlock(event.getBlock().getLocation());
        if (flag == null) {
            return;
        }
        game.removeFlag(flag.getId());
        event.getPlayer().sendMessage(ChatColor.YELLOW + "旗 '" + flag.getId() + "' を破壊したため無効化しました。");
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // リスポーン地点の設定と、スコアボード/ネームタグの貼り直しのみ。
        // 装備は死亡イベントで積み替え済み (keepInventory で保持される)。
        Player player = event.getPlayer();
        Team team = game.getTeamOf(player);
        if (team != null && team.getSpawn() != null) {
            event.setRespawnLocation(team.getSpawn());
        }
        game.applyNameTag(player);
    }
}
