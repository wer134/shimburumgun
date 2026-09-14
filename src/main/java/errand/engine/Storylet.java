package errand.engine;

import java.util.List;

/**
 * 이야기의 최소 단위. 조건을 만족할 때 등장하는 한 덩어리의 텍스트와 선택지다.
 *
 * <p>Failbetter의 quality-based narrative를 축소 적용한 형태다. 분기를 트리로
 * 고정하지 않고 "지금 상태에서 등장 자격이 있는 것 중 우선순위가 가장 높은 것"을
 * 고르므로, 장면을 추가할 때 기존 분기표를 건드릴 필요가 없다.
 *
 * @param id       전역 유일 식별자
 * @param chapter  소속 장. 0은 프롤로그/막간
 * @param scene    처리 방식. "narrative"(기본) / "battle" / "shop"
 * @param speaker  화자 이름. null이면 지문
 * @param text     문단 목록
 * @param requires 등장 조건
 * @param priority 같은 조건을 만족하는 스토리렛이 여럿일 때 큰 값이 이긴다
 * @param once     true면 한 번 본 뒤로는 후보에서 제외된다
 * @param onEnter  진입 즉시 적용되는 효과
 * @param choices  선택지. 비어 있으면 next로 자동 진행한다
 * @param next     선택지가 없을 때 이동할 대상. null이면 엔진이 고른다
 * @param ending   엔딩 스토리렛이면 엔딩 코드("A"/"B"/"C"/"HIDDEN"). 아니면 null
 */
public record Storylet(
        String id,
        int chapter,
        String scene,
        String speaker,
        List<String> text,
        Condition requires,
        int priority,
        boolean once,
        List<Effect> onEnter,
        List<Choice> choices,
        String next,
        String ending
) {
    public static final String SCENE_NARRATIVE = "narrative";
    public static final String SCENE_BATTLE    = "battle";
    public static final String SCENE_SHOP      = "shop";

    public boolean isEnding()   { return ending != null; }

    public boolean isTerminal() { return isEnding() || (choices.isEmpty() && next == null); }

    /** 지금 이 상태에서 등장할 자격이 있는가. */
    public boolean eligible(GameState s) {
        if (once && s.visited(id)) return false;
        return requires.test(s);
    }

    public List<Choice> visibleChoices(GameState s) {
        return choices.stream().filter(c -> c.visibleTo(s)).toList();
    }
}
