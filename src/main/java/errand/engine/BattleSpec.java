package errand.engine;

/**
 * 전투 스토리렛에 붙는 정보. JSON 표기는 다음과 같다.
 *
 * <pre>
 *   "scene": "battle",
 *   "battle": {
 *     "enemy": "잡귀",
 *     "onVictory": "ch1_07_after",
 *     "onDefeat": "ch1_07_scattered"
 *   }
 * </pre>
 *
 * <p>승패에 따라 갈 곳을 <b>스토리가 정한다.</b> 엔진은 패배를 하드 게임오버로
 * 처리하지 않는다 — 사이길에서는 죽음이 이미 일어난 일이므로, 흩어진 혼을 어떻게
 * 할지는 서사가 결정할 문제다.
 *
 * @param enemyId   {@code errand.combat.Bestiary}의 적 id
 * @param onVictory 이겼을 때 갈 스토리렛
 * @param onDefeat  졌을 때 갈 스토리렛
 */
public record BattleSpec(String enemyId, String onVictory, String onDefeat) {
}
