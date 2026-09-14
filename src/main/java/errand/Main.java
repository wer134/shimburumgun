package errand;

import errand.engine.*;
import errand.io.ConsoleRenderer;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 진입점.
 *
 * <pre>
 *   gradle run                      대화형 플레이
 *   gradle run --args="--validate"  스토리 JSON 검증만 하고 종료 (CI용)
 *   gradle run --args="--auto"      항상 첫 선택지를 골라 끝까지 진행 (스모크 테스트)
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        // 플랫폼 기본 인코딩에 기대지 않는다. gradle run이나 파이프를 거치면
        // 한글이 ?로 깨지므로 출력 스트림을 UTF-8로 직접 연다.
        PrintStream stdout = new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        ConsoleRenderer out = new ConsoleRenderer(stdout);
        List<String> opts = List.of(args);

        StoryRepository repo;
        try {
            repo = StoryRepository.loadDefault();
        } catch (StoryLoadException e) {
            out.error(e.getMessage());
            System.exit(1);
            return;
        }

        if (opts.contains("--validate")) {
            out.line("스토리 검증 통과 — 스토리렛 %d개, 플래그 %d개, 카운터 %d개"
                    .formatted(repo.size(), repo.declaredFlags().size(), repo.declaredCounters().size()));
            return;
        }

        boolean auto = opts.contains("--auto");
        GameState state = new GameState();
        StoryEngine engine = new StoryEngine(repo, state);

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            while (true) {
                out.storylet(engine.current());

                if (engine.isFinished()) {
                    out.ending(engine.current().ending(), state);
                    return;
                }
                if (engine.isAutoAdvance()) {
                    if (!auto) {
                        out.line("  (엔터)");
                        in.readLine();
                    }
                    engine.advance();
                    continue;
                }

                List<Choice> choices = engine.availableChoices();
                out.status(state);
                out.choices(choices);

                int picked;
                if (auto) {
                    picked = 0;
                    out.line((picked + 1) + "  (자동)");
                } else {
                    String line = in.readLine();
                    if (line == null) return;
                    picked = parse(line, choices.size());
                    if (picked < 0) {
                        out.line("  1에서 " + choices.size() + " 사이로 입력하세요.");
                        continue;
                    }
                }
                engine.choose(picked);
            }
        }
    }

    /** 1-based 입력을 0-based 인덱스로. 잘못된 입력이면 -1. */
    private static int parse(String line, int count) {
        try {
            int n = Integer.parseInt(line.trim());
            return (n >= 1 && n <= count) ? n - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
