package errand.engine;

import java.util.Comparator;
import java.util.List;

/**
 * 진행을 담당한다. 현재 스토리렛을 들고 있다가 선택을 받아 다음으로 넘긴다.
 *
 * <p>다음 스토리렛을 정하는 방법은 두 가지다.
 * <ol>
 *   <li><b>명시 이동</b> — 선택지의 goto나 스토리렛의 next가 가리키는 곳으로 간다.
 *       선형 구간에 쓴다.</li>
 *   <li><b>조건 선택</b> — 목적지가 비어 있으면 지금 상태에서 등장 자격이 있는
 *       스토리렛 중 priority가 가장 높은 것을 고른다. 같은 자리에 여러 변주를
 *       두고 상태에 따라 갈아 끼울 때 쓴다.</li>
 * </ol>
 *
 * <p>2번이 있기 때문에 장면을 추가할 때 기존 분기표를 고칠 필요가 없다. 새 JSON에
 * 조건과 우선순위만 적어 넣으면 그 자리에 끼어든다.
 */
public final class StoryEngine {

    private final StoryRepository repo;
    private final GameState state;
    private Storylet current;

    public StoryEngine(StoryRepository repo, GameState state) {
        this.repo = repo;
        this.state = state;
        enter(repo.start());
    }

    public Storylet current()   { return current; }
    public GameState state()    { return state; }
    public boolean isFinished() { return current.isEnding(); }

    /** 지금 보여줄 선택지. 비어 있으면 {@link #advance()}로 넘어가는 자동 진행 구간이다. */
    public List<Choice> availableChoices() { return current.visibleChoices(state); }

    public boolean isAutoAdvance() {
        return !isFinished() && current.choices().isEmpty() && !current.isBattle();
    }

    /** 선택지 없는 스토리렛에서 다음으로 넘어간다. */
    public void advance() {
        if (isFinished()) throw new IllegalStateException("엔딩에 도달해 더 진행할 수 없습니다.");
        if (current.isBattle()) {
            throw new IllegalStateException(
                    "'%s'는 전투 스토리렛입니다. 전투를 치른 뒤 resumeAt()으로 승패 분기를 지정하세요."
                            .formatted(current.id()));
        }
        if (!current.choices().isEmpty()) {
            throw new IllegalStateException("'%s'에는 선택지가 있습니다. choose()를 쓰세요.".formatted(current.id()));
        }
        enter(resolve(current.next()));
    }

    /**
     * 씬 처리기(전투 등)가 결과에 따라 갈 곳을 직접 지정한다.
     *
     * <p>선택지도 next도 아닌 경로로 이동하는 유일한 통로다. 전투의 승패처럼
     * 텍스트 밖에서 결정되는 분기에만 쓴다.
     */
    public void resumeAt(String storyletId) {
        enter(repo.require(storyletId));
    }

    /** 선택지를 고른다. 효과를 적용한 뒤 다음 스토리렛으로 이동한다. */
    public void choose(Choice choice) {
        List<Choice> visible = availableChoices();
        if (!visible.contains(choice)) {
            throw new IllegalArgumentException(
                    "'%s'에서 고를 수 없는 선택지입니다: %s".formatted(current.id(), choice.id()));
        }
        choice.applyTo(state);
        enter(resolve(choice.target()));
    }

    public void choose(int index) {
        List<Choice> visible = availableChoices();
        if (index < 0 || index >= visible.size()) {
            throw new IndexOutOfBoundsException(
                    "선택지 번호가 범위를 벗어났습니다: %d (0..%d)".formatted(index, visible.size() - 1));
        }
        choose(visible.get(index));
    }

    // ---------- 내부 ----------

    private void enter(Storylet s) {
        current = s;
        state.markVisited(s.id());
        s.onEnter().forEach(e -> e.apply(state));

        if (!s.isEnding() && !s.choices().isEmpty() && s.visibleChoices(state).isEmpty()) {
            throw new IllegalStateException(
                    "'%s'에 도달했으나 조건을 만족하는 선택지가 하나도 없습니다. "
                            .formatted(s.id())
                            + "조건 없는 선택지를 하나 남겨두세요. 현재 상태: " + state);
        }
    }

    /** 목적지가 비어 있으면 조건 선택으로 고른다. */
    private Storylet resolve(String explicitTarget) {
        if (explicitTarget != null) return repo.require(explicitTarget);

        return repo.all().stream()
                .filter(s -> s.eligible(state))
                .max(Comparator.comparingInt(Storylet::priority))
                .orElseThrow(() -> new IllegalStateException(
                        "'%s' 다음에 등장할 수 있는 스토리렛이 없습니다. 현재 상태: %s"
                                .formatted(current.id(), state)));
    }
}
