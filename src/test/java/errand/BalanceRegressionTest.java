package errand;

import errand.economy.Enhancer;
import errand.economy.WeaponTable;
import errand.engine.GameState;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 강화 확률표 밸런스 회귀 테스트. GDD 10.3.
 *
 * <p>이 게임에는 파밍 구간이 없다. 혼력 총 지급량이 정해져 있으므로 확률표를
 * 손대면 곧바로 "히든 엔딩에 닿을 수 있는가"가 흔들린다. 그 판정을 사람 눈이
 * 아니라 테스트가 하게 한다.
 *
 * <p>기준: <b>기대 예산 380 혼력을 전부 강화에 쏟았을 때 +5 도달률이 5~15%</b>.
 * 이 범위를 벗어나면 히든 엔딩이 너무 흔하거나 사실상 닫힌다.
 */
class BalanceRegressionTest {

    private static final int TRIALS = 60_000;
    private static final long SEED = 20260914L;

    /** GDD 4.1 기대 지급 총량. */
    private static final int EXPECTED_BUDGET = 380;

    /** 예산을 전부 강화에 쓰는 플레이 1회. 최종 도달 단계를 돌려준다. */
    private static int playAllIn(RandomGenerator rng, int budget) {
        GameState s = new GameState();
        s.addSoul(budget);
        Enhancer enhancer = new Enhancer(rng);
        while (enhancer.check(s, false) == Enhancer.Outcome.SUCCESS) {
            enhancer.attempt(s, false);
        }
        return s.weapon();
    }

    private static int[] distribution(int budget) {
        // java.util.Random은 Java 17부터 RandomGenerator다. 시드를 고정해 재현 가능하게 둔다.
        RandomGenerator rng = new java.util.Random(SEED + budget);
        int[] counts = new int[WeaponTable.MAX_LEVEL + 1];
        for (int i = 0; i < TRIALS; i++) counts[playAllIn(rng, budget)]++;
        return counts;
    }

    private static double percent(int[] counts, int level) {
        return counts[level] * 100.0 / TRIALS;
    }

    @Test void 기대예산_올인시_최대강화_도달률이_설계범위에_있다() {
        int[] d = distribution(EXPECTED_BUDGET);
        double maxRate = percent(d, WeaponTable.MAX_LEVEL);

        System.out.printf("예산 %d 올인 분포: ", EXPECTED_BUDGET);
        for (int i = 0; i <= WeaponTable.MAX_LEVEL; i++) System.out.printf("+%d %.1f%%  ", i, percent(d, i));
        System.out.printf("(평균 +%.2f)%n", average(d));

        assertTrue(maxRate >= 5.0 && maxRate <= 15.0,
                "+%d 도달률이 설계 범위(5~15%%)를 벗어났습니다: %.1f%%. 확률표를 고쳤다면 GDD 4.3의 표도 갱신하세요."
                        .formatted(WeaponTable.MAX_LEVEL, maxRate));
    }

    /**
     * 예산별 분포를 출력한다. GDD 4.3 표의 출처가 이 테스트다 —
     * 확률표를 고쳤다면 여기 출력을 그대로 문서에 옮기면 된다.
     */
    @Test void 예산이_늘수록_도달률도_단조증가한다() {
        System.out.println("── 강화 배분 예산별 도달 분포 (GDD 4.3) ──");
        double prev = -1;
        for (int budget : new int[]{150, 250, 300, 380, 450}) {
            int[] d = distribution(budget);
            double avg = average(d);
            StringBuilder row = new StringBuilder("예산 %3d → 평균 +%.2f   ".formatted(budget, avg));
            for (int i = 0; i <= WeaponTable.MAX_LEVEL; i++) {
                row.append("+%d %4.1f%%  ".formatted(i, percent(d, i)));
            }
            System.out.println(row);
            assertTrue(avg > prev, "예산 %d에서 평균 도달 단계가 줄었습니다: %.2f".formatted(budget, avg));
            prev = avg;
        }
    }

    @Test void 강화에_절반만_쓰면_최대강화는_사실상_닫힌다() {
        // GDD 4.2: 혼력은 강화·천도·생존이 나눠 쓴다. 공덕을 챙기면 히든은 포기해야 한다.
        int[] d = distribution(EXPECTED_BUDGET / 2);
        assertTrue(percent(d, WeaponTable.MAX_LEVEL) < 5.0,
                "예산 절반으로도 최대 강화가 자주 나오면 트레이드오프가 성립하지 않습니다: %.1f%%"
                        .formatted(percent(d, WeaponTable.MAX_LEVEL)));
    }

    @Test void 누적비용이_표와_일치한다() {
        int total = WeaponTable.steps().stream().mapToInt(WeaponTable.Step::soulCost).sum();
        assertEquals(285, total, "GDD 5.1 비용 합계(20+35+55+75+100)와 다릅니다");
        assertTrue(total < EXPECTED_BUDGET,
                "운이 완벽해도 최대 강화가 불가능하면 표가 잘못된 것입니다");
    }

    private static double average(int[] counts) {
        long sum = 0, n = 0;
        for (int i = 0; i < counts.length; i++) { sum += (long) i * counts[i]; n += counts[i]; }
        return (double) sum / n;
    }
}
