@echo off
title 始祖ERP框架 - Eclipse启动器
chcp 65001 >nul

:: ── 配置项（按需修改） ──
set ECLIPSE_PATH=D:\Downloads\eclipse-java-2026-03-R-win32-x86_64\eclipse\eclipse.exe
set WORKSPACE_PATH=D:\2026\Java大作业\workspace-erp
set PROJECT_PATH=D:\2026\Java大作业\erp-framework

:: 如果工作区目录不存在则创建
if not exist "%WORKSPACE_PATH%" mkdir "%WORKSPACE_PATH%"

echo ═══════════════════════════════════════════
echo  始祖ERP框架 — Eclipse 启动器
echo ═══════════════════════════════════════════
echo.
echo  Eclipse : %ECLIPSE_PATH%
echo  工作区   : %WORKSPACE_PATH%
echo  项目     : %PROJECT_PATH%
echo.
echo  启动后请执行：File ^> Import ^> Maven ^> Existing Maven Projects
echo  选择：%PROJECT_PATH%
echo.
echo  首次启动后，后续 Eclipse 会自动记住项目。
echo.
echo  按任意键启动 Eclipse（或关闭窗口取消）...
pause >nul

:: 检查 Eclipse 是否存在
if not exist "%ECLIPSE_PATH%" (
    echo.
    echo  [错误] 找不到 Eclipse！
    echo  请修改本文件顶部的 ECLIPSE_PATH 路径。
    echo.
    pause
    exit /b 1
)

:: 启动 Eclipse
echo 正在启动 Eclipse...
start "" "%ECLIPSE_PATH%" -data "%WORKSPACE_PATH%" -showLocation
echo.
echo  Eclipse 已启动！导入项目的步骤写在「导入Eclipse指南.txt」里。
echo.
echo  按任意键关闭本窗口...
pause >nul
