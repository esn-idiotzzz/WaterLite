param([Parameter(Mandatory=$true)][string]$ToolsDir,[Parameter(Mandatory=$true)][string]$JavaHome,[Parameter(Mandatory=$true)][string]$SigningKey,[Parameter(Mandatory=$true)][string]$PasswordFile,[switch]$Tests)
$ErrorActionPreference = 'Stop'
$taskJava = Join-Path $JavaHome 'bin'
$taskTools = (Resolve-Path -LiteralPath $ToolsDir).Path
$taskBuild = Join-Path $PSScriptRoot 'build/local'
$taskSource = $PSScriptRoot
function Invoke-Checked($exe, $taskArgs) { & $exe @taskArgs; if ($LASTEXITCODE -ne 0) { throw "Build step failed: $exe" } }
$taskClasses = Join-Path $taskBuild 'classes'
New-Item -ItemType Directory -Force $taskClasses, "$taskBuild\generated" | Out-Null
New-Item -ItemType Directory -Force "$taskBuild\dex" | Out-Null
Invoke-Checked "$taskTools\build-tools\aapt.exe" @('package','-f','-m','-J',"$taskBuild\generated",'-M',"$taskSource\app\src\main\AndroidManifest.xml",'-S',"$taskSource\app\src\main\res",'-I',"$taskTools\android.jar",'-F',"$taskBuild\resources.apk")
$taskSources = Get-ChildItem "$taskSource\app\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName
$taskSources += Get-ChildItem "$taskBuild\generated" -Recurse -Filter '*.java' | ForEach-Object FullName
Invoke-Checked "$taskJava\javac.exe" (@('-encoding','UTF-8','--release','8','-classpath',"$taskTools\android.jar;$taskTools\zxing.jar",'-d',$taskClasses) + $taskSources)
Invoke-Checked "$taskJava\jar.exe" @('cf',"$taskBuild\app-classes.jar",'-C',$taskClasses,'.')
Invoke-Checked "$taskJava\java.exe" @('-cp',"$taskTools\r8.jar",'com.android.tools.r8.D8','--release','--min-api','26','--lib',"$taskTools\android.jar",'--output',"$taskBuild\dex", "$taskBuild\app-classes.jar", "$taskTools\zxing.jar")
Copy-Item "$taskBuild\resources.apk" "$taskBuild\unsigned.apk" -Force
Push-Location "$taskBuild\dex"
try { Invoke-Checked "$taskTools\build-tools\aapt.exe" @('add',"$taskBuild\unsigned.apk",'classes.dex') } finally { Pop-Location }
Invoke-Checked "$taskTools\build-tools\zipalign.exe" @('-f','4',"$taskBuild\unsigned.apk", "$taskBuild\aligned.apk")
Invoke-Checked "$taskJava\java.exe" @('-jar',"$taskTools\build-tools\lib\apksigner.jar",'sign','--ks',$SigningKey,'--ks-key-alias','waterlite','--ks-pass',"file:$PasswordFile",'--out',"$taskBuild\WaterLite-1.1.0.apk", "$taskBuild\aligned.apk")
Invoke-Checked "$taskJava\java.exe" @('-jar',"$taskTools\build-tools\lib\apksigner.jar",'verify',"$taskBuild\WaterLite-1.1.0.apk")
if ($Tests) {
    New-Item -ItemType Directory -Force "$taskBuild\test-classes", "$taskBuild\test-dex" | Out-Null
    $taskTests = Get-ChildItem "$taskSource\app\src\androidTest\java" -Recurse -Filter '*.java' | ForEach-Object FullName
    Invoke-Checked "$taskJava\javac.exe" (@('-encoding','UTF-8','--release','8','-classpath',"$taskTools\android.jar;$taskTools\zxing.jar;$taskBuild\app-classes.jar",'-d',"$taskBuild\test-classes") + $taskTests)
    Invoke-Checked "$taskJava\jar.exe" @('cf',"$taskBuild\test-classes.jar",'-C',"$taskBuild\test-classes",'.')
    Invoke-Checked "$taskTools\build-tools\aapt.exe" @('package','-f','-M',"$PSScriptRoot\test\AndroidManifest.xml",'-I',"$taskTools\android.jar",'-F',"$taskBuild\test-resources.apk")
    Invoke-Checked "$taskJava\java.exe" @('-cp',"$taskTools\r8.jar",'com.android.tools.r8.D8','--release','--min-api','26','--lib',"$taskTools\android.jar",'--classpath',"$taskBuild\app-classes.jar",'--classpath',"$taskTools\zxing.jar",'--output',"$taskBuild\test-dex", "$taskBuild\test-classes.jar")
    Copy-Item "$taskBuild\test-resources.apk" "$taskBuild\test-unsigned.apk" -Force
    Push-Location "$taskBuild\test-dex"
    try { Invoke-Checked "$taskTools\build-tools\aapt.exe" @('add',"$taskBuild\test-unsigned.apk",'classes.dex') } finally { Pop-Location }
    Invoke-Checked "$taskTools\build-tools\zipalign.exe" @('-f','4',"$taskBuild\test-unsigned.apk", "$taskBuild\test-aligned.apk")
    Invoke-Checked "$taskJava\java.exe" @('-jar',"$taskTools\build-tools\lib\apksigner.jar",'sign','--ks',$SigningKey,'--ks-key-alias','waterlite','--ks-pass',"file:$PasswordFile",'--out',"$taskBuild\tests.apk", "$taskBuild\test-aligned.apk")
}






