package errand.engine;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 조건/효과 파서를 패키지 밖에서 단위 테스트하기 위한 통로.
 *
 * <p>{@link StoryJson}을 공개 API로 만들고 싶지 않아 분리했다. 운영 코드는
 * 이 클래스를 쓰지 않는다.
 */
public final class StoryTestHooks {

    private StoryTestHooks() {}

    public static Condition parseCondition(JsonNode n) {
        return StoryJson.condition(n, "test");
    }

    public static java.util.List<Effect> parseEffects(JsonNode n) {
        return StoryJson.effects(n, "test");
    }
}
