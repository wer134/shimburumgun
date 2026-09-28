@echo off
rem
rem 플레이용 실행 스크립트 (Windows).
rem
rem gradlew run 으로도 플레이는 되지만, Gradle의 진행 표시줄이 게임 출력 위에
rem 계속 덮어써서 글자가 깨진다. 여기서는 조용히 빌드한 뒤 만들어진 실행 파일을
rem 직접 띄우므로 화면에 게임 출력만 남는다.
rem
rem 사용법:
rem   play.bat
rem   play.bat --seed=42 --weapon=4 --soul=300
rem
setlocal

cd /d "%~dp0"

rem 콘솔 코드페이지를 UTF-8로. 구형 cmd.exe에서 한글이 깨지는 것을 막는다.
chcp 65001 > nul

echo 빌드 중...
call gradlew.bat --quiet --console=plain installDist
if errorlevel 1 exit /b 1

cls
call build\install\errand-runner\bin\errand-runner.bat %*
