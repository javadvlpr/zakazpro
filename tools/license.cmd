@echo off
chcp 65001 > nul
java -cp "%~dp0..\target\classes" com.mebelsex.license.tool.LicenseCli %*
