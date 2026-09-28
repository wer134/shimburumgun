package errand.combat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 적 도감. GDD 6.2를 그대로 옮긴 것이며 이 파일이 단일 진실 공급원이다.
 *
 * <p>스토리 JSON은 id로만 참조한다. 적 수치는 밸런스 데이터이므로 확률표와 같은
 * 이유로 코드에 둔다 — 고치면 {@code CombatBalanceTest}가 깨져야 한다.
 */
public final class Bestiary {

    public static final String JAPGWI = "잡귀";
    public static final String GATEKEEPER = "수문장";

    private static final Map<String, Enemy> BY_ID = new LinkedHashMap<>();

    static {
        put(new Enemy(JAPGWI, "잡귀", 40, 12, 12, 0));
        put(new Enemy(GATEKEEPER, "저승 수문장", 75, 16, 28, 3));
    }

    private Bestiary() {}

    private static void put(Enemy e) { BY_ID.put(e.id(), e); }

    public static boolean has(String id) { return BY_ID.containsKey(id); }

    public static Enemy require(String id) {
        Enemy e = BY_ID.get(id);
        if (e == null) {
            throw new IllegalArgumentException(
                    "도감에 없는 적: '%s' (가능: %s)".formatted(id, String.join(", ", BY_ID.keySet())));
        }
        return e;
    }

    public static Map<String, Enemy> all() { return Map.copyOf(BY_ID); }
}
