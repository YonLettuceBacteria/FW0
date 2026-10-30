@echo off
chcp 65001 > nul
echo [1/3] Compiling Java sources...
if not exist "out\production\FW0" mkdir "out\production\FW0"
javac -encoding UTF-8 -cp "lib\jlayer-1.0.1.jar" -d "out\production\FW0" src\Main.java
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo [2/3] Preparing fat JAR contents...
if exist "out\jar_staging" rmdir /s /q "out\jar_staging"
mkdir "out\jar_staging"
xcopy /e /y /q "out\production\FW0\*" "out\jar_staging\" > nul
pushd "out\jar_staging"
jar xf "..\..\lib\jlayer-1.0.1.jar"
if exist "META-INF" rmdir /s /q "META-INF"
popd
if exist "voice.mp3" copy /y "voice.mp3" "out\jar_staging\voice.mp3" > nul

echo [3/3] Building executable JAR (out\artifacts\FW0_jar\FW0.jar)...
if not exist "out\artifacts\FW0_jar" mkdir "out\artifacts\FW0_jar"
jar cfm "out\artifacts\FW0_jar\FW0.jar" "src\META-INF\MANIFEST.MF" -C "out\jar_staging" .
rmdir /s /q "out\jar_staging"

echo Done! Output: out\artifacts\FW0_jar\FW0.jar
