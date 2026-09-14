package errand.engine;

import java.util.*;

/**
 * 플레이어의 모든 진행 상태. 엔진 밖에서는 이 객체만 보면 된다.
 *
 * <p>수치 범위는 GDD 3장을 따른다. 공덕은 0~100, 체력은 0~100, 낫은 +0~+5.
 * 범위를 벗어나는 값은 조용히 잘라낸다(clamp) — 스토리 작가가 효과값을 크게
 * 적어도 게임이 깨지지 않게 하기 위함이다.
 */
public final class GameState {

    public static final int KARMA_START = 50;
    public static final int KARMA_MIN = 0;
    public static final int KARMA_MAX = 100;
    public static final int HP_MAX = 100;
    public static final int WEAPON_MAX = 5;

    private int karma = KARMA_START;
    private int soul = 0;
    private int hp = HP_MAX;
    private int weapon = 0;
    private int han = 0;
    private int charms = 0;

    private final Set<String> flags = new HashSet<>();
    private final Map<String, Integer> counters = new HashMap<>();
    private final Set<String> visited = new LinkedHashSet<>();

    public int karma()  { return karma; }
    public int soul()   { return soul; }
    public int hp()     { return hp; }
    public int weapon() { return weapon; }
    public int han()    { return han; }
    public int charms() { return charms; }

    public int stat(Stat s) {
        return switch (s) {
            case KARMA  -> karma;
            case SOUL   -> soul;
            case HP     -> hp;
            case WEAPON -> weapon;
            case HAN    -> han;
            case CHARM  -> charms;
        };
    }

    public void addKarma(int d)  { karma = clamp(karma + d, KARMA_MIN, KARMA_MAX); }
    public void addSoul(int d)   { soul = Math.max(0, soul + d); }
    public void addHp(int d)     { hp = clamp(hp + d, 0, HP_MAX); }
    public void addWeapon(int d) { weapon = clamp(weapon + d, 0, WEAPON_MAX); }
    public void addHan(int d)    { han = Math.max(0, han + d); }
    public void addCharms(int d) { charms = Math.max(0, charms + d); }
    public void setHan(int v)    { han = Math.max(0, v); }

    public boolean flag(String name)          { return flags.contains(name); }
    public void setFlag(String name, boolean v) { if (v) flags.add(name); else flags.remove(name); }
    public Set<String> flags()                { return Collections.unmodifiableSet(flags); }

    public int counter(String name)           { return counters.getOrDefault(name, 0); }
    public void addCounter(String name, int d) { counters.merge(name, d, Integer::sum); }
    public Map<String, Integer> counters()    { return Collections.unmodifiableMap(counters); }

    public boolean visited(String storyletId)  { return visited.contains(storyletId); }
    public void markVisited(String storyletId) { visited.add(storyletId); }
    public Set<String> visitedIds()            { return Collections.unmodifiableSet(visited); }

    /** 회피율(%). GDD 6.3 — 공덕 ÷ 5, 상한 20%. */
    public int evasionPercent() { return Math.min(20, karma / 5); }

    /** 저승낫 데미지. GDD 5.1 — 기본 10에 단계당 +3. */
    public int weaponDamage() { return 10 + weapon * 3; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    @Override public String toString() {
        return "공덕 %d · 혼력 %d · 체력 %d · 낫 +%d · 한 %d · 부적 %d"
                .formatted(karma, soul, hp, weapon, han, charms);
    }
}
