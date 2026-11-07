# Aplica un perfil grafico a la instancia WIPSS2 sin tocar texturas, modelos, packs ni shaders elegidos.
# Uso (PowerShell, con el juego CERRADO):
#   .\aplicar-perfil.ps1 -Instancia "C:\Users\TU_USUARIO\curseforge\minecraft\Instances\WIP SSJ2 troll" -Perfil Equilibrado
# Perfiles: Calidad | Equilibrado | Rendimiento.  Antes de cambiar nada guarda una copia en <instancia>\respaldo-perfil-<fecha>.
param(
  [Parameter(Mandatory=$true)][string]$Instancia,
  [Parameter(Mandatory=$true)][ValidateSet('Calidad','Equilibrado','Rendimiento')][string]$Perfil
)
$ErrorActionPreference = 'Stop'
$perfiles = @{
  # renderDistance y la calidad de texturas NO cambian entre perfiles. Solo lo que mas cuesta con menos impacto visual.
  'Calidad'     = @{ simulationDistance=10; graphicsMode=1; particles=0; biomeBlendRadius=2; entityDistanceScaling='1.0'; sombras=16 }
  'Equilibrado' = @{ simulationDistance=8;  graphicsMode=1; particles=1; biomeBlendRadius=2; entityDistanceScaling='1.0'; sombras=12 }
  'Rendimiento' = @{ simulationDistance=6;  graphicsMode=0; particles=1; biomeBlendRadius=1; entityDistanceScaling='0.75'; sombras=8 }
}
$p = $perfiles[$Perfil]
$fecha = Get-Date -Format 'yyyyMMdd-HHmmss'
$resp = Join-Path $Instancia "respaldo-perfil-$fecha"
New-Item -ItemType Directory -Force -Path $resp | Out-Null

function Set-Clave([string]$archivo, [string]$clave, [string]$valor, [string]$sep) {
  if (-not (Test-Path $archivo)) { Write-Warning "No existe $archivo"; return }
  $lineas = Get-Content -LiteralPath $archivo
  $hay = $false
  $nuevas = foreach ($l in $lineas) {
    if ($l -match "^\s*$([regex]::Escape($clave))\s*$([regex]::Escape($sep))") { $hay = $true; "$clave$sep$valor" } else { $l }
  }
  if (-not $hay) { $nuevas += "$clave$sep$valor" }
  Set-Content -LiteralPath $archivo -Value $nuevas -Encoding UTF8
}

$opt = Join-Path $Instancia 'options.txt'
$iris = Join-Path $Instancia 'config\iris.properties'
$pc = Join-Path $Instancia 'config\particle_core_config.toml'
foreach ($f in @($opt, $iris, $pc)) { if (Test-Path $f) { Copy-Item $f $resp } }

Set-Clave $opt 'simulationDistance' $p.simulationDistance ':'
Set-Clave $opt 'graphicsMode' $p.graphicsMode ':'
Set-Clave $opt 'particles' $p.particles ':'
Set-Clave $opt 'biomeBlendRadius' $p.biomeBlendRadius ':'
Set-Clave $opt 'entityDistanceScaling' $p.entityDistanceScaling ':'
# Sombras del shader: la distancia de sombras mas alla del render distance no se ve pero se dibuja igual.
Set-Clave $iris 'maxShadowRenderDistance' $p.sombras '='
# Particle Core y AsyncParticles hacian los dos el tick asincrono de particulas: queda solo AsyncParticles.
if (Test-Path $pc) { (Get-Content -LiteralPath $pc) -replace '^asynchronousTicking\s*=.*', 'asynchronousTicking = false' | Set-Content -LiteralPath $pc -Encoding UTF8 }

Write-Host "Perfil $Perfil aplicado. Copia de lo anterior en: $resp"
