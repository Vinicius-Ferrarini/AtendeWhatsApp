<#
    Captura a notificacao de chamada do WhatsApp sem precisar compilar nada.
    Responde P-1 e parte de P-3 (specs/001-atende-pai/plan.md secao 7).

    Uso: rodar COM A CHAMADA TOCANDO (ou em andamento, para P-3).
        powershell -ExecutionPolicy Bypass -File capturar.ps1 -Rotulo p1-bloqueado
#>
param(
    [string]$Rotulo = "captura"
)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$saida = Join-Path $PSScriptRoot "fixtures"
New-Item -ItemType Directory -Force -Path $saida | Out-Null

$dispositivos = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
if (-not $dispositivos) {
    Write-Error "Nenhum aparelho autorizado. Rode: $adb devices"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$bruto = Join-Path $saida "dumpsys-$Rotulo-$stamp.txt"

Write-Host "Capturando dumpsys notification..." -ForegroundColor Cyan
& $adb shell dumpsys notification --noredact | Out-File -FilePath $bruto -Encoding utf8

# Estado do aparelho no instante da captura: separa o caso bloqueado do desbloqueado (P-1).
$telaLigada = (& $adb shell dumpsys power | Select-String -Pattern "mWakefulness=" | Select-Object -First 1).Line
$bloqueada  = (& $adb shell dumpsys window | Select-String -Pattern "mDreamingLockscreen=|KeyguardServiceDelegate" | Select-Object -First 2) -join " | "

$conteudo = Get-Content $bruto -Raw
$temWhatsApp = $conteudo -match "com\.whatsapp"
$temCategoriaCall = $conteudo -match "category=call"

Write-Host ""
Write-Host "Arquivo: $bruto"
Write-Host "Estado:  $($telaLigada.Trim())"
Write-Host "Keyguard: $bloqueada"
Write-Host ""
Write-Host "P-1 ---------------------------------------" -ForegroundColor Yellow
Write-Host ("  notificacao do com.whatsapp presente : {0}" -f $temWhatsApp)
Write-Host ("  algum category=call presente         : {0}" -f $temCategoriaCall)

if ($temWhatsApp) {
    Write-Host ""
    Write-Host "Trechos do com.whatsapp (conferir category, actions, flags):" -ForegroundColor Yellow
    Select-String -Path $bruto -Pattern "com\.whatsapp" -Context 4, 30 |
        Select-Object -First 3 |
        ForEach-Object { $_.Context.PreContext + $_.Line + $_.Context.PostContext } |
        Where-Object { $_ -match "pkg=|category=|actions|flags=|android\.title|android\.text|fullscreen|ongoing|channel" } |
        ForEach-Object { "    $($_.Trim())" }
}
Write-Host ""
Write-Host "Dump completo guardado. Reveja o arquivo antes de versionar: contem nome de quem ligou." -ForegroundColor DarkYellow
