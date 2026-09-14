package errand.engine;

import java.util.List;

/**
 * 스토리렛과 선택지의 노출 조건.
 *
 * <p>JSON에서는 아래 형태 중 하나로 쓴다.
 * <pre>
 *   {"all": [ ... ]}                  모두 참
 *   {"any": [ ... ]}                  하나라도 참
 *   {"not": { ... }}                  부정
 *   {"flag": "이름"}                   플래그가 서 있으면 참
 *   {"flag": "이름", "is": false}      플래그가 없으면 참
 *   {"counter": "이름", "gte": 3}      카운터 비교
 *   {"stat": "karma", "gte": 70}      스탯 비교
 * </pre>
 *
 * <p>조건을 생략하면 {@link Always}로 간주한다.
 */
public sealed interface Condition {

    boolean test(GameState s);

    /** 사람이 읽을 수 있는 형태. 검증 실패 메시지와 디버그 출력에 쓴다. */
    String describe();

    record Always() implements Condition {
        @Override public boolean test(GameState s) { return true; }
        @Override public String describe() { return "항상"; }
    }

    record All(List<Condition> parts) implements Condition {
        @Override public boolean test(GameState s) { return parts.stream().allMatch(c -> c.test(s)); }
        @Override public String describe() {
            return parts.stream().map(Condition::describe).reduce((a, b) -> a + " 그리고 " + b).orElse("항상");
        }
    }

    record Any(List<Condition> parts) implements Condition {
        @Override public boolean test(GameState s) { return parts.stream().anyMatch(c -> c.test(s)); }
        @Override public String describe() {
            return parts.stream().map(Condition::describe).reduce((a, b) -> a + " 또는 " + b).orElse("거짓");
        }
    }

    record Not(Condition part) implements Condition {
        @Override public boolean test(GameState s) { return !part.test(s); }
        @Override public String describe() { return "(" + part.describe() + ")이 아님"; }
    }

    record FlagIs(String flag, boolean expected) implements Condition {
        @Override public boolean test(GameState s) { return s.flag(flag) == expected; }
        @Override public String describe() { return flag + (expected ? "" : " 없음"); }
    }

    record CounterCmp(String counter, Cmp cmp, int value) implements Condition {
        @Override public boolean test(GameState s) { return cmp.test(s.counter(counter), value); }
        @Override public String describe() { return counter + " " + cmp.json() + " " + value; }
    }

    record StatCmp(Stat stat, Cmp cmp, int value) implements Condition {
        @Override public boolean test(GameState s) { return cmp.test(s.stat(stat), value); }
        @Override public String describe() { return stat.json() + " " + cmp.json() + " " + value; }
    }
}
