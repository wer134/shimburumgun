package errand;

import errand.economy.Enhancer;
import errand.economy.Shop;
import errand.engine.*;
import errand.io.ConsoleRenderer;
import errand.io.ShopConsole;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 진입점.
 *
 * <pre>
 *   gradle run                      대화형 플레이
 *   gradle run --args="--validate"  스토리 JSON 검증만 하고 종료 (CI용)
 *   gradle run --args="--auto"      항상 첫 선택지를 골라 끝까지 진행 (스모크 테스트)
 *   gradle run --args="--seed=42"   강화 난수를 고정해 재현 가능하게
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
        Enhancer enhancer = new Enhancer(rngFrom(opts));

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            ShopConsole shopConsole = new ShopConsole(stdout, in, auto);

            while (true) {
                out.storylet(engine.current());

                if (Storylet.SCENE_SHOP.equals(engine.current().scene())) {
                    // 막간마다 새 Shop을 만든다 — 천도 횟수 제한이 인스턴스에 묶여 있다.
                    shopConsole.run(new Shop(enhancer), state);
                }

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

    /** {@code --seed=123}이 있으면 그 시드로, 없으면 매번 다른 난수로. */
    private static RandomGenerator rngFrom(List<String> opts) {
        Optional<Long> seed = opts.stream()
                .filter(o -> o.startsWith("--seed="))
                .map(o -> Long.parseLong(o.substring("--seed=".length())))
                .findFirst();
        return seed.<RandomGenerator>map(java.util.Random::new).orElseGet(java.util.Random::new);
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
