package errand;

import errand.economy.Enhancer;
import errand.economy.WeaponTable;
import errand.engine.GameState;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class EnhancerTest {

    /** nextInt(100)이 주어진 순서대로 나오는 가짜 난수. 0이면 무조건 성공, 99면 무조건 실패. */
    private static RandomGenerator rolls(int... seq) {
        int[] idx = {0};
        return new RandomGenerator() {
            @Override public long nextLong() { throw new UnsupportedOperationException(); }
            @Override public int nextInt(int bound) { return seq[idx[0]++]; }
        };
    }

    private static GameState state(int weapon, int soul) {
        GameState s = new GameState();
        s.addWeapon(weapon);
        s.addSoul(soul);
        return s;
    }

    @Test void 성공하면_단계가_오르고_혼력이_준다() {
        GameState s = state(0, 100);
        var r = new Enhancer(rolls(0)).attempt(s, false);

        assertEquals(Enhancer.Outcome.SUCCESS, r.outcome());
        assertEquals(1, s.weapon());
        assertEquals(80, s.soul());           // 100 - 20
        assertEquals(20, r.soulSpent());
        assertEquals(90, r.chancePercent());
    }

    @Test void 하락없는_구간의_실패는_한을_쌓지_않는다() {
        // +0~+2는 실패해도 유지된다. 여기서 한을 태우면 정작 필요한 +3 이상에서 방패가 없다.
        GameState s = state(0, 100);
        var r = new Enhancer(rolls(99)).attempt(s, false);

        assertEquals(Enhancer.Outcome.FAIL_HELD, r.outcome());
        assertEquals(0, s.weapon());
        assertEquals(0, s.han(), "하락이 없는 구간에서는 한이 쌓이면 안 됩니다");
        assertEquals(80, s.soul(), "실패해도 혼력은 소모됩니다");
    }

    @Test void 하락구간의_두번째_실패는_한이_막아준다() {
        GameState s = state(4, 500);
        Enhancer e = new Enhancer(rolls(99, 99));

        var first = e.attempt(s, false);      // +4 실패 → 한 1, +3으로 하락
        assertEquals(Enhancer.Outcome.FAIL_DROPPED, first.outcome());
        assertEquals(3, s.weapon());
        assertEquals(1, s.han());

        var second = e.attempt(s, false);     // +3 실패 → 한 2 도달, 하락 면제
        assertEquals(Enhancer.Outcome.FAIL_HELD, second.outcome());
        assertTrue(second.hanShielded());
        assertEquals(3, s.weapon(), "한이 하락을 막아야 합니다");
        assertEquals(0, s.han(), "한은 방패로 쓰이며 소멸합니다");
    }

    @Test void 부적은_성공률을_올리고_소모된다() {
        GameState s = state(2, 100);          // +2 기본 60%
        s.addCharms(1);
        var r = new Enhancer(rolls(65)).attempt(s, true);

        assertEquals(70, r.chancePercent(), "60 + 10 = 70이어야 합니다");
        assertEquals(Enhancer.Outcome.SUCCESS, r.outcome(), "65 < 70이므로 성공해야 합니다");
        assertTrue(r.charmUsed());
        assertEquals(0, s.charms());
    }

    @Test void 부적_보정에도_상한이_있다() {
        assertEquals(WeaponTable.CHARM_CAP_PERCENT, WeaponTable.successPercent(0, true),
                "+0은 기본 90%라 부적을 써도 90%를 넘지 않아야 합니다");
    }

    @Test void 혼력이_모자라면_시도조차_하지_않는다() {
        GameState s = state(0, 5);
        var r = new Enhancer(rolls()).attempt(s, false);   // 난수를 뽑으면 배열 초과로 터진다

        assertEquals(Enhancer.Outcome.NOT_ENOUGH_SOUL, r.outcome());
        assertFalse(r.attempted());
        assertEquals(5, s.soul(), "실패한 시도로 혼력이 줄면 안 됩니다");
        assertEquals(0, s.weapon());
    }

    @Test void 부적이_없으면_부적_강화는_막힌다() {
        GameState s = state(0, 100);
        var r = new Enhancer(rolls()).attempt(s, true);
        assertEquals(Enhancer.Outcome.NO_CHARM, r.outcome());
        assertEquals(100, s.soul());
    }

    @Test void 최대단계에서는_더_시도할_수_없다() {
        GameState s = state(WeaponTable.MAX_LEVEL, 1000);
        var r = new Enhancer(rolls()).attempt(s, false);
        assertEquals(Enhancer.Outcome.MAX_LEVEL, r.outcome());
        assertThrows(IllegalArgumentException.class, () -> WeaponTable.stepFrom(WeaponTable.MAX_LEVEL));
    }

    @Test void 단계는_0아래로_떨어지지_않는다() {
        GameState s = state(3, 1000);
        Enhancer e = new Enhancer(rolls(99, 99, 99, 99, 99, 99));
        for (int i = 0; i < 6; i++) e.attempt(s, false);
        assertTrue(s.weapon() >= 0);
    }
}
