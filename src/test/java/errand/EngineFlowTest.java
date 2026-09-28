package errand;

import errand.combat.Battle;
import errand.combat.Bestiary;
import errand.combat.CombatAction;
import errand.engine.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 스토리 JSON을 따라 끝까지 진행되는지, 분기가 갈리는지 본다. */
class EngineFlowTest {

    private static StoryEngine engine(GameState s) {
        return new StoryEngine(StoryRepository.loadDefault(Bestiary.all().keySet()), s);
    }

    /** 선택지 문구에 특정 단어가 든 것을 고른다. 없으면 테스트 실패. */
    private static void pick(StoryEngine e, String keyword) {
        List<Choice> cs = e.availableChoices();
        for (Choice c : cs) {
            if (c.text().contains(keyword) || c.id().equals(keyword)) { e.choose(c); return; }
        }
        fail("'%s'에 '%s' 선택지가 없습니다. 있는 것: %s"
                .formatted(e.current().id(), keyword, cs.stream().map(Choice::id).toList()));
    }

    /**
     * 자동 진행 구간과 전투를 넘겨 다음 선택지(또는 엔딩)까지 진행한다.
     *
     * <p>Main이 하는 일을 테스트에서 축약한 것이다. 상점 스토리렛은 선택지가 없으므로
     * 아무것도 사지 않고 지나간다.
     */
    private static void runToStop(StoryEngine e, GameState s) {
        while (!e.isFinished()) {
            if (e.current().isBattle()) { fightAlwaysStriking(e, s); continue; }
            if (e.isAutoAdvance()) { e.advance(); continue; }
            return;
        }
    }

    /** 계속 베면서 전투를 끝내고 승패에 따라 분기한다. */
    private static void fightAlwaysStriking(StoryEngine e, GameState s) {
        BattleSpec spec = e.current().battle();
        Battle b = new Battle(Bestiary.require(spec.enemyId()), s, new java.util.Random(1));
        while (!b.finished()) b.take(CombatAction.STRIKE);
        e.resumeAt(b.outcome() == Battle.Outcome.VICTORY ? spec.onVictory() : spec.onDefeat());
    }

    @Test void 자비로운_경로는_공덕이_오르고_주검_장면이_나온다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);

        runToStop(e, s);
        pick(e, "accept");                      // 프롤로그 계약 → 혼력 +50
        assertEquals(50, s.soul());

        runToStop(e, s);
        pick(e, "끝까지 듣는다");
        assertTrue(s.flag("원혼_말을_끝까지_들음"));

        runToStop(e, s);
        assertTrue(s.visited("ch1_02a_listened"), "경청 분기로 가야 합니다");

        pick(e, "이름이 무엇이오");
        assertTrue(s.flag("원혼_이름을_물음"));
        assertEquals(75, s.soul());             // 50 + 25

        runToStop(e, s);
        pick(e, "찾아준다");
        assertTrue(s.flag("원혼_소원_들어줌"));
        assertEquals(65, s.karma());            // 50 + 15

        runToStop(e, s);
        assertTrue(s.visited("ch1_05_mercy"), "조건 선택이 자비 분기를 골라야 합니다");
        assertFalse(s.visited("ch1_05_cold"));
        assertTrue(s.visited("ch1_07_won"), "잡귀를 이겼으니 승리 분기로 가야 합니다");
        assertEquals(105, s.soul(), "전투 승리 보상 +30이 붙어야 합니다");
        assertTrue(e.isFinished());
    }

    @Test void 냉정한_경로는_다른_장면으로_갈린다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);

        runToStop(e, s);
        pick(e, "accept");
        runToStop(e, s);
        pick(e, "용건만");                       // 경청하지 않음
        assertFalse(s.flag("원혼_말을_끝까지_들음"));

        runToStop(e, s);
        assertTrue(s.visited("ch1_02b_cut"), "축소 분기로 가야 합니다");

        pick(e, "묻지 않는다");
        assertFalse(s.flag("원혼_이름을_물음"));
        assertEquals(50, s.soul(), "이름을 묻지 않으면 혼력 보상이 없어야 합니다");
        assertFalse(s.visited("ch1_03b_name_given"));

        runToStop(e, s);
        pick(e, "심부름만 마치고");
        assertEquals(35, s.karma());            // 50 - 15

        runToStop(e, s);
        assertTrue(s.visited("ch1_05_cold"), "조건 선택이 냉정 분기를 골라야 합니다");
        assertFalse(s.visited("ch1_05_mercy"));
        assertTrue(e.isFinished());
    }

    @Test void 프롤로그_우회로도_같은_보상을_준다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);
        runToStop(e, s);
        pick(e, "왜 하필");                      // 질문 경로
        runToStop(e, s);
        pick(e, "accept_late");
        assertEquals(50, s.soul(), "어느 경로로 받든 계약금은 같아야 합니다");
    }

    @Test void 전투에_지면_패배_분기로_간다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);

        // 전투 스토리렛까지 간다.
        while (!e.current().isBattle()) {
            if (e.isAutoAdvance()) e.advance(); else e.choose(0);
        }

        s.addHp(-99);                            // 체력 1로 만들어 패배를 확정
        int karmaBefore = s.karma();

        RandomGenerator pessimistic = new RandomGenerator() {
            @Override public long nextLong() { throw new UnsupportedOperationException(); }
            @Override public int nextInt(int bound) { return bound - 1; }
        };
        BattleSpec spec = e.current().battle();
        Battle b = new Battle(Bestiary.require(spec.enemyId()), s, pessimistic);
        while (!b.finished()) b.take(CombatAction.STRIKE);

        assertEquals(Battle.Outcome.DEFEAT, b.outcome());
        e.resumeAt(spec.onDefeat());

        assertEquals("ch1_07_scattered", e.current().id());
        assertEquals(karmaBefore - 10, s.karma(), "강림이 건져 올린 값으로 공덕을 가져가야 합니다");
        assertEquals(30, s.hp(), "체력 0에서 30으로 복귀해야 합니다");
    }

    @Test void 전투_스토리렛은_자동_진행하지_않는다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);
        while (!e.current().isBattle()) {
            if (e.isAutoAdvance()) e.advance(); else e.choose(0);
        }
        assertFalse(e.isAutoAdvance(), "전투는 씬 처리기가 결과를 정해야 합니다");
        assertThrows(IllegalStateException.class, e::advance);
    }

    @Test void 엔딩_뒤에는_진행할_수_없다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);
        while (!e.isFinished()) {
            if (e.current().isBattle()) { fightAlwaysStriking(e, s); continue; }
            if (e.isAutoAdvance()) e.advance(); else e.choose(0);
        }
        assertThrows(IllegalStateException.class, e::advance);
    }
}
