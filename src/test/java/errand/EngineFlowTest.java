package errand;

import errand.engine.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 스토리 JSON을 따라 끝까지 진행되는지, 분기가 갈리는지 본다. */
class EngineFlowTest {

    private static StoryEngine engine(GameState s) {
        return new StoryEngine(StoryRepository.loadDefault(), s);
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

    /** 자동 진행 구간을 선택지가 나올 때까지 넘긴다. */
    private static void runToChoice(StoryEngine e) {
        while (!e.isFinished() && e.isAutoAdvance()) e.advance();
    }

    @Test void 자비로운_경로는_공덕이_오르고_주검_장면이_나온다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);

        runToChoice(e);
        pick(e, "accept");                      // 프롤로그 계약 → 혼력 +50
        assertEquals(50, s.soul());

        runToChoice(e);
        pick(e, "끝까지 듣는다");
        assertTrue(s.flag("원혼_말을_끝까지_들음"));

        runToChoice(e);
        assertTrue(s.visited("ch1_02a_listened"), "경청 분기로 가야 합니다");

        pick(e, "이름이 무엇이오");
        assertTrue(s.flag("원혼_이름을_물음"));
        assertEquals(75, s.soul());             // 50 + 25

        runToChoice(e);
        pick(e, "찾아준다");
        assertTrue(s.flag("원혼_소원_들어줌"));
        assertEquals(65, s.karma());            // 50 + 15

        runToChoice(e);
        assertTrue(s.visited("ch1_05_mercy"), "조건 선택이 자비 분기를 골라야 합니다");
        assertFalse(s.visited("ch1_05_cold"));
        assertTrue(e.isFinished());
    }

    @Test void 냉정한_경로는_다른_장면으로_갈린다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);

        runToChoice(e);
        pick(e, "accept");
        runToChoice(e);
        pick(e, "용건만");                       // 경청하지 않음
        assertFalse(s.flag("원혼_말을_끝까지_들음"));

        runToChoice(e);
        assertTrue(s.visited("ch1_02b_cut"), "축소 분기로 가야 합니다");

        pick(e, "묻지 않는다");
        assertFalse(s.flag("원혼_이름을_물음"));
        assertEquals(50, s.soul(), "이름을 묻지 않으면 혼력 보상이 없어야 합니다");
        assertFalse(s.visited("ch1_03b_name_given"));

        runToChoice(e);
        pick(e, "심부름만 마치고");
        assertEquals(35, s.karma());            // 50 - 15

        runToChoice(e);
        assertTrue(s.visited("ch1_05_cold"), "조건 선택이 냉정 분기를 골라야 합니다");
        assertFalse(s.visited("ch1_05_mercy"));
        assertTrue(e.isFinished());
    }

    @Test void 프롤로그_우회로도_같은_보상을_준다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);
        runToChoice(e);
        pick(e, "왜 하필");                      // 질문 경로
        runToChoice(e);
        pick(e, "accept_late");
        assertEquals(50, s.soul(), "어느 경로로 받든 계약금은 같아야 합니다");
    }

    @Test void 엔딩_뒤에는_진행할_수_없다() {
        GameState s = new GameState();
        StoryEngine e = engine(s);
        while (!e.isFinished()) {
            if (e.isAutoAdvance()) e.advance(); else e.choose(0);
        }
        assertThrows(IllegalStateException.class, e::advance);
    }
}
