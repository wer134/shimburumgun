package errand;

import errand.combat.Battle;
import errand.combat.Bestiary;
import errand.combat.CombatAction;
import errand.combat.Enemy;
import errand.engine.GameState;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class BattleTest {

    /** nextInt(bound)가 항상 같은 값을 주는 난수. 99면 회피도 선공도 실패한다. */
    private static RandomGenerator fixed(int value) {
        return new RandomGenerator() {
            @Override public long nextLong() { throw new UnsupportedOperationException(); }
            @Override public int nextInt(int bound) { return value; }
        };
    }

    /** 공덕 0으로 만들어 회피를 배제한다. 데미지 계산만 보고 싶을 때. */
    private static GameState noEvasion(int weapon, int soul) {
        GameState s = new GameState();
        s.addKarma(-100);
        s.addWeapon(weapon);
        s.addSoul(soul);
        return s;
    }

    /** 허수아비. 반격하지 않으므로 플레이어 행동만 관찰할 수 있다. */
    private static Enemy dummy(int hp) { return new Enemy("허수아비", "허수아비", hp, 0, 0, 0); }

    @Test void 벤_피해는_낫_데미지와_같다() {
        GameState s = noEvasion(2, 0);                       // 낫 +2 → 16
        Battle b = new Battle(dummy(100), s, fixed(99));

        var log = b.take(CombatAction.STRIKE);
        assertEquals(16, log.damageDealt());
        assertEquals(84, b.enemyHp());
    }

    @Test void 버틴_다음_공격은_1점5배다() {
        GameState s = noEvasion(0, 0);                       // 낫 +0 → 10
        Battle b = new Battle(dummy(100), s, fixed(99));

        var braced = b.take(CombatAction.BRACE);
        assertEquals(0, braced.damageDealt());
        assertTrue(b.braceBonusReady());

        var hit = b.take(CombatAction.STRIKE);
        assertEquals(15, hit.damageDealt(), "10 × 1.5 = 15");
        assertTrue(hit.braceBonusUsed());
        assertFalse(b.braceBonusReady(), "보너스는 한 번 쓰면 사라집니다");
    }

    @Test void 연속으로_버텨도_보너스가_사라지지는_않는다() {
        GameState s = noEvasion(0, 0);
        Battle b = new Battle(dummy(100), s, fixed(99));

        b.take(CombatAction.BRACE);
        b.take(CombatAction.BRACE);                          // 보너스를 낭비하지 않는다
        assertTrue(b.braceBonusReady());
        assertEquals(15, b.take(CombatAction.STRIKE).damageDealt(), "보너스는 중첩되지 않고 유지됩니다");
    }

    @Test void 혼을_태우면_데미지가_두배이고_혼력과_공덕을_쓴다() {
        GameState s = noEvasion(1, 50);                      // 낫 +1 → 13, 공덕 0
        s.addKarma(50);                                      // 공덕 50으로 되돌려 차감을 관찰
        Battle b = new Battle(dummy(100), s, fixed(99));

        var log = b.take(CombatAction.BURN_SOUL);
        assertEquals(26, log.damageDealt(), "13 × 2 = 26");
        assertEquals(35, s.soul(), "혼력 15 소모");
        assertEquals(48, s.karma(), "공덕 2 차감");
        assertEquals(1, s.counter(Battle.BURN_COUNTER), "폭력 지옥 판정용 카운터가 올라야 합니다");
    }

    @Test void 혼력이_모자라면_혼_태우기가_빠진다() {
        GameState s = noEvasion(0, 10);                      // 15 미만
        Battle b = new Battle(dummy(100), s, fixed(99));

        assertFalse(b.availableActions().contains(CombatAction.BURN_SOUL));
        assertThrows(IllegalArgumentException.class, () -> b.take(CombatAction.BURN_SOUL));
    }

    @Test void 버티면_받는_피해가_절반이다() {
        GameState s = noEvasion(0, 0);
        Enemy hitter = new Enemy("때리기", "때리기", 1000, 12, 12, 0);
        Battle b = new Battle(hitter, s, fixed(99));         // 선공 실패 → 적이 먼저

        var log = b.take(CombatAction.BRACE);
        assertEquals(6, log.damageTaken(), "12의 절반");
        assertEquals(94, s.hp());
    }

    @Test void 회피하면_피해가_없다() {
        GameState s = new GameState();                       // 공덕 50 → 회피 10%
        s.addWeapon(0);
        assertEquals(10, s.evasionPercent());

        Enemy hitter = new Enemy("때리기", "때리기", 1000, 12, 12, 0);
        Battle b = new Battle(hitter, s, fixed(5));          // 5 < 10 → 항상 회피

        var log = b.take(CombatAction.STRIKE);
        assertTrue(log.evaded());
        assertEquals(0, log.damageTaken());
        assertEquals(100, s.hp());
    }

    @Test void 낫_3단계_이상이면_선공이_확정된다() {
        // 난수를 99로 고정하면 선공 판정은 실패해야 하는데, +3이면 무시된다.
        assertTrue(new Battle(dummy(10), noEvasion(Battle.INITIATIVE_AT_WEAPON, 0), fixed(99)).playerFirst());
        assertFalse(new Battle(dummy(10), noEvasion(Battle.INITIATIVE_AT_WEAPON - 1, 0), fixed(99)).playerFirst());
    }

    @Test void 강공격은_적의_공격_횟수_기준이다() {
        Enemy gatekeeper = Bestiary.require(Bestiary.GATEKEEPER);
        assertFalse(gatekeeper.isHeavy(1));
        assertFalse(gatekeeper.isHeavy(2));
        assertTrue(gatekeeper.isHeavy(3), "3번째 공격이 강공격");
        assertTrue(gatekeeper.isHeavy(6));
        assertEquals(28, gatekeeper.damageOf(3));
        assertEquals(16, gatekeeper.damageOf(4));
    }

    @Test void 강공격이_없는_적은_평타만_쓴다() {
        Enemy japgwi = Bestiary.require(Bestiary.JAPGWI);
        assertEquals(0, japgwi.heavyEvery());
        for (int i = 1; i <= 10; i++) assertFalse(japgwi.isHeavy(i));
    }

    @Test void 적을_쓰러뜨리면_승리로_끝난다() {
        GameState s = noEvasion(0, 0);
        Battle b = new Battle(dummy(10), s, fixed(99));

        var log = b.take(CombatAction.STRIKE);
        assertEquals(Battle.Outcome.VICTORY, log.outcome());
        assertTrue(b.finished());
        assertEquals(0, b.enemyHp());
        assertThrows(IllegalStateException.class, () -> b.take(CombatAction.STRIKE));
    }

    @Test void 체력이_다하면_패배로_끝난다() {
        GameState s = noEvasion(0, 0);
        s.addHp(-95);                                        // 체력 5
        Enemy hitter = new Enemy("때리기", "때리기", 1000, 12, 12, 0);
        Battle b = new Battle(hitter, s, fixed(99));

        var log = b.take(CombatAction.STRIKE);
        assertEquals(Battle.Outcome.DEFEAT, log.outcome());
        assertEquals(0, s.hp());
    }

    @Test void 죽이는_턴에는_적이_반격하지_않는다() {
        GameState s = noEvasion(3, 0);                       // +3 → 선공 확정, 19딜
        Battle b = new Battle(new Enemy("약골", "약골", 19, 50, 50, 0), s, fixed(99));

        var log = b.take(CombatAction.STRIKE);
        assertEquals(Battle.Outcome.VICTORY, log.outcome());
        assertFalse(log.enemyActed(), "선공으로 처치했으면 반격이 없어야 합니다");
        assertEquals(100, s.hp());
    }
}
