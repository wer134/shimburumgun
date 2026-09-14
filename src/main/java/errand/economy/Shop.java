package errand.economy;

import errand.engine.GameState;

import java.util.ArrayList;
import java.util.List;

/**
 * 도깨비 상점. GDD 4.2.
 *
 * <p>이 클래스가 게임의 중심 긴장을 만든다. 혼력은 하나뿐인데 쓸 곳이 셋이고,
 * 기대 예산으로는 둘까지만 감당된다. 강화를 밀면 공덕이 멈추고, 공덕을 챙기면
 * 히든 엔딩이 닫히고, 둘 다 밀면 2장 수문장에서 죽는다.
 *
 * <p>인스턴스 하나가 <b>막간 한 번</b>에 대응한다. 천도 횟수 제한이 인스턴스
 * 수명에 묶여 있으므로 막간마다 새로 만들어야 한다.
 */
public final class Shop {

    public static final int CHEONDO_COST = 30;
    public static final int CHEONDO_KARMA = 8;
    public static final int CHEONDO_PER_VISIT = 2;

    public static final int SUMDOLI_COST = 20;
    public static final int SUMDOLI_HP = 40;

    public static final int CHARM_COST = 25;

    /** 천도 누적 횟수. 0이면 3장에서 나태 지옥이 열린다 (GDD 7.4). */
    public static final String CHEONDO_COUNTER = "천도_횟수";

    public enum Item { ENHANCE, ENHANCE_WITH_CHARM, CHEONDO, SUMDOLI, CHARM }

    /**
     * 상점에 진열된 항목 하나.
     *
     * @param blockedReason 살 수 없는 이유. 살 수 있으면 null
     */
    public record Offer(Item item, String label, String detail, int soulCost, String blockedReason) {
        public boolean available() { return blockedReason == null; }
    }

    public enum Status { OK, BLOCKED }

    /**
     * @param enhanceResult 강화를 시도했을 때만 채워진다. 그 외에는 null
     */
    public record Purchase(Status status, Item item, String message, Enhancer.Result enhanceResult) {}

    private final Enhancer enhancer;
    private int cheondoRemaining = CHEONDO_PER_VISIT;

    public Shop(Enhancer enhancer) { this.enhancer = enhancer; }

    public int cheondoRemaining() { return cheondoRemaining; }

    public List<Offer> offers(GameState s) {
        List<Offer> list = new ArrayList<>();
        list.add(enhanceOffer(s, false));
        if (s.charms() > 0) list.add(enhanceOffer(s, true));
        list.add(new Offer(Item.CHEONDO, "천도(薦度)",
                "공덕 +%d  (이번 막간 %d회 남음)".formatted(CHEONDO_KARMA, cheondoRemaining),
                CHEONDO_COST, cheondoBlock(s)));
        list.add(new Offer(Item.SUMDOLI, "숨돌이",
                "체력 +%d".formatted(SUMDOLI_HP),
                SUMDOLI_COST, sumdoliBlock(s)));
        list.add(new Offer(Item.CHARM, "부적 조각",
                "다음 강화 성공률 +%d%%p (상한 %d%%)"
                        .formatted(WeaponTable.CHARM_BONUS_PERCENT, WeaponTable.CHARM_CAP_PERCENT),
                CHARM_COST, s.soul() < CHARM_COST ? "혼력 부족" : null));
        return list;
    }

