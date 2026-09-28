# 테스트 환경

## 필요한 것

**Java 21 이상, 그게 전부다.** Gradle은 설치할 필요가 없다 — 저장소에 wrapper(`gradlew`)가 들어 있어서 알맞은 버전을 자동으로 내려받는다.

```bash
java -version    # 21 이상이면 됨
```

### Java가 없다면

**WSL / Ubuntu / Debian**

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk
```

`JAVA_HOME`은 따로 설정하지 않아도 된다. apt가 `/usr/bin/java`에 링크를 걸고 `gradlew`가 PATH에서 찾는다.

apt에 21이 없다면(Ubuntu 22.04 미만) SDKMAN이 간단하다.

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.5-tem
```

**macOS**: `brew install openjdk@21`
**Windows**: [Temurin 21](https://adoptium.net/temurin/releases/?version=21)

> **WSL에서 Windows에 깔린 Java를 끌어다 쓰지 말 것.** Windows용 실행 파일이라 경로 형식(`C:\` vs `/mnt/c/`)이 충돌해 Gradle이 깨진다. WSL 안에 Linux용 Java를 따로 깔아야 한다.

---

## 플레이

```bash
./play.sh
```

Windows에서는 `play.bat`.

### `./gradlew run`을 쓰지 않는 이유

돌아가긴 하지만 Gradle의 진행 표시줄이 게임 출력 위에 계속 덮어쓴다.

```
  (엔터)
<=========----> 75% EXECUTING [28s]
```

심하면 글자를 먹는다 — `형태를 얻은 모양이다.E` 처럼 `EXECUTING`의 첫 글자가 문장 끝에 박히는 식이다.

`play.sh`는 먼저 조용히 빌드한 뒤 만들어진 실행 파일을 직접 띄우므로 화면에 게임 출력만 남는다. 내부적으로는 이렇게 한다.

```bash
./gradlew --quiet --console=plain installDist
build/install/errand-runner/bin/errand-runner
```

`./gradlew run`을 꼭 쓰겠다면 `--console=plain`을 붙이면 진행 표시줄은 사라진다. 다만 `> Task :run` 같은 줄은 여전히 나온다.

### Windows에서 한글이 깨지면

`play.bat`은 `chcp 65001`을 자동으로 실행하므로 그냥 된다. 직접 돌릴 때는 한 번 바꿔 주면 된다.

```cmd
chcp 65001
gradlew.bat run --console=plain
```

Windows Terminal이나 PowerShell 7 이상에서는 그냥 된다.

---

## 조작

| 상황 | 입력 |
|------|------|
| 지문만 나오고 `(엔터)`가 보일 때 | 엔터 |
| 선택지가 나올 때 | 번호 + 엔터 |
| 도깨비 상점 | 번호로 구매, 마지막 번호로 나가기 |
| 전투 | 1 벤다 / 2 버틴다 / 3 혼을 태운다 |

---

## 지금 플레이 가능한 범위

```
프롤로그 (적패지)
  → 도깨비 상점 ①
  → 1장 대숲의 이름 (아랑 설화) — 선택 3개
  → 잡귀 전투
  → 도깨비 상점 ②
  → 여기까지
```

2~3장(동방삭·사만이·서천꽃밭·업경대)은 아직 없다.

---

## 봐 주면 좋을 것

### 1. 상점의 자원 경쟁

첫 상점에서 혼력 50을 받는다. 강화 한 번이 20이고 천도가 30이니 **둘 중 하나만** 된다. 이 선택이 답답하게 느껴지는지, 아니면 고민할 만한지가 핵심이다.

### 2. 전투 난이도

낫을 강화하지 않고 잡귀와 싸워 보고, 강화하고도 싸워 보라. 낫 **+3부터 선공이 확정**되므로 맞는 횟수가 줄어든다. 체감 차이가 있는지 봐 달라.

"혼을 태운다"는 혼력 15에 공덕 -2다. 쓸 만한 값인지, 아니면 너무 비싸서 아무도 안 쓸 것 같은지.

### 3. 1장 선택의 무게

원혼의 말을 끊고 임무만 처리해도 1장은 클리어된다. 이름을 묻지 않고 지나갈 수도 있다. **그렇게 해도 게임이 벌하지 않는다** — 대가는 3장 업경대에서 청구할 예정이다.

지금은 3장이 없으니 그 대가가 안 보인다. 그래도 "지나쳐도 되는데 찜찜한" 느낌이 드는지 봐 주면 좋다. 안 들면 1장 대사를 더 손대야 한다.

---

## 특정 장면만 보고 싶을 때

초기 상태를 지정해 뒤쪽을 바로 확인할 수 있다.

```bash
./play.sh --weapon=4 --soul=300 --karma=80
```

| 인자 | 뜻 |
|------|-----|
| `--karma=80` | 공덕 (0~100) |
| `--soul=300` | 혼력 |
| `--hp=60` | 체력 (0~100) |
| `--weapon=4` | 낫 강화 단계 (0~5) |
| `--charm=2` | 부적 조각 |
| `--seed=42` | 난수 고정 — 강화·회피·선공이 매번 같게 나온다 |

지정하면 화면에 `[테스트 모드]`가 찍힌다. 조작한 상태로 플레이한 것을 나중에 진짜 밸런스와 착각하지 않도록.

**강화 확률을 체감하고 싶으면** 혼력을 넉넉히 주고 상점에서 계속 갈아 보라.

```bash
./play.sh --soul=500
```

---

## 검증과 테스트

```bash
./gradlew validateStory   # 스토리 JSON만 검증 (2초)
./gradlew smoke           # 자동으로 끝까지 진행, 막히는 곳 확인
./gradlew test            # 단위 테스트 60개
./gradlew check           # 위 셋 전부
```

### 밸런스 표를 보고 싶으면

테스트가 실측 표를 출력한다.

```bash
./gradlew test --rerun-tasks --tests '*BalanceRegressionTest*'   # 강화 도달률
./gradlew test --rerun-tasks --tests '*CombatBalanceTest*'       # 전투 난이도
```

확률표(`WeaponTable`)나 적 수치(`Bestiary`)를 고치면 이 테스트들이 깨진다. 그게 의도다 — 파밍 구간이 없어서 수치를 조금만 건드려도 히든 엔딩 도달 가능성이 흔들린다.

---

## 스토리를 직접 고치고 싶으면

`src/main/resources/story/*.json`만 건드리면 된다. 코드를 볼 필요는 없다. 작성법은 **[STORY_FORMAT.md](STORY_FORMAT.md)** 에 있다.

고친 뒤에는:

```bash
./gradlew validateStory
```

오타 난 플래그, 끊어진 goto, 중복 id를 **한꺼번에** 짚어 준다. 플레이로 찾지 말 것.

---

## CI

`main`과 `claude/**` 브랜치에 푸시하면 GitHub Actions가 검증 → 테스트 → 스모크를 돌린다. 실패하면 테스트 리포트가 artifact로 올라간다.
