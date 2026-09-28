#!/usr/bin/env sh
#
# 플레이용 실행 스크립트.
#
# ./gradlew run 으로도 플레이는 되지만, Gradle의 진행 표시줄
# (<====----> 75% EXECUTING) 이 게임 출력 위에 계속 덮어써서 글자가 깨진다.
# 여기서는 먼저 조용히 빌드한 뒤 만들어진 실행 파일을 직접 띄우므로
# 화면에 게임 출력만 남는다.
#
# 사용법:
#   ./play.sh
#   ./play.sh --seed=42 --weapon=4 --soul=300
#
set -e

cd "$(dirname "$0")"

printf '빌드 중...\n'
./gradlew --quiet --console=plain installDist

clear 2>/dev/null || true
exec build/install/errand-runner/bin/errand-runner "$@"
