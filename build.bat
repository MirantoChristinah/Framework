@echo off
cls
cd /d "%~dp0"

echo ============================================
echo   Framework Build ^& Deploy - Sprint 1
echo ============================================

:: --- CONFIGURATION DES CHEMINS ---
set TOMCAT_LIB=D:\apache-tomcat-10.1.52-windows-x64\apache-tomcat-10.1.52\lib\servlet-api.jar
set TARGET_LIB_DIR=G:\L2-S4\MrNaina\FrameworkTest\lib
set JAR_NAME=framework.jar
echo [1/4] Nettoyage local...
if exist bin rmdir /s /q bin
mkdir bin

echo [2/4] Compilation globale du Framework...
:: Generation de la liste de tous les fichiers .java (recursif)
dir /s /B src\main\java\*.java > sources.txt

:: Compilation de tous les fichiers listes
javac -d bin -cp "%TOMCAT_LIB%" @sources.txt

if %errorlevel% neq 0 (
    echo.
    echo [ERREUR] La compilation a echoue !
    if exist sources.txt del sources.txt
    pause
    exit /b 1
)
:: Suppression du fichier temporaire si tout s'est bien passe
del sources.txt
echo [OK] Compilation reussie de toutes les classes.

echo [3/4] Creation du JAR...
jar -cf %JAR_NAME% -C bin .
if %errorlevel% neq 0 (
    echo [ERREUR] Impossible de creer le fichier JAR.
    pause
    exit /b 1
)
echo [OK] Fichier %JAR_NAME% cree avec succes.
echo.

echo [4/4] Deploiement vers le projet de test...
:: Verification si le dossier de destination existe, sinon on le cree
if not exist "%TARGET_LIB_DIR%" mkdir "%TARGET_LIB_DIR%"

:: Suppression de l'ancien JAR s'il existe
if exist "%TARGET_LIB_DIR%\%JAR_NAME%" (
    echo Suppression de l'ancien %JAR_NAME% dans le dossier de test...
    del /q "%TARGET_LIB_DIR%\%JAR_NAME%"
)

:: Copie du nouveau JAR
copy /y "%JAR_NAME%" "%TARGET_LIB_DIR%\"

if %errorlevel% neq 0 (
    echo [ERREUR] Impossible de copier le JAR vers le dossier de test.
    echo Verifiez que TomCat ou un autre processus ne bloque pas le fichier.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Termine ! Framework deployee dans :
echo   %TARGET_LIB_DIR%\%JAR_NAME%
echo ============================================
pause