package lobby.kit_teamfight.flag;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 旗の生成・削除・永続化を担う。旗座標は config.yml ではなく flags.yml に保存する
 * (コマンドで動的に設置/撤去されるため)。
 */
public class FlagManager {

    private final Plugin plugin;
    private final File file;
    private final Map<String, Flag> flags = new LinkedHashMap<>();

    public FlagManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "flags.yml");
    }

    public Flag create(String id, Location location, String ownerTeamId) {
        Flag flag = new Flag(id.toLowerCase(), location.clone(), ownerTeamId);
        flags.put(flag.getId(), flag);
        save();
        return flag;
    }

    public Flag remove(String id) {
        Flag removed = flags.remove(id.toLowerCase());
        if (removed != null) {
            save();
        }
        return removed;
    }

    public Flag get(String id) {
        return id == null ? null : flags.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return id != null && flags.containsKey(id.toLowerCase());
    }

    public Collection<Flag> all() {
        return flags.values();
    }

    public int count() {
        return flags.size();
    }

    /** 全旗のキャプチャ進捗をリセットする (試合開始時など)。 */
    public void resetAllCaptures() {
        for (Flag flag : flags.values()) {
            flag.resetCapture();
        }
    }

    // ---- 永続化 -------------------------------------------------------------

    public void load() {
        flags.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("flags");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String worldName = s.getString("world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("旗 '" + id + "' のワールド '" + worldName + "' が見つからないためスキップします。");
                continue;
            }
            Location loc = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
            // サーバ再起動時は試合状況をリセットする方針なので、旗は所有者を引き継がず
            // 全て中立 (owner = null) で読み込む。位置だけが永続化される。
            flags.put(id.toLowerCase(), new Flag(id.toLowerCase(), loc, null));
        }
        plugin.getLogger().info(flags.size() + " 個の旗を読み込みました。");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Flag flag : flags.values()) {
            String base = "flags." + flag.getId();
            Location loc = flag.getLocation();
            cfg.set(base + ".world", loc.getWorld() == null ? null : loc.getWorld().getName());
            cfg.set(base + ".x", loc.getX());
            cfg.set(base + ".y", loc.getY());
            cfg.set(base + ".z", loc.getZ());
            cfg.set(base + ".owner", flag.getOwnerTeamId() == null ? "none" : flag.getOwnerTeamId());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "flags.yml の保存に失敗しました", e);
        }
    }
}
