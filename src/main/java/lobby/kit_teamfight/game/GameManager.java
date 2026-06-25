package lobby.kit_teamfight.game;

import lobby.kit_teamfight.flag.FlagManager;
import lobby.kit_teamfight.kit.Kit;
import lobby.kit_teamfight.kit.KitRegistry;
import lobby.kit_teamfight.player.PlayerData;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 試合全体のハブ。チーム・プレイヤーデータ・kit・旗を束ね、装備付与/購入/チケット/勝敗を扱う。
 * チーム数・旗数に依存する処理はすべてコレクションのループで書き、N=0/1 でも破綻しないようにする。
 */
public class GameManager {

    private final Plugin plugin;
    private final GameConfig config = new GameConfig();
    private final KitRegistry kitRegistry = new KitRegistry();
    private final Kit defaultKit = new lobby.kit_teamfight.kit.kits.DefaultKit(); // 初期装備 (ショップ非表示)
    private final FlagManager flagManager;
    private final lobby.kit_teamfight.flag.FlagVisualizer flagVisualizer;

    private final Map<String, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, PlayerData> players = new LinkedHashMap<>();

    private boolean running = false;

    // ---- チーム希望受付フェーズ ---------------------------------------------
    /** 希望受付中にプレイヤーが選んだチーム (uuid -> teamId 小文字)。 */
    private final Map<UUID, String> teamPreferences = new HashMap<>();
    private boolean selectionActive = false;
    private TeamSelectionTask selectionTask;
    private final java.util.Random random = new java.util.Random();

    public GameManager(Plugin plugin) {
        this.plugin = plugin;
        this.flagManager = new FlagManager(plugin);
        this.flagVisualizer = new lobby.kit_teamfight.flag.FlagVisualizer(this);
    }

    // ---- 初期化 -------------------------------------------------------------

    public void reload() {
        config.load(plugin.getConfig());
        loadTeams();
        syncVanillaTeams(); // メインボードへ対応するバニラチームを作成/色同期
        // チーム定義が変わった可能性があるので、各プレイヤーのボードを作り直して再適用
        boards.clear();
        for (Player online : Bukkit.getOnlinePlayers()) {
            applyNameTag(online);
        }
    }

