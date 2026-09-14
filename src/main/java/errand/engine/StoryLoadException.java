package errand.engine;

/**
 * 스토리 JSON이 잘못됐을 때 던진다.
 *
 * <p>이 예외는 게임 실행 중이 아니라 <b>로딩 시점</b>에만 발생해야 한다.
 * 오타 난 플래그 이름이나 끊어진 goto를 플레이 도중에 발견하는 일이 없도록,
 * {@link StoryRepository}가 로딩 직후 전수 검사한다.
 */
public class StoryLoadException extends RuntimeException {
    public StoryLoadException(String message) { super(message); }

    public StoryLoadException(String message, Throwable cause) { super(message, cause); }
}
