param(
    [Parameter(Position=0)]
    [ValidateSet('build','generate-smoke','generate-pilot','summary','evaluate')]
    [string]$Command = 'build',

    [Parameter(Position=1)]
    [string]$Sample
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root
$Jar = Join-Path $Root 'runner\target\springllm-runner-1.0.0.jar'

function Ensure-Jar {
    if (-not (Test-Path $Jar)) {
        Write-Host 'Runner JAR not found. Building suite...'
        mvn clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
    }
}

switch ($Command) {
    'build' {
        mvn clean package -DskipTests
        exit $LASTEXITCODE
    }
    'generate-smoke' {
        Ensure-Jar
        java -jar $Jar `
          --springllm.command=generate `
          --springllm.work-root=. `
          --springllm.tasks=T01 `
          --springllm.models=M01,M02,M03 `
          --springllm.generations=1
    }
    'generate-pilot' {
        Ensure-Jar
        java -jar $Jar `
          --springllm.command=generate `
          --springllm.work-root=. `
          --springllm.tasks=T01,T02,T03,T04,T05 `
          --springllm.models=M01,M02,M03 `
          --springllm.generations=3
    }
    'summary' {
        Ensure-Jar
        java -jar $Jar --springllm.command=summary --springllm.work-root=.
    }
    'evaluate' {
        Ensure-Jar
        if ([string]::IsNullOrWhiteSpace($Sample)) { throw 'Usage: .\run.ps1 evaluate T01-M01-G01' }
        java -jar $Jar `
          --springllm.command=evaluate `
          --springllm.work-root=. `
          --springllm.sample=$Sample
    }
}
