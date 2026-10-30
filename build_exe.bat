@echo off
chcp 65001 > nul
call "%~dp0build_jar.bat"
if errorlevel 1 exit /b 1

echo [4/4] Building standalone EXE (out\artifacts\FW0_exe\FW0.exe)...
if not exist "out\artifacts\FW0_exe" mkdir "out\artifacts\FW0_exe"
"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /target:winexe /optimize+ /out:out\artifacts\FW0_exe\FW0.exe /resource:out\artifacts\FW0_jar\FW0.jar,FW0.jar tools\Launcher.cs
if errorlevel 1 (
    echo EXE build failed!
    exit /b 1
)

echo Done! Output EXE: out\artifacts\FW0_exe\FW0.exe
