package errand;

import errand.economy.Enhancer;
import errand.economy.Shop;
import errand.engine.GameState;
import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class ShopTest {

    private static Shop shop() {
        return new Shop(new Enhancer(new java.util.Random(1)));
    }

    private static GameState rich() {
        GameState s = new GameState();
        s.addSoul(500);
        return s;
    }

    @Test void 천도는_공덕을_올리고_혼력을_쓴다() {
        GameState s = rich();
        var r = shop().buy(s, Shop.Item.CHEONDO);

        assertEquals(Shop.Status.OK, r.status());
        assertEquals(58, s.karma());                       // 50 + 8
        assertEquals(470, s.soul());                       // 500 - 30
        assertEquals(1, s.counter(Shop.CHEONDO_COUNTER));
    }

    @Test void 천도는_막간당_두번까지다() {
        GameState s = rich();
        Shop shop = shop();

        assertEquals(Shop.Status.OK, shop.buy(s, Shop.Item.CHEONDO).status());
        assertEquals(Shop.Status.OK, shop.buy(s, Shop.Item.CHEONDO).status());

        var third = shop.buy(s, Shop.Item.CHEONDO);
        assertEquals(Shop.Status.BLOCKED, third.status());
        assertEquals(66, s.karma(), "막힌 거래로 공덕이 오르면 안 됩니다");
        assertEquals(440, s.soul(), "막힌 거래로 혼력이 줄면 안 됩니다");
    }

    @Test void 다음_막간에는_천도가_다시_열린다() {
        GameState s = rich();
        Shop first = shop();
        first.buy(s, Shop.Item.CHEONDO);
        first.buy(s, Shop.Item.CHEONDO);
        assertEquals(0, first.cheondoRemaining());

        Shop second = shop();                              // 막간이 바뀌면 새 인스턴스
        assertEquals(Shop.CHEONDO_PER_VISIT, second.cheondoRemaining());
        assertEquals(Shop.Status.OK, second.buy(s, Shop.Item.CHEONDO).status());
        assertEquals(3, s.counter(Shop.CHEONDO_COUNTER), "누적 카운터는 막간을 넘어 이어집니다");
    }

    @Test void 숨돌이는_체력을_채운다() {
        GameState s = rich();
        s.addHp(-60);                                      // 체력 40
        var r = shop().buy(s, Shop.Item.SUMDOLI);

        assertEquals(Shop.Status.OK, r.status());
        assertEquals(80, s.hp());
        assertEquals(480, s.soul());
    }

    @Test void 체력이_가득하면_숨돌이는_막힌다() {
        GameState s = rich();
        var r = shop().buy(s, Shop.Item.SUMDOLI);
        assertEquals(Shop.Status.BLOCKED, r.status());
        assertEquals(500, s.soul());
    }

    @Test void 혼력이_모자라면_아무것도_살_수_없다() {
        GameState s = new GameState();                     // 혼력 0
        s.addHp(-50);
        for (Shop.Item item : Shop.Item.values()) {
            if (item == Shop.Item.ENHANCE_WITH_CHARM) continue;   // 부적이 없으면 진열되지 않는다
            assertEquals(Shop.Status.BLOCKED, shop().buy(s, item).status(), item + "이(가) 막히지 않았습니다");
        }
        assertEquals(0, s.soul());
        assertEquals(50, s.karma());
    }

    @Test void 부적_강화는_부적이_있을_때만_진열된다() {
        GameState s = rich();
        assertTrue(shop().offers(s).stream().noneMatch(o -> o.item() == Shop.Item.ENHANCE_WITH_CHARM));

        s.addCharms(1);
        assertTrue(shop().offers(s).stream().anyMatch(o -> o.item() == Shop.Item.ENHANCE_WITH_CHARM));
    }

    @Test void 진열되지_않은_항목을_사면_예외() {
        GameState s = rich();                              // 부적 0
        assertThrows(IllegalArgumentException.class,
                () -> shop().buy(s, Shop.Item.ENHANCE_WITH_CHARM));
    }

    @Test void 강화_실패해도_혼력은_소모된다() {
        GameState s = rich();
        RandomGenerator alwaysFail = new RandomGenerator() {
            @Override public long nextLong() { throw new UnsupportedOperationException(); }
            @Override public int nextInt(int bound) { return 99; }
        };
        var r = new Shop(new Enhancer(alwaysFail)).buy(s, Shop.Item.ENHANCE);

        assertEquals(Shop.Status.OK, r.status(), "시도 자체는 성사된 것입니다");
        assertEquals(Enhancer.Outcome.FAIL_HELD, r.enhanceResult().outcome());
        assertEquals(480, s.soul());
        assertEquals(0, s.weapon());
    }
}
