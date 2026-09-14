package errand.economy;

import java.util.List;

/**
 * 저승낫 강화표. GDD 5.1을 그대로 옮긴 것이며 <b>이 파일이 단일 진실 공급원</b>이다.
 *
 * <p>1판에는 {@code 비용 = 현재 단계 × 10}이라는 공식이 있었으나 +0에서 0원이 되어
 * 표와 모순됐다. 곡선이 선형이 아니므로 공식화하지 않고 표로만 둔다.
 *
 * <p>수치를 고치면 {@code BalanceRegressionTest}가 깨진다. 그것이 의도다 —
 * 확률표를 손대면 히든 엔딩 도달률이 설계 범위를 벗어나는지 자동으로 확인된다.
 */
public final class WeaponTable {

    /** 강화 한 단계. */
    public record Step(int from, int successPercent, int soulCost, int failDrop) {
        public int to() { return from + 1; }
    }

    /** 단계별 정체성. GDD 5.1의 이름과 해금 효과. */
    public record Tier(int level, String name, String unlock) {}

    public static final int MAX_LEVEL = 5;

    /** 부적 조각 보정치와 그 상한. 상한이 있어 +0(90%)에서는 효과가 없다. */
    public static final int CHARM_BONUS_PERCENT = 10;
    public static final int CHARM_CAP_PERCENT = 90;

    /** 한이 이만큼 쌓이면 단계 하락을 한 번 막고 0으로 돌아간다. */
    public static final int HAN_SHIELD_AT = 2;

    private static final List<Step> STEPS = List.of(
            new Step(0, 90,  20, 0),
            new Step(1, 75,  35, 0),
            new Step(2, 60,  55, 0),
            new Step(3, 45,  75, 1),
            new Step(4, 30, 100, 1)
    );

    private static final List<Tier> TIERS = List.of(
            new Tier(0, "빌린 낫",           "—"),
            new Tier(1, "손에 익은 낫",       "—"),
            new Tier(2, "혼 감지",           "숨겨진 선택지 공개"),
            new Tier(3, "홍사줄이 감긴 낫",   "전투 선공 확정"),
            new Tier(4, "차사의 눈",         "업경대 장면 1회 재열람"),
            new Tier(5, "강림의 낫",         "히든 엔딩 자격")
    );

    private WeaponTable() {}

    public static boolean isMax(int level) { return level >= MAX_LEVEL; }

    /** {@code level}에서 다음 단계로 가는 강화 정보. 최대치면 예외. */
    public static Step stepFrom(int level) {
        if (isMax(level)) {
            throw new IllegalArgumentException("이미 최대 단계(+%d)입니다.".formatted(MAX_LEVEL));
        }
        return STEPS.get(level);
    }

    public static Tier tier(int level) { return TIERS.get(level); }

    public static List<Step> steps() { return STEPS; }

    public static List<Tier> tiers() { return TIERS; }

    /** 부적 조각을 쓸 때의 실제 성공률. 상한 적용 결과를 돌려준다. */
    public static int successPercent(int level, boolean useCharm) {
        int base = stepFrom(level).successPercent();
        if (!useCharm) return base;
        return Math.min(CHARM_CAP_PERCENT, base + CHARM_BONUS_PERCENT);
    }
}
