package errand.engine;

/** 조건식과 효과에서 참조 가능한 수치. JSON에서는 소문자 이름으로 쓴다. */
public enum Stat {
    KARMA("karma"), SOUL("soul"), HP("hp"), WEAPON("weapon"), HAN("han");

    private final String json;

    Stat(String json) { this.json = json; }

    public String json() { return json; }

    public static Stat fromJson(String s) {
        for (Stat v : values()) if (v.json.equals(s)) return v;
        throw new IllegalArgumentException(
                "알 수 없는 스탯: '" + s + "' (가능: karma, soul, hp, weapon, han)");
    }
}
