package errand.io;

import errand.combat.Battle;
import errand.combat.CombatAction;
import errand.combat.Enemy;
import errand.engine.GameState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.random.RandomGenerator;

/** 전투의 콘솔 진행. 규칙은 {@link Battle}에 있고 여기는 입출력만 한다. */
public final class BattleConsole {

    private static final int WIDTH = 64;

    private final PrintStream out;
    private final BufferedReader in;
    private final boolean auto;
    private final RandomGenerator rng;

    public BattleConsole(PrintStream out, BufferedReader in, boolean auto, RandomGenerator rng) {
        this.out = out;
        this.in = in;
        this.auto = auto;
        this.rng = rng;
    }

    /** 전투가 끝날 때까지 돌리고 결과를 돌려준다. */
    public Battle.Outcome run(Enemy enemy, GameState state) throws IOException {
        Battle battle = new Battle(enemy, state, rng);

        out.println();
        out.printf("  ── %s (체력 %d) ──%n", enemy.name(), enemy.maxHp());
        out.println(battle.playerFirst()
                ? "  낫이 먼저 움직인다."
                : "  상대가 먼저 움직인다.");

        while (!battle.finished()) {
            List<CombatAction> actions = battle.availableActions();

            out.println();
            out.println("  " + "─".repeat(WIDTH));
            out.printf("  %s 체력 %d/%d      나 %s%n",
                    enemy.name(), battle.enemyHp(), enemy.maxHp(), state);
            if (battle.braceBonusReady()) out.println("  버틴 힘이 실려 있다 — 다음 공격 ×1.5");
            out.println("  " + "─".repeat(WIDTH));

            for (int i = 0; i < actions.size(); i++) {
                CombatAction a = actions.get(i);
                out.printf("  %d) %-12s %s%n", i + 1, a.label(), a.detail());
            }
            out.print("> ");
            out.flush();

            int picked;
            if (auto) {
                picked = 0;                                   // 자동 모드는 계속 벤다
                out.println("1  (자동)");
            } else {
                String line = in.readLine();
                if (line == null) return battle.outcome();
                picked = parse(line, actions.size());
                if (picked < 0) {
                    out.println("  1에서 " + actions.size() + " 사이로 입력하세요.");
                    continue;
                }
            }

            describe(battle.take(actions.get(picked)), enemy);
        }

        out.println();
        out.println(battle.outcome() == Battle.Outcome.VICTORY
                ? "  " + enemy.name() + "이 흩어진다."
                : "  눈앞이 흐려진다. 서 있을 수가 없다.");
        return battle.outcome();
    }

    private void describe(Battle.TurnLog log, Enemy enemy) {
        out.println();
        switch (log.action()) {
            case STRIKE -> out.printf("  낫을 휘두른다. %d 피해%s%n",
                    log.damageDealt(), log.braceBonusUsed() ? "  (버틴 힘 ×1.5)" : "");
            case BRACE -> out.println("  낫을 세워 버틴다.");
            case BURN_SOUL -> out.printf("  혼을 태운다. 손이 뜨거워진다. %d 피해%s%n",
                    log.damageDealt(), log.braceBonusUsed() ? "  (버틴 힘 ×1.5)" : "");
        }

        if (log.enemyHpAfter() == 0) return;

        if (!log.enemyActed()) return;
        if (log.evaded()) {
            out.println("  " + enemy.name() + "의 손이 허공을 스친다.");
        } else if (log.enemyHeavy()) {
            out.printf("  %s가 크게 내리친다. %d 피해%n", enemy.name(), log.damageTaken());
        } else {
            out.printf("  %s가 덮쳐 온다. %d 피해%n", enemy.name(), log.damageTaken());
        }
    }

    private static int parse(String line, int count) {
        try {
            int n = Integer.parseInt(line.trim());
            return (n >= 1 && n <= count) ? n - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
