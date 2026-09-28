package errand.combat;

/**
 * 적 하나의 스펙. GDD 6.2.
 *
 * @param id          스토리 JSON에서 참조하는 이름
 * @param name        화면에 보이는 이름
 * @param maxHp       체력
 * @param attack      평타 피해
 * @param heavyAttack 강공격 피해. 강공격이 없으면 attack과 같게 둔다
 * @param heavyEvery  몇 번째 공격마다 강공격인가. 0이면 강공격 없음
 */
public record Enemy(
        String id,
        String name,
        int maxHp,
        int attack,
        int heavyAttack,
        int heavyEvery
) {
    public Enemy {
        if (maxHp <= 0) throw new IllegalArgumentException("적 체력은 1 이상이어야 합니다: " + id);
        if (heavyEvery < 0) throw new IllegalArgumentException("heavyEvery는 음수일 수 없습니다: " + id);
    }

    /** {@code attackNumber}는 1부터 센다. */
    public boolean isHeavy(int attackNumber) {
        return heavyEvery > 0 && attackNumber % heavyEvery == 0;
    }

    public int damageOf(int attackNumber) {
        return isHeavy(attackNumber) ? heavyAttack : attack;
    }
}
