@echo off
cls
cd /d "%~dp0"

echo ============================================
echo Framework Deploy
echo ============================================

set TOMCAT_LIB=D:\apache-tomcat-10.1.52-windows-x64\apache-tomcat-10.1.52\lib\servlet-api.jar
set SOURCE=src\main\java\com\controller\FrontControllerServlet.java

echo [1/3] Nettoyage...
if exist bin rmdir /s /q bin
mkdir bin

echo [2/3] Compilation...
javac -d bin -cp "%TOMCAT_LIB%" "%SOURCE%"

if %errorlevel% neq 0 (
    echo.
    echo [ERREUR] Compilation echouee !
    pause
    exit /b 1
)

echo [OK] Compilation reussie.
echo.

echo [3/3] Creation du JAR...
jar -cf framework.jar -C bin .

echo.
echo ============================================
echo Termine ! Fichier : framework.jar
echo ============================================
pause