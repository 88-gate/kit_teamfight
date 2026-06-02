package lobby.kit_teamfight.kit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登録された kit を管理する。新 kit は register するだけで追加できる。
 */
public class KitRegistry {

    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public void register(Kit kit) {
        kits.put(kit.getId().toLowerCase(), kit);
    }

    public Kit get(String id) {
        if (id == null) {
            return null;
        }
        return kits.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return id != null && kits.containsKey(id.toLowerCase());
    }

    /** 登録順を保持した全 kit。ショップ表示に使う。 */
    public Collection<Kit> all() {
        return kits.values();
    }
}