    /** メインスコアボードを返す (取得不可なら null)。チーム所属の正はここ。 */
    private org.bukkit.scoreboard.Scoreboard mainBoard() {
        return Bukkit.getScoreboardManager() == null ? null
                : Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /**
     * config のチームに対応するバニラチームをメインボードに用意し、色を同期する。
     * 既存の所属エントリは消さない。OP は /team join &lt;id&gt; &lt;player&gt; でここへ振り分ける。
     */
    private void syncVanillaTeams() {
        org.bukkit.scoreboard.Scoreboard main = mainBoard();
        if (main == null) {
            return;
        }
        for (Team team : teams.values()) {
            org.bukkit.scoreboard.Team vt = main.getTeam(team.getId());
            if (vt == null) {
                vt = main.registerNewTeam(team.getId());
            }
            try {
                if (team.getColor().isColor()) {
                    vt.setColor(team.getColor());
                }
            } catch (IllegalArgumentException ignored) {
                // 色以外の ChatColor は無視
            }
        }
    }

    private void loadTeams() {
        teams.clear();
        List<Map<?, ?>> defs = plugin.getConfig().getMapList("teams");
        for (Map<?, ?> def : defs) {
            String id = String.valueOf(def.get("id"));
            String name = def.get("name") != null ? String.valueOf(def.get("name")) : id;
            ChatColor color = parseColor(def.get("color"));
            Location spawn = parseLocation(def.get("spawn"));
            teams.put(id.toLowerCase(), new Team(id, name, color, spawn, config.ticketInitial));
        }
        if (teams.isEmpty()) {
            plugin.getLogger().warning("config.yml にチームが定義されていません。/team コマンドは機能しません。");
        }
    }

    // ---- スコアボード (ネームタグ色 + サイドバー) ---------------------------
    // バニラのメインボードは /scoreboard・/team で外から上書きされるため使わない。
    // ポイントはプレイヤーごとに違うので、ボードも *プレイヤーごとに1枚* 持つ。
    //   - ネームタグ色: 各ボードに全チームを登録し、全オンライン者を所属チームに入れる
    //   - サイドバー: 各チームのチケット + そのボードの持ち主自身のポイントを表示

    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> boards = new HashMap<>();
    private static final String SIDEBAR_OBJ = "ktf_side";
    private static final String POINT_LINE = ChatColor.AQUA + "あなたのポイント";

    /** プレイヤー専用ボードを取得 (無ければ作成しチームを登録)。取得不可なら null。 */
    private org.bukkit.scoreboard.Scoreboard boardFor(Player player) {
        if (Bukkit.getScoreboardManager() == null) {
            return null;
        }
        org.bukkit.scoreboard.Scoreboard b = boards.get(player.getUniqueId());
        if (b == null) {
            b = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(player.getUniqueId(), b);
            setupTeamsOn(b);
        }
        return b;
    }

    /** ボード b に全チームを登録し、config の色をネームタグ色に設定する。 */
    private void setupTeamsOn(org.bukkit.scoreboard.Scoreboard b) {
        for (Team team : teams.values()) {
            org.bukkit.scoreboard.Team sb = b.getTeam(team.getId());
            if (sb == null) {
                sb = b.registerNewTeam(team.getId());
            }
            try {
                if (team.getColor().isColor()) {
                    sb.setColor(team.getColor());
                }
            } catch (IllegalArgumentException ignored) {
                // 色以外の ChatColor が来ても無視 (ネームタグは無色のまま)
            }
        }
    }

    /** ボード b のチーム所属を、現在のオンライン全員ぶん貼り直す。 */
    private void syncMembership(org.bukkit.scoreboard.Scoreboard b) {
        for (Team team : teams.values()) {
            org.bukkit.scoreboard.Team sb = b.getTeam(team.getId());
            if (sb == null) {
                continue;
            }
            for (String entry : new HashSet<>(sb.getEntries())) {
                sb.removeEntry(entry);
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            Team team = getTeamOf(online);
            if (team == null) {
                continue;
            }
            org.bukkit.scoreboard.Team sb = b.getTeam(team.getId());
            if (sb != null) {
                sb.addEntry(online.getName());
            }
        }
    }

    /** プレイヤーに専用ボードを割り当て、全員のネームタグ所属を同期する。 */
    public void applyNameTag(Player player) {
        if (!applyBoard(player)) {
            return;
        }
        // 全員のボードで所属を貼り直す (誰から見ても色が反映されるように)
        for (Player online : Bukkit.getOnlinePlayers()) {
            org.bukkit.scoreboard.Scoreboard ob = boards.get(online.getUniqueId());
            if (ob != null) {
                syncMembership(ob);
            }
        }
        updateSidebar();
    }

    /** プレイヤーに専用ボードを作成/取得して割り当てる (再帰しない軽量版)。成功で true。 */
    private boolean applyBoard(Player player) {
        org.bukkit.scoreboard.Scoreboard b = boardFor(player);
        if (b == null) {
            return false;
        }
        player.setScoreboard(b);
        return true;
    }

    /** 各プレイヤーのサイドバーを更新する。チーム別チケット + 自分のポイント。毎秒呼ばれる。 */
    public void updateSidebar() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            org.bukkit.scoreboard.Scoreboard b = boards.get(online.getUniqueId());
            if (b == null) {
                // まだボード未割り当て(リロード後など)。割り当ててから更新する
                applyBoard(online);
                b = boards.get(online.getUniqueId());
                if (b == null) {
                    continue;
                }
            }
            // 自己修復: リスポーン/ワールド移動等で表示ボードがメインに戻ったら貼り直す
            if (online.getScoreboard() != b) {
                online.setScoreboard(b);
            }
            org.bukkit.scoreboard.Objective obj = b.getObjective(SIDEBAR_OBJ);
            if (obj == null) {
                obj = b.registerNewObjective(SIDEBAR_OBJ, "dummy",
                        ChatColor.GOLD + "" + ChatColor.BOLD + "Kit Teamfight");
                obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
            }
            obj.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Kit Teamfight"
                    + (running ? "" : ChatColor.GRAY + " (待機中)"));
            // 各チーム行 (エントリ=色付きチーム名、スコア=チケット)。エントリ名は不変なので上書きでOK
            for (Team team : teams.values()) {
                obj.getScore(team.getColor() + team.getName()).setScore(team.getTickets());
            }
            // 自分のポイント行 (ラベル固定、スコア=ポイント値)
            PlayerData data = getPlayerData(online);
            obj.getScore(POINT_LINE).setScore(data.getPoints());

            // 旗の所有チーム行。所有者が変わると表示が変わるため、エントリは不変トークンに固定し
            // scoreboard Team の prefix だけ書き換える (古い行が残らないようにする)。
            int fi = 0;
            for (lobby.kit_teamfight.flag.Flag flag : flagManager.all()) {
                String teamName = "ktf_f" + fi;
                org.bukkit.scoreboard.Team line = b.getTeam(teamName);
                if (line == null) {
                    line = b.registerNewTeam(teamName);
                }
                String token = ChatColor.values()[fi % ChatColor.values().length].toString();
                line.addEntry(token);
                line.setPrefix(ChatColor.YELLOW + "旗" + flag.getId() + ": " + flagOwnerLabel(flag));
                obj.getScore(token).setScore(-1 - fi); // 旗行はサイドバー下部へ
                fi++;
            }

            // Tab (プレイヤーリスト) に各プレイヤーのキル数を表示する
            org.bukkit.scoreboard.Objective klist = b.getObjective("ktf_klist");
            if (klist == null) {
                klist = b.registerNewObjective("ktf_klist", "dummy",
                        ChatColor.RED + "Kills");
                klist.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.PLAYER_LIST);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                klist.getScore(p.getName()).setScore(getPlayerData(p).getKills());
            }
        }
    }

    /** 旗の所有チームを色付きラベルにする。未所有は「中立」。 */
    private String flagOwnerLabel(lobby.kit_teamfight.flag.Flag flag) {
        String ownerId = flag.getOwnerTeamId();
        if (ownerId == null) {
            return ChatColor.GRAY + "中立";
        }
        Team team = getTeam(ownerId);
        if (team == null) {
            return ChatColor.GRAY + "中立";
        }
        return team.getColor() + "" + team.getName();
    }

    /** プレイヤー切断時にボードを破棄する。 */
    public void removeBoard(Player player) {
        boards.remove(player.getUniqueId());
    }

    private ChatColor parseColor(Object raw) {
        if (raw == null) {
            return ChatColor.WHITE;
        }
        try {
            return ChatColor.valueOf(String.valueOf(raw).toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatColor.WHITE;
        }
    }

    @SuppressWarnings("unchecked")
    private Location parseLocation(Object raw) {
        if (!(raw instanceof Map)) {
            return null;
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        Object worldName = m.get("world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(String.valueOf(worldName));
        if (world == null) {
            return null;
        }
        double x = toDouble(m.get("x"));
        double y = toDouble(m.get("y"));
        double z = toDouble(m.get("z"));
        float yaw = (float) toDouble(m.get("yaw"));
        float pitch = (float) toDouble(m.get("pitch"));
        return new Location(world, x, y, z, yaw, pitch);
    }

    private double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ---- アクセサ -----------------------------------------------------------

    public GameConfig getConfig() {
        return config;
    }

    public KitRegistry getKitRegistry() {
        return kitRegistry;
    }

    public FlagManager getFlagManager() {
        return flagManager;
    }

    public boolean isRunning() {
        return running;
    }

    // ---- 旗 (ブロック設置 + 見た目) -----------------------------------------

    /** 旗を作成し、ブロックとホログラムを設置する。 */
    public lobby.kit_teamfight.flag.Flag createFlag(String id, Location blockLoc, String owner) {
        lobby.kit_teamfight.flag.Flag flag = flagManager.create(id, blockLoc, owner);
        flagVisualizer.render(flag);
        return flag;
    }

    /** 旗を削除し、ブロックとホログラムを撤去する。 */
    public lobby.kit_teamfight.flag.Flag removeFlag(String id) {
        lobby.kit_teamfight.flag.Flag flag = flagManager.get(id);
        if (flag != null) {
            flagVisualizer.remove(flag);
        }
        return flagManager.remove(id);
    }

    /** 旗の所有者を変更し、ブロックの色を更新する。 */
    public void setFlagOwner(lobby.kit_teamfight.flag.Flag flag, String teamId) {
        flag.setOwnerTeamId(teamId);
        flag.resetCapture();
        flagVisualizer.render(flag);
        flagVisualizer.showCaptureBurst(flag); // 占領した瞬間の軽い演出
        flagManager.save();
    }

    /** 読み込んだ全旗のブロック/ホログラムを貼り直す (onEnable 用)。 */
    public void renderAllFlags() {
        flagVisualizer.renderAll(flagManager.all());
    }

    /** 全旗を中立 (owner=null) に戻し、キャプチャ進捗をリセットしてブロック色を更新する (試合開始用)。 */
    public void neutralizeAllFlags() {
        for (lobby.kit_teamfight.flag.Flag flag : flagManager.all()) {
            flag.setOwnerTeamId(null);
            flag.resetCapture();
            flagVisualizer.render(flag);
        }
        flagManager.save();
    }

    /** ホログラムだけ撤去する (onDisable 用)。 */
    public void removeAllFlagHolograms() {
        flagVisualizer.removeAllHolograms(flagManager.all());
    }

    /** 全旗の占領範囲パーティクルを描画する (パーティクルタスク用)。 */
    public void tickFlagParticles() {
        double radius = config.flagCaptureRadius;
        for (lobby.kit_teamfight.flag.Flag flag : flagManager.all()) {
            flagVisualizer.showCaptureRing(flag, radius);
        }
    }

    /** 指定ブロック座標にある旗を返す (破壊検知用)。無ければ null。 */
    public lobby.kit_teamfight.flag.Flag getFlagAtBlock(Location blockLoc) {
        if (blockLoc == null || blockLoc.getWorld() == null) {
            return null;
        }
        for (lobby.kit_teamfight.flag.Flag flag : flagManager.all()) {
            Location loc = flag.getLocation();
            if (loc != null && loc.getWorld() != null
                    && loc.getWorld().equals(blockLoc.getWorld())
                    && loc.getBlockX() == blockLoc.getBlockX()
                    && loc.getBlockY() == blockLoc.getBlockY()
                    && loc.getBlockZ() == blockLoc.getBlockZ()) {
                return flag;
            }
        }
        return null;
    }

    public Team getTeam(String id) {
        return id == null ? null : teams.get(id.toLowerCase());
    }

    public java.util.Collection<Team> getTeams() {
        return teams.values();
    }

    public PlayerData getPlayerData(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), PlayerData::new);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return players.get(uuid);
    }

    /** プレイヤーの所属チームをメインボードのバニラチームから引く。未所属/未対応なら null。 */
    public Team getTeamOf(Player player) {
        org.bukkit.scoreboard.Scoreboard main = mainBoard();
        if (main == null) {
            return null;
        }
        org.bukkit.scoreboard.Team vt = main.getEntryTeam(player.getName());
        return vt == null ? null : getTeam(vt.getName());
    }

    /** プレイヤーをメインボードのバニラチームへ入れる (同名以外からは自動的に外れる)。 */
    private void addToVanillaTeam(Player player, String teamId) {
        org.bukkit.scoreboard.Scoreboard main = mainBoard();
        if (main == null) {
            return;
        }
        org.bukkit.scoreboard.Team vt = main.getTeam(teamId);
        if (vt == null) {
            vt = main.registerNewTeam(teamId);
        }
        vt.addEntry(player.getName());
    }

    /**
     * チームのスポーン地点を設定し、config.yml に永続化する (向き込み)。
     * @return 対象チームが見つかれば true
     */
    public boolean setTeamSpawn(String teamId, Location loc) {
        Team team = getTeam(teamId);
        if (team == null || loc == null || loc.getWorld() == null) {
            return false;
        }
        team.setSpawn(loc);

        // config.yml の teams リストを更新して保存する
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("teams");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : entry.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            if (team.getId().equalsIgnoreCase(String.valueOf(copy.get("id")))) {
                Map<String, Object> spawn = new LinkedHashMap<>();
                spawn.put("world", loc.getWorld().getName());
                spawn.put("x", loc.getX());
                spawn.put("y", loc.getY());
                spawn.put("z", loc.getZ());
                spawn.put("yaw", (double) loc.getYaw());
                spawn.put("pitch", (double) loc.getPitch());
                copy.put("spawn", spawn);
            }
            out.add(copy);
        }
        plugin.getConfig().set("teams", out);
        plugin.saveConfig();
        return true;
    }

    // ---- チーム振り分け -----------------------------------------------------

    /** 人数が最も少ないチームへ自動配属する。 */
    public Team autoAssign(Player player) {
        if (teams.isEmpty()) {
            return null;
        }
        Team smallest = null;
        int smallestCount = Integer.MAX_VALUE;
        for (Team team : teams.values()) {
            int count = countMembers(team.getId());
            if (count < smallestCount) {
                smallestCount = count;
                smallest = team;
            }
        }
        if (smallest != null) {
            addToVanillaTeam(player, smallest.getId());
            applyNameTag(player);
        }
        return smallest;
    }

    /**
     * オンライン全員をランダムにシャッフルし、各チームへ人数が均等 (差は最大1) になるよう振り分ける。
     * シャッフルしたプレイヤー列をチームへラウンドロビンで配ることで均等性を保証する。
     * @return 振り分けた人数。チームが未定義なら -1。
     */
    public int shuffleTeams() {
        if (teams.isEmpty()) {
            return -1;
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        java.util.Collections.shuffle(online);
        List<Team> teamList = new ArrayList<>(teams.values());
        for (int i = 0; i < online.size(); i++) {
            Player player = online.get(i);
            Team team = teamList.get(i % teamList.size());
            addToVanillaTeam(player, team.getId());
        }
        // ネームタグ/ボードの所属を全員ぶん貼り直す
        for (Player player : online) {
            applyNameTag(player);
        }
        return online.size();
    }

    // ---- チーム希望受付フェーズ ---------------------------------------------

    public boolean isSelectionActive() {
        return selectionActive;
    }

    /**
     * 15秒のチーム希望受付フェーズを開始する。受付中はプレイヤーが {@link #setTeamPreference} で
     * 希望チームを選べる。受付終了時に {@link #finalizeTeamSelection} が呼ばれ、人数差が最大1に
     * なるよう均等化する。
     * @return 開始できれば true。チーム未定義 / 既に受付中なら false。
     */
    public boolean startTeamSelection() {
        if (teams.isEmpty() || selectionActive) {
            return false;
        }
        teamPreferences.clear();
        selectionActive = true;
        broadcastTeamSelectPrompt();
        selectionTask = new TeamSelectionTask(this, 15);
        selectionTask.runTaskTimer(plugin, 20L, 20L);
        return true;
    }

    /** 希望受付タスクが動いていれば停止する (onDisable などの後始末用)。 */
    public void cancelSelectionTask() {
        if (selectionTask != null) {
            selectionTask.cancel();
            selectionTask = null;
        }
        selectionActive = false;
        teamPreferences.clear();
    }

    /**
     * 受付中にプレイヤーの希望チームを登録する。見た目にも即座に反映する。
     * @return 登録できれば true。受付中でない / チームが存在しないなら false。
     */
    public boolean setTeamPreference(Player player, String teamId) {
        if (!selectionActive) {
            return false;
        }
        Team team = getTeam(teamId);
        if (team == null) {
            return false;
        }
        teamPreferences.put(player.getUniqueId(), team.getId());
        // フィードバックとして見た目も即反映 (最終確定は finalizeTeamSelection)
        addToVanillaTeam(player, team.getId());
        applyNameTag(player);
        player.sendMessage(ChatColor.GREEN + team.getDisplayName()
                + ChatColor.GREEN + " を希望しました。");
        return true;
    }

    /**
     * 受付中にプレイヤーの希望を「おまかせ (未希望)」に戻す。finalize 時にランダム配分される。
     * @return 受付中なら true。受付中でなければ false。
     */
    public boolean clearTeamPreference(Player player) {
        if (!selectionActive) {
            return false;
        }
        teamPreferences.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "チームをおまかせ (ランダム) にしました。");
        return true;
    }

    /** 各チームのクリック可能なボタンを全員に表示する。 */
    private void broadcastTeamSelectPrompt() {
        net.kyori.adventure.text.Component header = net.kyori.adventure.text.Component.text(
                "15秒以内に希望チームをクリック！ ", net.kyori.adventure.text.format.NamedTextColor.GOLD);
        net.kyori.adventure.text.Component line = header;
        for (Team team : teams.values()) {
            net.kyori.adventure.text.Component button =
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                            .deserialize("[" + team.getDisplayName() + "]")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ktf team " + team.getId()))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    net.kyori.adventure.text.Component.text(team.getName() + " を希望する")));
            line = line.append(button).append(net.kyori.adventure.text.Component.text(" "));
        }
        // おまかせ (ランダム) ボタン。希望を出さずランダム配分を選べる。
        net.kyori.adventure.text.Component randomButton =
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(ChatColor.GRAY + "[おまかせ]")
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/ktf team random"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                net.kyori.adventure.text.Component.text("チームをおまかせ (ランダム) にする")));
        line = line.append(randomButton);
        Bukkit.broadcast(line);
    }

    /**
     * 受付終了時に呼ばれ、希望を反映しつつ人数差が最大1になるよう均等化する。
     * 希望者は希望チームへ、未希望者は人数の少ないチームへ配る。それでも差が2以上残る場合は
     * 多いチームから「未希望者を優先」してランダムに少ないチームへ移動する。
     */
    void finalizeTeamSelection() {
        selectionActive = false;
        selectionTask = null;
        try {
            if (teams.isEmpty()) {
                return;
            }
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            List<Team> teamList = new ArrayList<>(teams.values());

            // teamId -> そのチームに割り当てたプレイヤー一覧
            Map<String, List<Player>> assign = new HashMap<>();
            for (Team t : teamList) {
                assign.put(t.getId(), new ArrayList<>());
            }
            // 希望を出さなかったプレイヤー (バランス調整で優先的に動かす)
            java.util.Set<UUID> unchosen = new java.util.HashSet<>();

            // 1) 希望者は希望チームへ
            List<Player> noPref = new ArrayList<>();
            for (Player p : online) {
                String pref = teamPreferences.get(p.getUniqueId());
                if (pref != null && assign.containsKey(pref)) {
                    assign.get(pref).add(p);
                } else {
                    noPref.add(p);
                    unchosen.add(p.getUniqueId());
                }
            }

            // 2) 未希望者を毎回「最小人数チーム」へ貪欲に配る
            java.util.Collections.shuffle(noPref, random);
            for (Player p : noPref) {
                smallestTeam(assign, teamList).add(p);
            }

            // 3) 人数差が最大1になるまで、多いチームから少ないチームへ移動 (未希望者優先)
            while (true) {
                List<Player> maxList = largestTeam(assign, teamList);
                List<Player> minList = smallestTeam(assign, teamList);
                if (maxList.size() - minList.size() <= 1) {
                    break;
                }
                Player mover = pickMover(maxList, unchosen);
                maxList.remove(mover);
                minList.add(mover);
            }

            // 4) 確定反映
            for (Map.Entry<String, List<Player>> e : assign.entrySet()) {
                for (Player p : e.getValue()) {
                    addToVanillaTeam(p, e.getKey());
                    applyNameTag(p);
                }
            }
            updateSidebar();

            // 5) 最終人数を告知
            StringBuilder sb = new StringBuilder(ChatColor.GOLD + "[KitTeamfight] チーム分け完了！ ");
            boolean first = true;
            for (Team t : teamList) {
                if (!first) {
                    sb.append(ChatColor.GRAY).append(" / ");
                }
                sb.append(t.getDisplayName()).append(ChatColor.GRAY).append(": ")
                        .append(assign.get(t.getId()).size()).append("人");
                first = false;
            }
            Bukkit.broadcastMessage(sb.toString());
        } finally {
            teamPreferences.clear();
        }
    }

    /** 移動させるプレイヤーを選ぶ。未希望者がいれば優先、いなければランダム。 */
    private Player pickMover(List<Player> from, java.util.Set<UUID> unchosen) {
        List<Player> candidates = new ArrayList<>();
        for (Player p : from) {
            if (unchosen.contains(p.getUniqueId())) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            candidates = from;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private List<Player> smallestTeam(Map<String, List<Player>> assign, List<Team> teamList) {
        List<Player> smallest = null;
        for (Team t : teamList) {
            List<Player> list = assign.get(t.getId());
            if (smallest == null || list.size() < smallest.size()) {
                smallest = list;
            }
        }
        return smallest;
    }

    private List<Player> largestTeam(Map<String, List<Player>> assign, List<Team> teamList) {
        List<Player> largest = null;
        for (Team t : teamList) {
            List<Player> list = assign.get(t.getId());
            if (largest == null || list.size() > largest.size()) {
                largest = list;
            }
        }
        return largest;
    }

    public boolean joinTeam(Player player, String teamId) {
        Team team = getTeam(teamId);
        if (team == null) {
            return false;
        }
        addToVanillaTeam(player, team.getId());
        applyNameTag(player);
        return true;
    }

    private int countMembers(String teamId) {
        Team team = getTeam(teamId);
        if (team == null) {
            return 0;
        }
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            Team t = getTeamOf(online);
            if (t != null && t.getId().equalsIgnoreCase(team.getId())) {
                count++;
            }
        }
        return count;
    }

    // ---- 装備 ---------------------------------------------------------------

    /**
     * 保有 kit があれば再適用、なければ初期装備(デフォルト kit)を付与する。
     * 初期装備も Kit として扱うため、パン2スタックの共通配布は全ケースで行われる。
     */
    public void equip(Player player) {
        PlayerData data = getPlayerData(player);
        clearEquipment(player);
        clearPotionEffects(player);
        removeHorse(player); // 前の馬が残っていれば消す (騎兵→非騎兵の切替も含む)

        Kit kit = kitRegistry.get(data.getCurrentKitId());
        if (kit == null) {
            kit = defaultKit; // 未購入/死亡後の復帰は初期装備 kit
        }
        kit.apply(player);
        // 全 kit 共通: パン2スタック (初期装備にも配る)
        PlayerInventory inv = player.getInventory();
        inv.addItem(new ItemStack(Material.BREAD, 64));
        inv.addItem(new ItemStack(Material.BREAD, 64));
        if (kit.isCavalry()) {
            // 騎兵系: 小麦2スタック + 馬 (自動騎乗)
            inv.addItem(new ItemStack(Material.WHEAT, 64));
            inv.addItem(new ItemStack(Material.WHEAT, 64));
            spawnCavalryHorse(player, kit);
        }
    }

    /** 初期装備(革+石剣+パン)を付与する。中身はデフォルト kit + 共通配布。 */
    public void giveStartingLoadout(Player player) {
        equip(player);
    }

    /** 満腹度と HP を全回復する (試合開始時に呼ぶ)。HP は最大値属性に合わせる。 */
    public void restoreVitals(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
    }

    private void clearEquipment(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
        inv.setItemInOffHand(null); // 盾などオフハンドも消す
        inv.clear();
    }

    /** 付与中のポーション効果をすべて除去する。 */
    private void clearPotionEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    // ---- 騎兵系の馬 ---------------------------------------------------------
    // 騎兵系 kit のスポーン時に馬を出して自動騎乗させる。プレイヤーごとに1頭を追跡し、
    // kit切替/死亡/退出で古い馬を消す。馬は復活しない (殺されても再発行しない)。

    private final Map<UUID, UUID> playerHorses = new HashMap<>();

    /** 現在の kit が騎兵系かどうか。 */
    public boolean isCavalry(Player player) {
        Kit kit = kitRegistry.get(getPlayerData(player).getCurrentKitId());
        return kit != null && kit.isCavalry();
    }

    /** 騎兵系の馬を出現させ、プレイヤーを自動騎乗させる。平均くらいの性能に固定する。 */
    private void spawnCavalryHorse(Player player, Kit kit) {
        removeHorse(player);
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Horse horse = world.spawn(loc, Horse.class);
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(player);
        horse.setMaxDomestication(1);
        horse.setDomestication(1);
        horse.setPersistent(false); // 再起動でワールドに残さない

        // 性能を固定 (バニラはランダムなので再現性のため)。HP/速度は kit ごと
        double maxHp = kit.horseMaxHealth();
        setAttribute(horse, Attribute.MAX_HEALTH, maxHp);
        horse.setHealth(maxHp);
        setAttribute(horse, Attribute.MOVEMENT_SPEED, kit.horseMovementSpeed());
        setAttribute(horse, Attribute.JUMP_STRENGTH, 0.7);

        // サドル + 馬鎧 (馬鎧は BODY スロット)
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        Material armor = kit.horseArmor();
        if (armor != null && horse.getEquipment() != null) {
            horse.getEquipment().setItem(EquipmentSlot.BODY, new ItemStack(armor));
        }

        horse.addPassenger(player); // 自動騎乗
        playerHorses.put(player.getUniqueId(), horse.getUniqueId());
    }

    private void setAttribute(Horse horse, Attribute attribute, double value) {
        AttributeInstance instance = horse.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** プレイヤーに紐づく馬を消す (追跡から外す)。 */
    public void removeHorse(Player player) {
        removeHorse(player.getUniqueId());
    }

    public void removeHorse(UUID playerId) {
        UUID horseId = playerHorses.remove(playerId);
        if (horseId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(horseId);
        if (entity != null) {
            entity.remove();
        }
    }

    /** 出現中の全ての馬を消す (onDisable / 試合終了用)。 */
    public void removeAllHorses() {
        for (UUID horseId : playerHorses.values()) {
            Entity entity = Bukkit.getEntity(horseId);
            if (entity != null) {
                entity.remove();
            }
        }
        playerHorses.clear();
    }

    // ---- kit 購入 -----------------------------------------------------------

    /**
     * kit を購入し装備を完全置換する。
     * @return 結果メッセージ (失敗理由を含む)
     */
    public String buyKit(Player player, String kitId) {
        Kit kit = kitRegistry.get(kitId);
        if (kit == null) {
            return ChatColor.RED + "その kit は存在しません。";
        }
        PlayerData data = getPlayerData(player);
        if (kit.getId().equalsIgnoreCase(data.getCurrentKitId())) {
            return ChatColor.YELLOW + "すでにその kit を装備しています。";
        }
        if (!data.spendPoints(kit.getCost())) {
            return ChatColor.RED + "ポイントが足りません (必要: " + kit.getCost()
                    + " / 所持: " + data.getPoints() + ")";
        }
        data.setCurrentKitId(kit.getId());
        // kit 選択時はリスポーン地点 (自チームスポーン) へ戻してから装備する。
        // 騎兵系の馬もスポーン地点に出るよう、テレポート→equip の順にする。
        Team team = getTeamOf(player);
        if (team != null && team.getSpawn() != null) {
            player.teleport(team.getSpawn());
        }
        // 完全置換: equip が clearEquipment/効果除去/馬の出し直し/パン配布まで行う
        equip(player);
        return ChatColor.GREEN + kit.getDisplayName() + " を購入しました！残りポイント: " + data.getPoints();
    }

    /** 死亡時に保有 kit を解除する。次のリスポーンで初期装備に戻る。 */
    public void resetKitOnDeath(Player player) {
        getPlayerData(player).setCurrentKitId(null);
    }

    /**
     * 初期チケット数 (試合開始時に各チームへ配られる値) を設定し config.yml に永続化する。
     * 次の試合開始時から反映される (進行中チームの現在チケットは変更しない)。
     * @return 設定後の値 (0未満は1に丸める)
     */
    public int setInitialTickets(int amount) {
        int value = Math.max(1, amount);
        config.ticketInitial = value;
        plugin.getConfig().set("ticket.initial", value);
        plugin.saveConfig();
        return value;
    }

    /**
     * 旗保持によるチケット減少の間隔 (何秒に1回減らすか) を設定し config.yml に永続化する。
     * 毎秒の試合ループが config の値を直接見るため、進行中でも即座に反映される。
     * @return 設定後の値 (1未満は1に丸める)
     */
    public int setFlagDrainInterval(int seconds) {
        int value = Math.max(1, seconds);
        config.flagDrainIntervalSeconds = value;
        plugin.getConfig().set("flag.ticketDrainIntervalSeconds", value);
        plugin.saveConfig();
        return value;
    }

    // ---- ポイント -----------------------------------------------------------

    /** 全オンラインプレイヤーに時間経過ポイントを加算する (タスクから呼ばれる)。 */
    public void tickPoints() {
        if (!running || config.pointAmountPerTick <= 0) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (getTeamOf(online) != null) {
                getPlayerData(online).addPoints(config.pointAmountPerTick);
            }
        }
    }

    // ---- チケット / 勝敗 ----------------------------------------------------

    /** キル時: 倒されたプレイヤーのチームのチケットを減らし、勝敗判定する。 */
    public void onDeath(Player victim) {
        if (!running) {
            return;
        }
        Team team = getTeamOf(victim);
        if (team != null) {
            team.loseTickets(config.ticketLossPerKill);
            checkVictory();
        }
    }

    /**
     * キル時の演出。被害者の死亡地点にパーティクルと効果音を出し、
     * キラーがいれば撃破フィードバック (アクションバー + 効果音) を送る。
     */
    public void playKillEffect(Player killer, Player victim) {
        World world = victim.getWorld();
        if (world == null) {
            return;
        }
        Location loc = victim.getLocation().add(0, 1.0, 0);
        world.spawnParticle(Particle.CRIT, loc, 30, 0.4, 0.6, 0.4, 0.2);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 12, 0.3, 0.5, 0.3, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 8, 0.2, 0.4, 0.2, 0.02);
        world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);

        if (killer != null && killer != victim) {
            killer.playSound(killer.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
            killer.sendActionBar(net.kyori.adventure.text.Component.text(
                    "☠ " + victim.getName() + " を撃破！",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            if (running) {
                getPlayerData(killer).addKill(); // その試合のキル数を集計 (Tab表示用)
            }
        }
    }

    /**
     * 旗保持によるチケット減少。各チームが所有する旗数に応じて、敵チーム全てから均等に減らす。
     * 旗が 0個ならこのメソッドは何もしない。
     */
    public void tickFlagDrain() {
        if (!running) {
            return;
        }
        // チームごとの所有旗数を集計
        Map<String, Integer> ownedCount = new LinkedHashMap<>();
        for (var flag : flagManager.all()) {
            String owner = flag.getOwnerTeamId();
            if (owner != null && getTeam(owner) != null) {
                ownedCount.merge(owner.toLowerCase(), 1, Integer::sum);
            }
        }
        if (ownedCount.isEmpty()) {
            return;
        }
        // 所有旗 N 個のチームは、自分以外の全チームから N * drainPerFlag を奪う
        for (Map.Entry<String, Integer> e : ownedCount.entrySet()) {
            int drain = e.getValue() * config.flagDrainPerFlag;
            if (drain <= 0) {
                continue;
            }
            for (Team enemy : teams.values()) {
                if (!enemy.getId().equalsIgnoreCase(e.getKey())) {
                    enemy.loseTickets(drain);
                }
            }
        }
        checkVictory();
    }

    /** いずれかのチームが全滅したら試合終了し勝者を発表する。 */
    public void checkVictory() {
        if (!running) {
            return;
        }
        List<Team> alive = new ArrayList<>();
        boolean anyEliminated = false;
        for (Team team : teams.values()) {
            if (team.isEliminated()) {
                anyEliminated = true;
            } else {
                alive.add(team);
            }
        }
        if (!anyEliminated) {
            return;
        }
        // 残存チームのうち最大チケットを勝者とする
        Team winner = null;
        for (Team team : alive) {
            if (winner == null || team.getTickets() > winner.getTickets()) {
                winner = team;
            }
        }
        endMatch(winner);
    }

    // ---- 試合制御 -----------------------------------------------------------

    public boolean startMatch() {
        if (running || teams.isEmpty()) {
            return false;
        }
        running = true;
        for (Team team : teams.values()) {
            team.setTickets(config.ticketInitial);
        }
        neutralizeAllFlags(); // 前の試合の所有者を引き継がず、全旗を中立に戻す
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (getTeamOf(online) == null) {
                autoAssign(online);
            }
            getPlayerData(online).resetForNewMatch();
            applyNameTag(online);
            Team team = getTeamOf(online);
            if (team != null && team.getSpawn() != null) {
                online.teleport(team.getSpawn());
            }
        }
        Bukkit.broadcastMessage(ChatColor.GOLD + "[KitTeamfight] 試合開始！敵チームのチケットをゼロにしろ！");
        // 装備配布は1秒(20tick)遅延させる。開始直後だと他処理のインベントリクリアと
        // レースして装備が消えることがあるため。
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) {
                return;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                equip(online);
                restoreVitals(online); // 試合開始時に満腹度と HP を全回復
            }
        }, 20L);
        return true;
    }

    public void endMatch(Team winner) {
        if (!running) {
            return;
        }
        running = false;
        removeAllHorses(); // 試合終了時に騎兵の馬を片付ける
        if (winner != null) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[KitTeamfight] 勝者: " + winner.getDisplayName() + " !");

            // 勝者/敗者ごとに画面中央へ大きくタイトル表示 + 効果音
            String winSubtitle = ChatColor.GRAY + winner.getDisplayName() + ChatColor.GRAY + " の勝利！";
            for (Player player : Bukkit.getOnlinePlayers()) {
                Team team = getTeamOf(player);
                if (team == null) {
                    continue; // 観戦/未所属にはタイトルを出さない
                }
                boolean won = team.getId().equalsIgnoreCase(winner.getId());
                if (won) {
                    // §6§l 金色 + 太字で「勝利」
                    player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "勝利",
                            winSubtitle, 10, 70, 20);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    // §c§l 赤色 + 太字で「敗北」
                    player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "敗北",
                            winSubtitle, 10, 70, 20);
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 1.0f);
                }
            }

            // 勝者チームの色を基調に、複数色の花火パレットを用意
            final java.util.List<Color> palette = new ArrayList<>();
            if (winner.getColor() == ChatColor.RED) {
                palette.add(Color.RED);
                palette.add(Color.ORANGE);
                palette.add(Color.YELLOW);
                palette.add(Color.WHITE);
            } else {
                palette.add(Color.BLUE);
                palette.add(Color.AQUA);
                palette.add(Color.fromRGB(0x00, 0xBF, 0xFF));
                palette.add(Color.WHITE);
            }
            final FireworkEffect.Type[] shapes = {
                    FireworkEffect.Type.BALL_LARGE,
                    FireworkEffect.Type.BALL,
                    FireworkEffect.Type.STAR,
                    FireworkEffect.Type.BURST
            };
            final java.util.Random rng = new java.util.Random();

            // 約4秒間 (0.4秒間隔 * 10回) にわたって花火を連射し、豪華に演出する
            new org.bukkit.scheduler.BukkitRunnable() {
                int count = 0;

                @Override
                public void run() {
                    if (count >= 10) {
                        cancel();
                        return;
                    }
                    count++;
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        World world = player.getWorld();
                        Location loc = player.getLocation().add(rng.nextDouble() * 4 - 2, 2,
                                rng.nextDouble() * 4 - 2);
                        Firework firework = world.spawn(loc, Firework.class);
                        FireworkMeta meta = firework.getFireworkMeta();

                        Color c1 = palette.get(rng.nextInt(palette.size()));
                        Color c2 = palette.get(rng.nextInt(palette.size()));
                        Color fade = palette.get(rng.nextInt(palette.size()));

                        FireworkEffect effect = FireworkEffect.builder()
                                .withColor(c1, c2)
                                .withFade(fade)
                                .with(shapes[rng.nextInt(shapes.length)])
                                .flicker(true)
                                .trail(true)
                                .build();

                        meta.addEffect(effect);
                        meta.setPower(1 + rng.nextInt(2)); // 1〜2 で飛ぶ高さを散らす
                        firework.setFireworkMeta(meta);
                    }
                }
            }.runTaskTimer(plugin, 0L, 8L);
        } else {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[KitTeamfight] 試合終了 (引き分け)");
        }
    }
}
