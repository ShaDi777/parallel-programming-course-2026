@echo off
REM set JAVA_HOME=C:\Users\Дмитрий\.jdks\openjdk-26.0.2.1
REM set PATH=%JAVA_HOME%\bin;%PATH%

dir /s /b src\main\java\*.java > sources.txt
javac --add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED -d out @sources.txt || exit /b 1

echo === BENCHMARK ===
java -cp out org.labs.Main benchmark plain 1
for %%C in (synchronized synchronizedEmpty lockStriping threadLocal doubleBuffer) do @for %%T in (1 2 4 8 16) do @java -cp out org.labs.Main benchmark %%C %%T
REM for %%C in (doubleBuffer) do @for %%T in (1 2 4 8 16) do @java -cp out org.labs.Main benchmark %%C %%T

echo === STRESS ===
for %%C in (lockStriping threadLocal doubleBuffer doubleBufferWeak) do @java -cp out org.labs.Main stress %%C 4
REM for %%C in (doubleBuffer) do @java -cp out org.labs.Main stress %%C 4

pause