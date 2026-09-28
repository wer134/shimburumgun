package errand;

import errand.combat.Battle;
import errand.combat.Bestiary;
import errand.combat.CombatAction;
import errand.combat.Enemy;
import errand.engine.GameState;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 전투 난이도 검산. GDD 6.4를 코드로 다시 세운 것이다.
 *
 * <p>문서의 6.4 표는 자체 모순이 있었다. "+3이면 4턴에 누적 피해 76"은 적이 네 번
 * 때린다는 뜻인데, 그건 적이 먼저 움직인다는 전제다. 그런데 +3은 "선공 확정"
 * 특전을 가진 단계다. 둘은 같이 성립할 수 없다.
 *
 * <p>여기서는 실제 구현으로 돌려 수치를 다시 뽑고, 문서가 의도한 <b>설계 목표</b>만
 * 검사한다 — "+3 루트는 아슬아슬하게 살고, 저강화 루트는 혼력을 생존에 쓰지 않으면
 * 죽는다".
 */
class CombatBalanceTest {

    /** 회피와 선공 판정을 모두 실패시키는 난수. 최악의 경우를 본다. */
    private static final RandomGenerator PESSIMISTIC = new RandomGenerator() {
        @Override public long nextLong() { throw new UnsupportedOperationException(); }
        @Override public int nextInt(int bound) { return bound - 1; }
    };

    private record Fight(int turns, int damageTaken, int hpLeft, boolean won, boolean playerFirst) {}

    /** 항상 벤다. 회피를 배제하려면 공덕 0인 상태를 넘길 것. */
    private static Fight fight(Enemy enemy, GameState s, RandomGenerator rng) {
        int hpBefore = s.hp();
        Battle b = new Battle(enemy, s, rng);
        boolean first = b.playerFirst();
        while (!b.finished()) b.take(CombatAction.STRIKE);
        return new Fight(b.round(), hpBefore - s.hp(), s.hp(),
                b.outcome() == Battle.Outcome.VICTORY, first);
    }

    private static GameState atWeapon(int weapon) {
        GameState s = new GameState();
        s.addKarma(-100);                                    // 회피 0%로 고정
        s.addWeapon(weapon);
        return s;
    }

    /** 1장 잡귀 → 장 전환 회복 +30 → 2장 수문장. GDD 6.4가 상정한 경로. */
    private static Fight chapterTwoRun(int weapon) {
        GameState s = atWeapon(weapon);
        Fight first = fight(Bestiary.require(Bestiary.JAPGWI), s, PESSIMISTIC);
        assertTrue(first.won(), "1장 잡귀에서 죽으면 검산이 성립하지 않습니다 (낫 +%d)".formatted(weapon));
        s.addHp(30);                                         // GDD 3장: 장 시작 시 +30
        return fight(Bestiary.require(Bestiary.GATEKEEPER), s, PESSIMISTIC);
    }

    @Test void 난이도_검산표를_출력한다() {
        System.out.println("── 전투 난이도 검산 (항상 벤다 · 회피 0% · 선공 판정 실패 가정) ──");
        System.out.println("   1장 잡귀");
        for (int w = 0; w <= 5; w++) {
            Fight f = fight(Bestiary.require(Bestiary.JAPGWI), atWeapon(w), PESSIMISTIC);
            System.out.printf("   낫 +%d (%2d딜)  %d턴  피해 %2d  체력 %3d %s%s%n",
                    w, 10 + w * 3, f.turns(), f.damageTaken(), f.hpLeft(),
                    f.won() ? "생존" : "사망", f.playerFirst() ? "  선공" : "");
        }
        System.out.println("   2장 수문장 (1장 전투 후 +30 회복하고 진입)");
        for (int w = 0; w <= 5; w++) {
            Fight f = chapterTwoRun(w);
            System.out.printf("   낫 +%d (%2d딜)  %d턴  피해 %2d  체력 %3d %s%s%n",
                    w, 10 + w * 3, f.turns(), f.damageTaken(), f.hpLeft(),
                    f.won() ? "생존" : "사망", f.playerFirst() ? "  선공" : "");
        }
    }

    @Test void 낫_3단계_루트는_수문장을_아슬아슬하게_넘긴다() {
        Fight f = chapterTwoRun(3);
        assertTrue(f.won(), "+3에서 수문장을 못 넘기면 너무 어렵습니다");
        assertTrue(f.hpLeft() > 0 && f.hpLeft() <= 40,
                "+3 루트는 아슬아슬해야 합니다. 남은 체력: %d".formatted(f.hpLeft()));
    }

    @Test void 저강화_루트는_그냥_베기만으로는_수문장을_못_넘긴다() {
        // GDD 6.4의 설계 의도: 혼력을 생존(숨돌이)이나 화력(혼 태우기)에 써야 한다.
        Fight f = chapterTwoRun(1);
        assertFalse(f.won(), "+1에서 아무 자원도 안 쓰고 이기면 경제 경쟁이 성립하지 않습니다");
    }

    @Test void 혼을_태우면_저강화_루트도_수문장을_넘길_수_있다() {
        GameState s = atWeapon(1);
        Fight first = fight(Bestiary.require(Bestiary.JAPGWI), s, PESSIMISTIC);
        assertTrue(first.won());
        s.addHp(30);
        s.addSoul(200);                                      // 혼력을 화력에 붓는다

        Battle b = new Battle(Bestiary.require(Bestiary.GATEKEEPER), s, PESSIMISTIC);
        while (!b.finished()) {
            b.take(b.availableActions().contains(CombatAction.BURN_SOUL)
                    ? CombatAction.BURN_SOUL
                    : CombatAction.STRIKE);
        }
        assertEquals(Battle.Outcome.VICTORY, b.outcome(),
                "혼력을 쏟아부어도 못 이기면 저강화 루트에 출구가 없습니다");
        assertTrue(s.counter(Battle.BURN_COUNTER) >= 3,
                "이 루트로 이기려면 혼을 3번 이상 태워야 하고, 그러면 3장에서 폭력 지옥이 열린다");
    }

