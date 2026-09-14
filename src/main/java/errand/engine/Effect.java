package errand.engine;

/**
 * 선택지를 고르거나 스토리렛에 진입할 때 적용되는 상태 변화.
 *
 * <p>JSON 표기:
 * <pre>
 *   {"karma": 15}                       공덕 +15 (음수 가능)
 *   {"soul": 30}                        혼력 +30
 *   {"hp": -12}                         체력 -12
 *   {"weapon": 1}                       낫 단계 +1
 *   {"han": 1}                          한 +1
 *   {"flag": "이름"}                     플래그 세움
 *   {"flag": "이름", "set": false}       플래그 내림
 *   {"counter": "이름", "add": 1}        카운터 증가
 * </pre>
 */
public sealed interface Effect {

    void apply(GameState s);

    String describe();

    record StatDelta(Stat stat, int delta) implements Effect {
        @Override public void apply(GameState s) {
            switch (stat) {
                case KARMA  -> s.addKarma(delta);
                case SOUL   -> s.addSoul(delta);
                case HP     -> s.addHp(delta);
                case WEAPON -> s.addWeapon(delta);
                case HAN    -> s.addHan(delta);
            }
        }
        @Override public String describe() {
            return "%s %+d".formatted(stat.json(), delta);
        }
    }

    record SetFlag(String flag, boolean value) implements Effect {
        @Override public void apply(GameState s) { s.setFlag(flag, value); }
        @Override public String describe() { return (value ? "" : "해제 ") + flag; }
    }

    record AddCounter(String counter, int delta) implements Effect {
        @Override public void apply(GameState s) { s.addCounter(counter, delta); }
        @Override public String describe() { return "%s %+d".formatted(counter, delta); }
    }
}
