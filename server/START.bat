@echo off
chcp 65001 >nul
title Astral Nexus RPG - Local Paper Server
cd /d "%~dp0"
echo ================================================
echo       ASTRAL NEXUS RPG - LOCAL SERVER
echo ================================================
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-server.ps1"
if errorlevel 1 (
  echo.
  echo Server stopped because of an error. See the message above.
  pause
)
