package errand;

import errand.combat.Battle;
import errand.combat.Bestiary;
import errand.economy.Enhancer;
import errand.economy.Shop;
import errand.engine.*;
import errand.io.BattleConsole;
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
 *   ./gradlew run                        대화형 플레이
 *   ./gradlew validateStory              스토리 JSON 검증만
 *   ./gradlew smoke                      자동으로 끝까지 진행 (막히는 곳 확인)
 *
 *   --validate                           스토리 검증만 하고 종료
 *   --auto                               항상 첫 선택지를 골라 끝까지 진행
 *   --seed=42                            난수 고정 (강화·회피·선공 재현)
 *
 *   테스트용 초기 상태 지정 — 뒤쪽 분기를 바로 확인할 때:
 *   --karma=80 --soul=400 --weapon=4 --hp=60 --charm=2
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
            repo = StoryRepository.loadDefault(Bestiary.all().keySet());
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
        applyStateOverrides(opts, state, out);
        StoryEngine engine = new StoryEngine(repo, state);
        RandomGenerator rng = rngFrom(opts);
        Enhancer enhancer = new Enhancer(rng);

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            ShopConsole shopConsole = new ShopConsole(stdout, in, auto);
            BattleConsole battleConsole = new BattleConsole(stdout, in, auto, rng);

            while (true) {
                Storylet cur = engine.current();
                out.storylet(cur);

                if (cur.isShop()) {
                    // 막간마다 새 Shop을 만든다 — 천도 횟수 제한이 인스턴스에 묶여 있다.
                    shopConsole.run(new Shop(enhancer), state);
                }

                if (cur.isBattle()) {
                    BattleSpec spec = cur.battle();
                    Battle.Outcome result =
                            battleConsole.run(Bestiary.require(spec.enemyId()), state);
                    engine.resumeAt(result == Battle.Outcome.VICTORY
                            ? spec.onVictory()
                            : spec.onDefeat());
                    continue;
                }

                if (engine.isFinished()) {
                    out.ending(engine.current().ending(), state);
                    return;
                }
                if (engine.isAutoAdvance()) {
                    if (!auto) {
                        out.line("  (엔터)");
                        // EOF면 입력이 끊긴 것이다. 그대로 진행하면 남은 프롬프트를
                        // 전부 지나쳐 엔딩까지 쏟아지므로 여기서 멈춘다.
                        if (in.readLine() == null) {
                            out.error("입력이 끊겼습니다. 대화형으로 플레이하려면 ./gradlew run 을 쓰세요.");
                            return;
                        }
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

    /**
     * 테스트용 초기 상태 지정.
     *
     * <p>{@code --weapon=4 --soul=400} 같은 인자로 뒤쪽 분기를 바로 확인할 수 있다.
     * 예를 들어 2장 수문장 난이도를 보려면 강화 단계를 바꿔 가며 돌리면 된다.
     * 값을 지정하면 어떤 상태로 시작하는지 화면에 찍어 준다 — 조작된 상태로
     * 플레이한 것을 나중에 진짜 밸런스와 착각하지 않도록.
     */
    private static void applyStateOverrides(List<String> opts, GameState s, ConsoleRenderer out) {
        boolean changed = false;
        changed |= intOpt(opts, "--karma=").map(v -> { s.addKarma(v - s.karma()); return true; }).orElse(false);
        changed |= intOpt(opts, "--soul=").map(v -> { s.addSoul(v - s.soul()); return true; }).orElse(false);
        changed |= intOpt(opts, "--hp=").map(v -> { s.addHp(v - s.hp()); return true; }).orElse(false);
        changed |= intOpt(opts, "--weapon=").map(v -> { s.addWeapon(v - s.weapon()); return true; }).orElse(false);
        changed |= intOpt(opts, "--charm=").map(v -> { s.addCharms(v - s.charms()); return true; }).orElse(false);

        if (changed) {
            out.line("  [테스트 모드] 초기 상태를 지정했습니다 — " + s);
        }
    }

    private static Optional<Integer> intOpt(List<String> opts, String prefix) {
        return opts.stream()
                .filter(o -> o.startsWith(prefix))
                .map(o -> Integer.parseInt(o.substring(prefix.length())))
                .findFirst();
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
