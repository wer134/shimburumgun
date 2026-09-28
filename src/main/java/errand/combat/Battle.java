package errand.combat;

import errand.engine.GameState;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 턴제 텍스트 전투. GDD 6장.
 *
 * <p>루프가 아니라 상태 기계다. 콘솔이 한 턴씩 밀어 넣고, 테스트는 고정 난수로
 * 같은 전투를 재현한다.
 *
 * <p><b>GDD가 정하지 않아 여기서 결정한 것들</b> — 6.4의 검산이 적의 4회 공격을
 * 전제하는데 그건 적이 먼저 때린다는 뜻이고, 그러면 +3의 "선공 확정" 특전이 갈
 * 자리가 없다. 아래처럼 정리했다.
 * <ul>
 *   <li><b>선공</b> — 낫 +{@value #INITIATIVE_AT_WEAPON} 이상이면 플레이어 확정 선공,
 *       그 미만이면 50% 확률. 이것이 "홍사줄이 감긴 낫"의 실제 값어치다.</li>
 *   <li><b>강공격</b> — 적의 공격 횟수 기준(3번째, 6번째…). 라운드 수가 아니다.</li>
 *   <li><b>패배</b> — 체력 0이면 {@link Outcome#DEFEAT}로 끝내고 서사 처리는
 *       스토리 JSON에 맡긴다. 사이길에서는 죽음이 이미 일어난 일이므로 하드
 *       게임오버가 어울리지 않는다.</li>
 * </ul>
 */
public final class Battle {

    public static final int BURN_SOUL_COST = 15;
    public static final int BURN_SOUL_KARMA = 2;

    /** 이 단계 이상이면 선공이 확정된다. GDD 5.1 "홍사줄이 감긴 낫". */
    public static final int INITIATIVE_AT_WEAPON = 3;

    /** 혼을 태운 누적 횟수. 3 이상이면 3장에서 폭력 지옥이 열린다 (GDD 7.4). */
    public static final String BURN_COUNTER = "혼_태운_횟수";

    private static final double BRACE_DAMAGE_BONUS = 1.5;
    private static final int BURN_DAMAGE_MULTIPLIER = 2;

    public enum Outcome { ONGOING, VICTORY, DEFEAT }

    /** 한 라운드에 일어난 일. 렌더링과 검증에 쓴다. */
    public record TurnLog(
            int round,
            CombatAction action,
            int damageDealt,
            boolean braceBonusUsed,
            int enemyHpAfter,
            boolean enemyActed,
            boolean enemyHeavy,
            boolean evaded,
            int damageTaken,
            int playerHpAfter,
            Outcome outcome
    ) {}

    private final Enemy enemy;
    private final GameState state;
    private final RandomGenerator rng;
    private final boolean playerFirst;
    private final List<TurnLog> history = new ArrayList<>();

    private int enemyHp;
    private int round;
    private int enemyAttackCount;
    private boolean braceBonusPending;
    private Outcome outcome = Outcome.ONGOING;

    public Battle(Enemy enemy, GameState state, RandomGenerator rng) {
        this.enemy = enemy;
        this.state = state;
        this.rng = rng;
        this.enemyHp = enemy.maxHp();
        this.playerFirst = state.weapon() >= INITIATIVE_AT_WEAPON || rng.nextInt(100) < 50;
    }

    public Enemy enemy()            { return enemy; }
    public int enemyHp()            { return enemyHp; }
    public int round()              { return round; }
    public boolean playerFirst()    { return playerFirst; }
    public Outcome outcome()        { return outcome; }
    public boolean finished()       { return outcome != Outcome.ONGOING; }
    public List<TurnLog> history()  { return List.copyOf(history); }

    /** 다음 공격에 1.5배가 붙어 있는가. 콘솔이 표시해 준다. */
    public boolean braceBonusReady() { return braceBonusPending; }

    /** 지금 고를 수 있는 행동. 혼력이 모자라면 혼 태우기가 빠진다. */
    public List<CombatAction> availableActions() {
        List<CombatAction> out = new ArrayList<>(List.of(CombatAction.STRIKE, CombatAction.BRACE));
        if (state.soul() >= BURN_SOUL_COST) out.add(CombatAction.BURN_SOUL);
        return out;
    }

    /** 한 라운드를 진행한다. */
    public TurnLog take(CombatAction action) {
        if (finished()) throw new IllegalStateException("이미 끝난 전투입니다: " + outcome);
        if (!availableActions().contains(action)) {
            throw new IllegalArgumentException("지금 고를 수 없는 행동입니다: " + action);
        }

        round++;
        boolean bracing = action == CombatAction.BRACE;

        // 적이 선공이면 플레이어가 행동하기 전에 맞는다. 버티기 선언은 이미 했으므로 적용된다.
        Strike incoming = playerFirst ? null : enemyStrike(bracing);
        if (incoming != null && state.hp() <= 0) {
            return finish(action, 0, false, incoming, Outcome.DEFEAT);
        }

        Attack mine = playerStrike(action);
        enemyHp = Math.max(0, enemyHp - mine.damage());

        if (enemyHp == 0) {
            return finish(action, mine.damage(), mine.bonusUsed(), incoming, Outcome.VICTORY);
        }

        // 플레이어 선공이었으면 이제 적이 반격한다.
        if (incoming == null) {
            incoming = enemyStrike(bracing);
            if (state.hp() <= 0) {
                return finish(action, mine.damage(), mine.bonusUsed(), incoming, Outcome.DEFEAT);
            }
        }

        return finish(action, mine.damage(), mine.bonusUsed(), incoming, Outcome.ONGOING);
    }

    // ---------- 내부 ----------

    private record Attack(int damage, boolean bonusUsed) {}

    private record Strike(int damage, boolean heavy, boolean evaded) {}

    private Attack playerStrike(CombatAction action) {
        if (action == CombatAction.BRACE) {
            // 버티기는 데미지가 없다. 쌓여 있던 보너스는 소모하지 않고 다음 공격까지 들고 간다.
            braceBonusPending = true;
            return new Attack(0, false);
        }

        int base = state.weaponDamage();
        if (action == CombatAction.BURN_SOUL) {
            base *= BURN_DAMAGE_MULTIPLIER;
            state.addSoul(-BURN_SOUL_COST);
            state.addKarma(-BURN_SOUL_KARMA);
            state.addCounter(BURN_COUNTER, 1);
        }

        boolean bonusUsed = braceBonusPending;
        if (bonusUsed) {
            base = (int) Math.round(base * BRACE_DAMAGE_BONUS);
            braceBonusPending = false;
        }
        return new Attack(base, bonusUsed);
    }

    private Strike enemyStrike(boolean bracing) {
        enemyAttackCount++;
        boolean heavy = enemy.isHeavy(enemyAttackCount);
        int raw = enemy.damageOf(enemyAttackCount);

        if (rng.nextInt(100) < state.evasionPercent()) {
            return new Strike(0, heavy, true);
        }

        int dealt = bracing ? raw / 2 : raw;
        state.addHp(-dealt);
        return new Strike(dealt, heavy, false);
    }

    private TurnLog finish(CombatAction action, int dealt, boolean bonusUsed,
                           Strike incoming, Outcome result) {
        outcome = result;
        TurnLog log = new TurnLog(
                round, action, dealt, bonusUsed, enemyHp,
                incoming != null,
                incoming != null && incoming.heavy(),
                incoming != null && incoming.evaded(),
                incoming == null ? 0 : incoming.damage(),
                state.hp(), result);
        history.add(log);
        return log;
    }
}
