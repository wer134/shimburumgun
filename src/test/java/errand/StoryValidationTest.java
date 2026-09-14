package errand;

import errand.engine.StoryLoadException;
import errand.engine.StoryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 검증기가 <b>플레이 전에</b> 오류를 잡는지 본다.
 *
 * <p>플래그 기반 분기의 가장 큰 위험은 "3장까지 가야 드러나는 오타"다.
 * 그런 건 플레이테스트로 못 잡으므로 로딩 시점에 전부 걸러야 한다.
 */
class StoryValidationTest {

    @Test void 실제_스토리는_검증을_통과한다() {
        StoryRepository repo = StoryRepository.loadDefault();
        assertTrue(repo.size() >= 10, "스토리렛이 너무 적습니다: " + repo.size());
        assertNotNull(repo.start());
        assertTrue(repo.declaredFlags().contains("원혼_이름을_물음"));
        assertTrue(repo.declaredCounters().contains("천도_횟수"));
    }

    @Test void 고장난_스토리는_모든_오류를_한꺼번에_보고한다() {
        StoryLoadException e = assertThrows(StoryLoadException.class,
                () -> StoryRepository.load("/broken/index.json"));
        String msg = e.getMessage();

        assertAll(
                () -> assertTrue(msg.contains("존재하지_않음"), "끊어진 goto를 못 잡음:\n" + msg),
                () -> assertTrue(msg.contains("오타난_플래그"), "선언 안 된 플래그를 못 잡음:\n" + msg),
                () -> assertTrue(msg.contains("id 중복"), "중복 id를 못 잡음:\n" + msg),
                () -> assertTrue(msg.contains("orphan"), "고아 스토리렛을 못 잡음:\n" + msg)
        );
    }

    @Test void 없는_시작점은_거부한다() {
        StoryLoadException e = assertThrows(StoryLoadException.class,
                () -> StoryRepository.load("/broken/bad-start.json"));
        assertTrue(e.getMessage().contains("없습니다"), e.getMessage());
    }
}
