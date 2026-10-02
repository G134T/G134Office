$ErrorActionPreference = "Stop"
$cpp = $PSScriptRoot
$build = Join-Path $cpp "build"

$vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path -LiteralPath $vswhere)) {
    $cmd = Get-Command vswhere.exe -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "vswhere.exe не найден" }
    $vswhere = $cmd.Source
}

$require = @("-latest", "-products", "*", "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64")
$install = & $vswhere @require -property installationPath
$version = & $vswhere @require -property installationVersion
$line = & $vswhere @require -property catalog_productLineVersion
if (-not $install -or -not $version) { throw "Инструменты C++ Visual Studio не найдены" }
$major = ($version -split "\.")[0]
if (-not $major) { throw "Не удалось определить генератор Visual Studio" }

$year = $null
if ($line -match "^\d{4}$") {
    $year = $line
} else {
    foreach ($row in @(cmake --help)) {
        if ($row -match "Visual Studio $major (\d{4})") {
            $year = $Matches[1]
            break
        }
    }
}
if (-not $year) { throw "CMake не знает генератор Visual Studio $major (линия $line)" }

$cmakeCmd = $null
$cmake = Get-Command cmake.exe -ErrorAction SilentlyContinue
if ($cmake) {
    $cmakeCmd = $cmake.Source
} else {
    $bundled = Join-Path $install "Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
    if (Test-Path -LiteralPath $bundled) { $cmakeCmd = $bundled }
}
if (-not $cmakeCmd) { throw "cmake.exe не найден" }

Write-Host "Visual Studio $major $year"
& $cmakeCmd -S $cpp -B $build -G "Visual Studio $major $year" -A x64
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $cmakeCmd --build $build --config Release
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
