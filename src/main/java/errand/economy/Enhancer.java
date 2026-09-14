package errand.economy;

import errand.engine.GameState;

import java.util.random.RandomGenerator;

/**
 * 저승낫 강화 시도. GDD 5장.
 *
 * <p>난수 발생기를 주입받는다. 테스트에서 고정 시드를 넣어 결과를 재현하고,
 * 몬테카를로 회귀 테스트에서 수십만 회를 돌리기 위함이다.
 */
public final class Enhancer {

    public enum Outcome {
        /** 성공. 한은 0으로 돌아간다. */
        SUCCESS,
        /** 실패했으나 단계는 유지됐다(하락 없는 구간이거나 한이 막아줬다). */
        FAIL_HELD,
        /** 실패해서 단계가 떨어졌다. */
        FAIL_DROPPED,
        /** 이미 최대 단계라 시도할 수 없다. */
        MAX_LEVEL,
        /** 혼력이 모자라 시도할 수 없다. */
        NOT_ENOUGH_SOUL,
        /** 부적을 쓰겠다고 했으나 가진 것이 없다. */
        NO_CHARM
    }

    /**
     * @param outcome      결과
     * @param levelBefore  시도 전 단계
     * @param levelAfter   시도 후 단계
     * @param soulSpent    소모한 혼력 (시도조차 못 했으면 0)
     * @param chancePercent 실제 적용된 성공률 (부적 보정 포함)
     * @param charmUsed    부적을 소모했는가
     * @param hanShielded  한이 단계 하락을 막았는가
     */
    public record Result(
            Outcome outcome,
            int levelBefore,
            int levelAfter,
            int soulSpent,
            int chancePercent,
            boolean charmUsed,
            boolean hanShielded
    ) {
        public boolean attempted() {
            return outcome == Outcome.SUCCESS
                    || outcome == Outcome.FAIL_HELD
                    || outcome == Outcome.FAIL_DROPPED;
        }
    }

    private final RandomGenerator rng;

    public Enhancer(RandomGenerator rng) { this.rng = rng; }

    /** 시도 가능 여부만 미리 본다. 상점 UI에서 버튼을 회색으로 만들 때 쓴다. */
    public Outcome check(GameState s, boolean useCharm) {
        if (WeaponTable.isMax(s.weapon()))                 return Outcome.MAX_LEVEL;
        if (useCharm && s.charms() <= 0)                   return Outcome.NO_CHARM;
        if (s.soul() < WeaponTable.stepFrom(s.weapon()).soulCost()) return Outcome.NOT_ENOUGH_SOUL;
        return Outcome.SUCCESS;
    }

    public Result attempt(GameState s, boolean useCharm) {
        int before = s.weapon();

        Outcome blocked = check(s, useCharm);
        if (blocked != Outcome.SUCCESS) {
            return new Result(blocked, before, before, 0, 0, false, false);
        }

        WeaponTable.Step step = WeaponTable.stepFrom(before);
        int chance = WeaponTable.successPercent(before, useCharm);

        s.addSoul(-step.soulCost());
        if (useCharm) s.addCharms(-1);

        if (rng.nextInt(100) < chance) {
            s.addWeapon(1);
            s.setHan(0);
            return new Result(Outcome.SUCCESS, before, s.weapon(),
                    step.soulCost(), chance, useCharm, false);
        }

        // 실패. 한은 하락이 실제로 일어나는 구간에서만 쌓이고 소모된다 —
        // 하락이 없는 +0~+2 구간에서 한을 태워 없애면 정작 필요한 +3 이상에서
        // 방패가 남지 않는다.
        boolean shielded = false;
        if (step.failDrop() > 0) {
            s.addHan(1);
            if (s.han() >= WeaponTable.HAN_SHIELD_AT) {
                s.setHan(0);
                shielded = true;
            } else {
                s.addWeapon(-step.failDrop());
            }
        }

        Outcome outcome = (s.weapon() < before) ? Outcome.FAIL_DROPPED : Outcome.FAIL_HELD;
        return new Result(outcome, before, s.weapon(), step.soulCost(), chance, useCharm, shielded);
    }
}
