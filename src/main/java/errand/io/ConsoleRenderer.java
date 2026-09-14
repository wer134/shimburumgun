package errand.io;

import errand.engine.Choice;
import errand.engine.GameState;
import errand.engine.Storylet;

import java.io.PrintStream;
import java.util.List;

/** 콘솔 출력. 렌더링만 하고 상태는 건드리지 않는다. */
public final class ConsoleRenderer {

    private static final int WIDTH = 64;

    private final PrintStream out;

    public ConsoleRenderer(PrintStream out) { this.out = out; }

    public void storylet(Storylet s) {
        out.println();
        if (s.speaker() != null) out.println("[" + s.speaker() + "]");
        for (String line : s.text()) {
            out.println("  " + line);
            out.println();
        }
        if (Storylet.SCENE_BATTLE.equals(s.scene())) {
            out.println("  (전투 장면 — 전투 시스템 미구현. 지금은 통과합니다.)");
            out.println();
        }
    }

    public void choices(List<Choice> choices) {
        for (int i = 0; i < choices.size(); i++) {
            out.printf("  %d) %s%n", i + 1, choices.get(i).text());
        }
        out.print("> ");
        out.flush();
    }

    public void status(GameState s) {
        out.println(rule());
        out.println("  " + s);
        out.println(rule());
    }

    public void ending(String code, GameState s) {
        out.println(rule());
        out.println("  엔딩: " + code);
        out.println("  최종 상태 — " + s);
        if (!s.flags().isEmpty()) out.println("  플래그 — " + String.join(", ", s.flags()));
        if (!s.counters().isEmpty()) out.println("  카운터 — " + s.counters());
        out.println("  거쳐온 장면 " + s.visitedIds().size() + "개");
        out.println(rule());
    }

    public void error(String message) {
        out.println();
        out.println(rule());
        out.println("  " + message);
        out.println(rule());
    }

    public void line(String text) { out.println(text); }

    private static String rule() { return "  " + "─".repeat(WIDTH); }
}
