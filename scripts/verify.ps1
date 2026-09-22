# Smoke test against a running instance (local or cloud) from PowerShell.
#   .\scripts\verify.ps1
#   .\scripts\verify.ps1 -Base https://my-app.example -Mode production
param(
    [string]$Base = "http://localhost:8080",
    [string]$Mode = "development"
)

$script:pass = 0
$script:fail = 0

function Check($name, $url, [int]$expected, $fragment = "") {
    try {
        $r = Invoke-WebRequest -Uri ($Base + $url) -UseBasicParsing -ErrorAction Stop
        $status = [int]$r.StatusCode
        $body = [string]$r.Content
        if ($r.Content -is [byte[]]) { $body = "" }
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        $body = ""
    }
    if ($status -eq $expected -and ($fragment -eq "" -or $body.Contains($fragment))) {
        Write-Host "PASS  $name ($status)" -ForegroundColor Green; $script:pass++
    } else {
        Write-Host "FAIL  $name : expected $expected $fragment, got $status" -ForegroundColor Red; $script:fail++
    }
}

Write-Host "Verifying $Base (mode: $Mode)"
Check "index page"         "/"                  200 "Lambda Web Framework"
Check "styles.css"         "/styles.css"        200 ":root"
Check "app.js"             "/app.js"            200 "fetch("
Check "logo.png"           "/images/logo.png"   200
Check "hello with name"    "/hello?name=Pedro"  200 "Pedro"
Check "hello without name" "/hello"             200 "world"
Check "pi"                 "/pi"                200 "3.14159"
Check "sum (2 params)"     "/api/sum?a=2&b=3"   200 '"sum":5.0'
Check "sum missing param"  "/api/sum?a=2"       400
Check "info"               "/api/info"          200 ('"appEnv":"' + $Mode + '"')
Check "unknown route"      "/unknown"           404
if ($Mode -eq "production") { Check "shutdown disabled" "/shutdown" 404 }

Write-Host "----"
Write-Host "$script:pass passed, $script:fail failed"
if ($script:fail -gt 0) { exit 1 }
