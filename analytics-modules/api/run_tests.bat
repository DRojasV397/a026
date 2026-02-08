@echo off
REM ================================================
REM Script de ejecucion de pruebas para Windows
REM TT 2026-A026 - Sistema de Inteligencia Empresarial
REM ================================================

echo.
echo ================================================
echo   API Analytics - Ejecucion de Pruebas
echo ================================================
echo.

REM Activar entorno virtual si existe
if exist ".venv\Scripts\activate.bat" (
    echo [INFO] Activando entorno virtual...
    call .venv\Scripts\activate.bat
)

REM Verificar argumentos
if "%1"=="" goto all_tests
if "%1"=="--help" goto show_help
if "%1"=="-h" goto show_help
if "%1"=="unit" goto unit_tests
if "%1"=="integration" goto integration_tests
if "%1"=="coverage" goto coverage_tests
if "%1"=="fast" goto fast_tests
if "%1"=="html" goto html_report

:all_tests
echo [INFO] Ejecutando TODAS las pruebas...
python -m pytest tests/ -v --tb=short --color=yes
goto end

:unit_tests
echo [INFO] Ejecutando pruebas UNITARIAS...
python -m pytest tests/unit/ -v --tb=short --color=yes
goto end

:integration_tests
echo [INFO] Ejecutando pruebas de INTEGRACION...
python -m pytest tests/integration/ -v --tb=short --color=yes
goto end

:coverage_tests
echo [INFO] Ejecutando pruebas con COBERTURA...
python -m pytest tests/ --cov=app --cov-report=term-missing --cov-report=html:coverage_report --cov-fail-under=70 -v
echo.
echo [INFO] Reporte de cobertura generado en: coverage_report/index.html
goto end

:fast_tests
echo [INFO] Ejecutando pruebas RAPIDAS...
python -m pytest tests/ -v --tb=short -m "not slow" --color=yes
goto end

:html_report
echo [INFO] Ejecutando pruebas con reporte HTML...
python -m pytest tests/ --html=test_report.html --self-contained-html -v
echo.
echo [INFO] Reporte generado en: test_report.html
goto end

:show_help
echo.
echo Uso: run_tests.bat [opcion]
echo.
echo Opciones:
echo   (sin argumentos)  Ejecutar todas las pruebas
echo   unit              Solo pruebas unitarias
echo   integration       Solo pruebas de integracion
echo   coverage          Con reporte de cobertura
echo   fast              Solo pruebas rapidas
echo   html              Generar reporte HTML
echo   --help, -h        Mostrar esta ayuda
echo.
goto end

:end
echo.
echo ================================================
echo   Ejecucion completada
echo ================================================
