@echo off
echo ================================================
echo Lugg AI Mod - Auto build script for Windows
echo ================================================
echo.

:: Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found!
    echo.
    echo Install Java 17 JDK from here:
    echo https://adoptium.net/temurin/releases/?version=17^&package=jdk^&os=windows
    echo After install restart command prompt and run this script again.
    pause
    exit /b 1
)
echo [OK] Java found
echo.

:: Create wrapper directory
if not exist gradle\wrapper mkdir gradle\wrapper

:: Download gradle-wrapper.jar if missing
if not exist gradle\wrapper\gradle-wrapper.jar (
    echo [INFO] Downloading Gradle Wrapper (~60KB)...
    powershell -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle/wrapper/gradle-wrapper.jar'"
    if %errorlevel% neq 0 (
        echo [INFO] PowerShell failed, trying curl...
        curl -L --ssl-no-revoke https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar -o gradle/wrapper/gradle-wrapper.jar
    )
)

if not exist gradle\wrapper\gradle-wrapper.jar (
    echo.
    echo [ERROR] Cannot download gradle-wrapper.jar automatically!
    echo Download it manually in browser:
    echo https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar
    echo Put the file into gradle\wrapper\ folder, then run this script again.
    pause
    exit /b 1
)
echo [OK] Gradle Wrapper ready
echo.

echo [INFO] Starting mod build...
echo First run will take time, Gradle downloads dependencies ~200MB, please wait.
echo.

call gradlew.bat build

echo.
if %errorlevel% equ 0 (
    echo ================================================
    echo BUILD SUCCESSFUL!
    echo.
    echo Compiled mod is in folder:
    echo %cd%\build\libs\
    echo.
    echo Put the .jar file from that folder into your Minecraft mods folder.
    echo ================================================
) else (
    echo [ERROR] Build failed, see log output above.
)

pause
