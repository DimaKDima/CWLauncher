@echo off
rem Компиляция CWLauncher (только JDK, без внешних зависимостей)
setlocal
set "JAVAC=%ProgramFiles%\Java\jdk-17\bin\javac.exe"
if not exist "%JAVAC%" set "JAVAC=javac"
if not exist build mkdir build
dir /s /b src\*.java > sources.rsp
"%JAVAC%" -nowarn -encoding UTF-8 -d build @sources.rsp
if errorlevel 1 (
  echo.
  echo ОШИБКА КОМПИЛЯЦИИ
  exit /b 1
)
echo Сборка успешна: build\
endlocal
