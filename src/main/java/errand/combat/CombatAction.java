package errand.combat;

/** 플레이어가 한 턴에 할 수 있는 일. GDD 6.1. */
public enum CombatAction {
    /** 벤다 — 낫 데미지 그대로. */
    STRIKE("벤다", "낫 데미지 그대로"),

    /**
     * 버틴다 — 이번 턴 받는 피해를 절반으로 줄이고, 다음 공격의 데미지를 1.5배로.
     * 강공격이 오는 턴을 읽고 쓰는 것이 정석이다.
     */
    BRACE("버틴다", "이번 턴 피해 절반, 다음 공격 ×1.5"),

    /**
     * 혼을 태운다 — 데미지 2배. 혼력을 쓰고 공덕을 깎는다.
     * 전투를 경제와 도덕에 동시에 묶는 행동이다.
     */
    BURN_SOUL("혼을 태운다", "데미지 ×2 · 혼력 %d · 공덕 %d".formatted(Battle.BURN_SOUL_COST, -Battle.BURN_SOUL_KARMA));

    private final String label;
    private final String detail;

    CombatAction(String label, String detail) {
        this.label = label;
        this.detail = detail;
    }

    public String label()  { return label; }
    public String detail() { return detail; }
}