    /**
     * 혼 태우기가 체력 보존 수단으로서 숨돌이보다 비싼지 본다.
     *
     * <p>플레이테스트에서 혼 태우기를 기본 공격처럼 쓰는 모습이 나왔다. 그래서
     * 실제 단가를 재 둔다 — 혼 태우기가 숨돌이보다 <b>싸면</b> 상점의 체력 항목이
     * 죽고, 태우는 것이 항상 정답이 된다.
     *
     * <p>혼 태우기의 값어치는 체력 효율이 아니라 <b>턴 단축</b>이어야 한다.
     * 수문장의 3번째 공격마다 오는 강공격(28)을 건너뛰는 용도다.
     */
    @Test void 혼_태우기는_체력_보존_수단으로는_숨돌이보다_비싸다() {
        Enemy japgwi = Bestiary.require(Bestiary.JAPGWI);

        GameState striker = atWeapon(1);
        Fight byStriking = fight(japgwi, striker, PESSIMISTIC);

        GameState burner = atWeapon(1);
        burner.addSoul(200);
        int soulBefore = burner.soul();
        Battle b = new Battle(japgwi, burner, PESSIMISTIC);
        while (!b.finished()) b.take(CombatAction.BURN_SOUL);

        int hpSaved = byStriking.damageTaken() - (100 - burner.hp());
        int soulSpent = soulBefore - burner.soul();
        int burns = burner.counter(Battle.BURN_COUNTER);

        System.out.println("── 혼 태우기 단가 (1장 잡귀 · 낫 +1) ──");
        System.out.printf("   계속 벤다   %d턴, 피해 %d%n", byStriking.turns(), byStriking.damageTaken());
        System.out.printf("   계속 태운다 %d턴, 피해 %d  (%d회 태움)%n",
                b.round(), 100 - burner.hp(), burns);
        System.out.printf("   차익: 체력 %d 절약에 혼력 %d + 공덕 %d%n",
                hpSaved, soulSpent, burns * Battle.BURN_SOUL_KARMA);
        System.out.printf("   비교: 숨돌이는 혼력 %d에 체력 %d%n",
                errand.economy.Shop.SUMDOLI_COST, errand.economy.Shop.SUMDOLI_HP);

        double burnPerHp = (double) soulSpent / hpSaved;
        double shopPerHp = (double) errand.economy.Shop.SUMDOLI_COST / errand.economy.Shop.SUMDOLI_HP;
        System.out.printf("   체력당 혼력: 태우기 %.2f  vs  숨돌이 %.2f%n", burnPerHp, shopPerHp);

        assertTrue(burnPerHp > shopPerHp,
                ("혼 태우기가 숨돌이보다 체력당 싸면(%.2f vs %.2f) 상점의 체력 항목이 죽고 "
                        + "태우는 것이 항상 정답이 됩니다.").formatted(burnPerHp, shopPerHp));
    }

    /**
     * 공덕이 0이면 혼 태우기의 도덕적 대가가 사라진다.
     *
     * <p>공덕은 0~100으로 잘리므로 바닥에 닿은 플레이어는 공짜로 태울 수 있다.
     * 이미 엔딩 C 확정 구간이라 실전 영향은 작지만, 규칙으로 못 박아 둔다 —
     * 나중에 하한을 음수로 바꾸거나 다른 대가를 붙일 때 이 테스트가 신호가 된다.
     */
    @Test void 공덕이_남아_있으면_태울_때마다_깎인다() {
        GameState s = atWeapon(1);
        s.addKarma(50);                                      // 공덕 50으로 복구
        s.addSoul(200);
        Battle b = new Battle(Bestiary.require(Bestiary.JAPGWI), s, PESSIMISTIC);
        b.take(CombatAction.BURN_SOUL);

        assertEquals(50 - Battle.BURN_SOUL_KARMA, s.karma());
        assertEquals(1, s.counter(Battle.BURN_COUNTER));
    }

    @Test void 공덕_바닥에서는_태워도_더_깎이지_않는다() {
        GameState s = atWeapon(1);                           // 공덕 0
        s.addSoul(200);
        assertEquals(0, s.karma());

        Battle b = new Battle(Bestiary.require(Bestiary.JAPGWI), s, PESSIMISTIC);
        b.take(CombatAction.BURN_SOUL);

        assertEquals(0, s.karma(), "하한에 닿으면 더 깎이지 않는다 (의도된 클램프)");
        assertEquals(1, s.counter(Battle.BURN_COUNTER),
                "공덕이 안 깎여도 태운 횟수는 기록돼야 한다 — 폭력 지옥 판정은 카운터로 한다");
    }

    @Test void 첫_전투는_어떤_강화_단계에서도_넘길_수_있다() {
        for (int w = 0; w <= 5; w++) {
            Fight f = fight(Bestiary.require(Bestiary.JAPGWI), atWeapon(w), PESSIMISTIC);
            assertTrue(f.won(), "낫 +%d로 1장 잡귀를 못 넘기면 첫 전투가 너무 어렵습니다".formatted(w));
        }
    }
}
