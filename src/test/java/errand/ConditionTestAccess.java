package errand;

import com.fasterxml.jackson.databind.JsonNode;
import errand.engine.Condition;
import errand.engine.StoryTestHooks;

/** StoryJson은 패키지 전용이므로 테스트에서 쓰기 위한 얇은 우회 통로. */
final class ConditionTestAccess {
    static Condition parse(JsonNode n) { return StoryTestHooks.parseCondition(n); }
}