    private Offer enhanceOffer(GameState s, boolean withCharm) {
        Item item = withCharm ? Item.ENHANCE_WITH_CHARM : Item.ENHANCE;
        String label = withCharm ? "저승낫 강화 (부적 사용)" : "저승낫 강화";

        if (WeaponTable.isMax(s.weapon())) {
            return new Offer(item, label, "이미 최대 단계", 0, "더 갈 수 없음");
        }
        WeaponTable.Step step = WeaponTable.stepFrom(s.weapon());
        int chance = WeaponTable.successPercent(s.weapon(), withCharm);
        String detail = "+%d → +%d   성공 %d%%%s".formatted(
                step.from(), step.to(), chance,
                step.failDrop() > 0 ? "   실패 시 -%d단계".formatted(step.failDrop()) : "   실패해도 유지");

        Enhancer.Outcome blocked = enhancer.check(s, withCharm);
        String reason = switch (blocked) {
            case NOT_ENOUGH_SOUL -> "혼력 부족";
            case NO_CHARM -> "부적 없음";
            case MAX_LEVEL -> "더 갈 수 없음";
            default -> null;
        };
        return new Offer(item, label, detail, step.soulCost(), reason);
    }

    private String cheondoBlock(GameState s) {
        if (cheondoRemaining <= 0) return "이번 막간에는 더 할 수 없음";
        if (s.soul() < CHEONDO_COST) return "혼력 부족";
        if (s.karma() >= GameState.KARMA_MAX) return "공덕이 이미 가득함";
        return null;
    }

    private String sumdoliBlock(GameState s) {
        if (s.soul() < SUMDOLI_COST) return "혼력 부족";
        if (s.hp() >= GameState.HP_MAX) return "체력이 이미 가득함";
        return null;
    }

    public Purchase buy(GameState s, Item item) {
        Offer offer = offers(s).stream()
                .filter(o -> o.item() == item)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("진열되지 않은 항목: " + item));

        if (!offer.available()) {
            return new Purchase(Status.BLOCKED, item, offer.blockedReason(), null);
        }

        return switch (item) {
            case ENHANCE, ENHANCE_WITH_CHARM -> {
                Enhancer.Result r = enhancer.attempt(s, item == Item.ENHANCE_WITH_CHARM);
                yield new Purchase(Status.OK, item, describeEnhance(r), r);
            }
            case CHEONDO -> {
                s.addSoul(-CHEONDO_COST);
                s.addKarma(CHEONDO_KARMA);
                s.addCounter(CHEONDO_COUNTER, 1);
                cheondoRemaining--;
                yield new Purchase(Status.OK, item,
                        "도깨비가 지전을 태운다. 이름 없는 혼 하나가 길을 찾아 간다. (공덕 +%d)"
                                .formatted(CHEONDO_KARMA), null);
            }
            case SUMDOLI -> {
                int before = s.hp();
                s.addSoul(-SUMDOLI_COST);
                s.addHp(SUMDOLI_HP);
                yield new Purchase(Status.OK, item,
                        "숨이 트인다. (체력 %d → %d)".formatted(before, s.hp()), null);
            }
            case CHARM -> {
                s.addSoul(-CHARM_COST);
                s.addCharms(1);
                yield new Purchase(Status.OK, item,
                        "누렇게 바랜 부적 조각 하나. (보유 %d)".formatted(s.charms()), null);
            }
        };
    }

    private static String describeEnhance(Enhancer.Result r) {
        return switch (r.outcome()) {
            case SUCCESS -> "날이 운다. +%d → +%d  「%s」%s".formatted(
                    r.levelBefore(), r.levelAfter(),
                    WeaponTable.tier(r.levelAfter()).name(),
                    unlockNote(r.levelAfter()));
            case FAIL_HELD -> r.hanShielded()
                    ? "불꽃이 튀었으나 낫이 버틴다. 쌓인 한(恨)이 대신 흩어졌다. (+%d 유지)".formatted(r.levelAfter())
                    : "실패. 다행히 단계는 그대로다. (+%d 유지)".formatted(r.levelAfter());
            case FAIL_DROPPED -> "날이 갈라진다. +%d → +%d".formatted(r.levelBefore(), r.levelAfter());
            default -> "시도하지 못했다.";
        };
    }

    private static String unlockNote(int level) {
        String unlock = WeaponTable.tier(level).unlock();
        return "—".equals(unlock) ? "" : "   ▸ " + unlock;
    }
}
