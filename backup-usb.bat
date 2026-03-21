@echo off
echo ========================================
echo   BACKUP PiscineTimer vers cle USB
echo ========================================

:: Detecte la lettre de la cle USB automatiquement
:: Modifie la lettre si besoin (D:, E:, F:, G:...)
set USB=D:
set DEST=%USB%\PiscineTimer_Backup

echo.
echo Destination : %DEST%
echo.

:: Cree les dossiers de destination
mkdir "%DEST%\MontreGalaxie" 2>nul
mkdir "%DEST%\PiscineTimerPhone" 2>nul

echo Copie de MontreGalaxie...
xcopy "E:\MontreGalaxie" "%DEST%\MontreGalaxie" ^
  /E /H /Y /I ^
  /EXCLUDE:E:\MontreGalaxie\backup-exclusions.txt

echo.
echo Copie de PiscineTimerPhone...
xcopy "E:\PiscineTimerPhone" "%DEST%\PiscineTimerPhone" ^
  /E /H /Y /I ^
  /EXCLUDE:E:\MontreGalaxie\backup-exclusions.txt

echo.
echo Copie de Claude Code (conversations)...
mkdir "%DEST%\claude_config" 2>nul
xcopy "C:\Users\patau\.claude" "%DEST%\claude_config" ^
  /E /H /Y /I

echo.
echo ========================================
echo   BACKUP TERMINE !
echo ========================================
echo Verifier : %DEST%
echo.
pause
