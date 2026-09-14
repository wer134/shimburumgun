package errand.engine;

import java.util.List;

/**
 * 선택지 하나.
 *
 * @param id       스토리렛 안에서 유일한 식별자. 세이브/분석용
 * @param text     화면에 보이는 문구
 * @param requires 이 선택지가 보일 조건
 * @param effects  고르는 즉시 적용되는 변화
 * @param target   이동할 스토리렛 id. null이면 엔진이 조건에 맞는 스토리렛을 고른다
 */
public record Choice(
        String id,
        String text,
        Condition requires,
        List<Effect> effects,
        String target
) {
    public boolean visibleTo(GameState s) { return requires.test(s); }

    public void applyTo(GameState s) { effects.forEach(e -> e.apply(s)); }
}
