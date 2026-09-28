package errand;

import errand.combat.Bestiary;
import errand.io.Korean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 조사 선택. 하드코딩했다가 "잡귀이 흩어진다"가 나온 적이 있어 테스트로 묶어 둔다.
 *
 * <p>적 이름은 받침 유무가 갈리는 두 종류다 — 잡귀(없음), 수문장(있음). 한쪽에
 * 맞춰 조사를 적으면 반대쪽이 반드시 깨진다.
 */
class KoreanTest {

    @Test void 받침_유무를_가린다() {
        assertFalse(Korean.hasFinalConsonant("잡귀"));
        assertTrue(Korean.hasFinalConsonant("수문장"));
        assertTrue(Korean.hasFinalConsonant("윤동옥"));
        assertFalse(Korean.hasFinalConsonant("도깨비"));
        assertTrue(Korean.hasFinalConsonant("강림"));
        assertFalse(Korean.hasFinalConsonant("사만이"));
    }

    @Test void 주격_조사() {
        assertEquals("잡귀가", Korean.subject("잡귀"));
        assertEquals("수문장이", Korean.subject("수문장"));
    }

    @Test void 보조사와_목적격() {
        assertEquals("잡귀는", Korean.topic("잡귀"));
        assertEquals("수문장은", Korean.topic("수문장"));
        assertEquals("잡귀를", Korean.object("잡귀"));
        assertEquals("수문장을", Korean.object("수문장"));
        assertEquals("잡귀와", Korean.with("잡귀"));
        assertEquals("수문장과", Korean.with("수문장"));
    }

    @Test void 도감에_있는_모든_적_이름에_조사가_붙는다() {
        Bestiary.all().values().forEach(e -> {
            String s = Korean.subject(e.name());
            assertTrue(s.endsWith("가") || s.endsWith("이"),
                    "'%s'에 주격 조사가 제대로 붙지 않았습니다: %s".formatted(e.name(), s));
            assertTrue(s.startsWith(e.name()));
        });
    }

    @Test void 한글이_아니면_받침_없는_쪽으로_본다() {
        assertFalse(Korean.hasFinalConsonant("Boss"));
        assertFalse(Korean.hasFinalConsonant(""));
        assertFalse(Korean.hasFinalConsonant(null));
        assertEquals("Boss가", Korean.subject("Boss"));
    }
}
