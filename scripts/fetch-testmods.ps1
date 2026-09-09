# Downloads the third-party mod jars the dev client needs into run/mods/.
#
# Compiling needs none of this - Gradle resolves every compileOnly dependency from a real Maven
# repo. But FML's mod discovery only scans run/mods and jar-in-jar, so the dev CLIENT still needs
# the real jars on disk. Versions here must match gradle.properties.
#
# Usage:  pwsh -File scripts/fetch-testmods.ps1

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$mods = Join-Path $PSScriptRoot '..\run\mods'
New-Item -ItemType Directory -Force -Path $mods | Out-Null

# Modrinth CDN direct links (project / version). Keep in sync with gradle.properties.
$files = @{
    'curios-neoforge-9.5.1+1.21.1.jar'   = 'https://cdn.modrinth.com/data/vvuO3ImH/versions/wLh5wDfd/curios-neoforge-9.5.1%2B1.21.1.jar'
    'relics-1.21.1-0.12.8.jar'            = 'https://cdn.modrinth.com/data/OCJRPujW/versions/WKEe9sPL/relics-1.21.1-0.12.8.jar'
    'artifacts-neoforge-13.2.5.jar'       = 'https://cdn.modrinth.com/data/P0Mu4wcQ/versions/lMfuK0o7/artifacts-neoforge-13.2.5.jar'
    'OctoLib-NEOFORGE-0.6.2+1.21.jar'     = 'https://cdn.modrinth.com/data/RH2KUdKJ/versions/yVCCi6TK/OctoLib-NEOFORGE-0.6.2%2B1.21.jar'
}

foreach ($name in $files.Keys) {
    $dst = Join-Path $mods $name
    if (Test-Path $dst) { Write-Host "have  $name"; continue }
    Write-Host "get   $name"
    Invoke-WebRequest -Uri $files[$name] -OutFile $dst
}

Write-Host ""
Write-Host "Apotheosis family (Apotheosis, Placebo, ApothicAttributes) + apothic_compats are on"
Write-Host "CurseForge / Shadows-of-Fire's maven, not Modrinth. Grab them from run/mods of the"
Write-Host "foxs-tweaks project, or from CurseForge, matching the versions in gradle.properties."
