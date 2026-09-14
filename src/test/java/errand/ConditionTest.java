package errand;

import com.fasterxml.jackson.databind.ObjectMapper;
import errand.engine.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static boolean eval(String json, GameState s) throws Exception {
        return ConditionTestAccess.parse(M.readTree(json)).test(s);
    }

    @Test void 플래그_조건() throws Exception {
        GameState s = new GameState();
        assertFalse(eval("{\"flag\":\"원혼_이름을_물음\"}", s));
        s.setFlag("원혼_이름을_물음", true);
        assertTrue(eval("{\"flag\":\"원혼_이름을_물음\"}", s));
        assertFalse(eval("{\"flag\":\"원혼_이름을_물음\",\"is\":false}", s));
    }

    @Test void 스탯_비교() throws Exception {
        GameState s = new GameState();                       // 공덕 50으로 시작
        assertTrue(eval("{\"stat\":\"karma\",\"gte\":50}", s));
        assertFalse(eval("{\"stat\":\"karma\",\"gte\":70}", s));
        s.addKarma(30);
        assertTrue(eval("{\"stat\":\"karma\",\"gte\":70}", s));
    }

    @Test void 카운터_비교() throws Exception {
        GameState s = new GameState();
        assertFalse(eval("{\"counter\":\"혼_태운_횟수\",\"gte\":3}", s));
        s.addCounter("혼_태운_횟수", 3);
        assertTrue(eval("{\"counter\":\"혼_태운_횟수\",\"gte\":3}", s));
    }

    @Test void 논리_조합() throws Exception {
        GameState s = new GameState();
        s.setFlag("원혼_소원_들어줌", true);
        String hiddenEnding = """
            {"all":[
              {"stat":"weapon","gte":5},
              {"flag":"강림_제안_수락"}
            ]}""";
        assertFalse(eval(hiddenEnding, s));
        s.addWeapon(5);
        s.setFlag("강림_제안_수락", true);
        assertTrue(eval(hiddenEnding, s));

        assertTrue(eval("{\"any\":[{\"flag\":\"없음\"},{\"flag\":\"원혼_소원_들어줌\"}]}", s));
        assertTrue(eval("{\"not\":{\"flag\":\"없음\"}}", s));
    }

    @Test void 조건_없으면_항상_참() throws Exception {
        assertTrue(ConditionTestAccess.parse(null).test(new GameState()));
    }

    @Test void 잘못된_조건은_로딩시_실패() {
        StoryLoadException e = assertThrows(StoryLoadException.class,
                () -> eval("{\"stat\":\"karma\"}", new GameState()));
        assertTrue(e.getMessage().contains("비교 연산자가 없습니다"), e.getMessage());

        StoryLoadException e2 = assertThrows(StoryLoadException.class,
                () -> eval("{\"stat\":\"없는스탯\",\"gte\":1}", new GameState()));
        assertTrue(e2.getMessage().contains("알 수 없는 스탯"), e2.getMessage());

        StoryLoadException e3 = assertThrows(StoryLoadException.class,
                () -> eval("{\"모르는키\":1}", new GameState()));
        assertTrue(e3.getMessage().contains("알 수 없는 조건"), e3.getMessage());
    }

    @Test void 공덕은_0에서_100으로_잘린다() {
        GameState s = new GameState();
        s.addKarma(999);
        assertEquals(100, s.karma());
        s.addKarma(-999);
        assertEquals(0, s.karma());
    }

    @Test void 회피율은_공덕나누기5이고_상한은_20() {
        GameState s = new GameState();
        assertEquals(10, s.evasionPercent());     // 공덕 50
        s.addKarma(50);
        assertEquals(20, s.evasionPercent());     // 공덕 100 → 정확히 20%
    }

    @Test void 낫_데미지는_기본10에_단계당3() {
        GameState s = new GameState();
        assertEquals(10, s.weaponDamage());
        s.addWeapon(3);
        assertEquals(19, s.weaponDamage());
        s.addWeapon(99);                          // 상한 +5
        assertEquals(5, s.weapon());
        assertEquals(25, s.weaponDamage());
    }
}
