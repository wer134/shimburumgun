package errand.io;

/**
 * 한국어 조사 선택.
 *
 * <p>이름을 문장에 끼워 넣을 때 조사를 하드코딩하면 반드시 틀린다. "잡귀"는
 * 받침이 없어 "잡귀<b>가</b>"이고 "수문장"은 받침이 있어 "수문장<b>이</b>"인데,
 * 한쪽에 맞춰 적어 두면 다른 쪽이 깨진다. 적이 늘어날수록 이런 자리가 늘어나므로
 * 규칙을 한 곳에 둔다.
 *
 * <p>판정은 마지막 글자의 종성 유무로 한다. 한글 음절은
 * {@code 0xAC00 + (초성×21 + 중성)×28 + 종성} 로 배치돼 있어
 * {@code (코드 - 0xAC00) % 28}이 0이면 받침이 없다.
 */
public final class Korean {

    private static final char HANGUL_FIRST = 0xAC00;
    private static final char HANGUL_LAST = 0xD7A3;
    private static final int JONGSEONG_COUNT = 28;

    private Korean() {}

    /** 마지막 글자에 받침이 있는가. 한글이 아니면 없는 것으로 본다. */
    public static boolean hasFinalConsonant(String word) {
        if (word == null || word.isEmpty()) return false;
        char last = word.charAt(word.length() - 1);
        if (last < HANGUL_FIRST || last > HANGUL_LAST) return false;
        return (last - HANGUL_FIRST) % JONGSEONG_COUNT != 0;
    }

    /** 주격 조사 — 잡귀<b>가</b> / 수문장<b>이</b>. */
    public static String subject(String word) {
        return word + (hasFinalConsonant(word) ? "이" : "가");
    }

    /** 보조사 — 잡귀<b>는</b> / 수문장<b>은</b>. */
    public static String topic(String word) {
        return word + (hasFinalConsonant(word) ? "은" : "는");
    }

    /** 목적격 조사 — 잡귀<b>를</b> / 수문장<b>을</b>. */
    public static String object(String word) {
        return word + (hasFinalConsonant(word) ? "을" : "를");
    }

    /** 접속 조사 — 잡귀<b>와</b> / 수문장<b>과</b>. */
    public static String with(String word) {
        return word + (hasFinalConsonant(word) ? "과" : "와");
    }
}
