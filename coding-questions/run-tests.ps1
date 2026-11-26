# Compile everything into out\ and run all tests (Windows PowerShell). Exit code 0 = all passed.
Set-Location $PSScriptRoot
if (Test-Path out) { Remove-Item -Recurse -Force out }
$files = Get-ChildItem -Recurse -Path src, test -Filter *.java | ForEach-Object { $_.FullName }
javac -d out $files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp out AllTests
exit $LASTEXITCODE
