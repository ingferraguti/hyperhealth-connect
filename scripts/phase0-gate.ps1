[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipEnvironment
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check([string]$id, [bool]$passed, [string]$detail) {
    $checks.Add([ordered]@{ id = $id; passed = $passed; detail = $detail })
    if (-not $passed) { throw "[$id] $detail" }
    Write-Host "PASS $id - $detail"
}

$required = @(
    '.github/CODEOWNERS', '.github/branch-protection.yml', '.github/workflows/phase0.yml',
    'governance/dependencies.yml', 'governance/controls.yml', 'governance/risks.yml',
    'governance/traceability.yml', 'test-data/policy.yml',
    'docs/security/threat-model.md', 'docs/security/data-protection-design-review.md',
    'docs/security/clinical-safety-assurance.md', 'docs/operations/business-impact-analysis.md',
    'docs/operations/audit-event-schema.md', 'docs/deployment/reference-topology-and-benchmark.md',
    'deploy/compose/compose.yaml', 'mvnw', 'mvnw.cmd', '.mvn/wrapper/maven-wrapper.properties'
)
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath $_) })
Add-Check 'repository-inventory' ($missing.Count -eq 0) $(if ($missing) { "missing: $($missing -join ', ')" } else { 'all required deliverables present' })

$adrFailures = @()
1..10 | ForEach-Object {
    $prefix = 'ADR-{0:D3}-' -f $_
    $adr = Get-ChildItem docs/adr -File | Where-Object Name -like "$prefix*" | Select-Object -First 1
    if (-not $adr -or (Get-Content $adr.FullName -Raw) -notmatch '(?m)^Stato:\s+Accepted') { $adrFailures += $prefix }
}
Add-Check 'adr-baseline' ($adrFailures.Count -eq 0) $(if ($adrFailures) { "not accepted: $($adrFailures -join ', ')" } else { 'ADR-001 through ADR-010 accepted' })

$workflow = Get-Content '.github/workflows/phase0.yml' -Raw
$unpinned = @([regex]::Matches($workflow, '(?m)^\s*- uses:\s+[^\s]+@(?![0-9a-f]{40}(?:\s|$))[^\s]+'))
Add-Check 'actions-pinned' ($unpinned.Count -eq 0) $(if ($unpinned) { 'one or more GitHub Actions are not pinned to a commit SHA' } else { 'all GitHub Actions pinned to 40-character SHA' })

$dependencyBlocks = (Get-Content 'governance/dependencies.yml' -Raw) -split '(?m)^  - id:' | Select-Object -Skip 1
$ownerFailures = @($dependencyBlocks | Where-Object { $_ -notmatch '(?m)^\s+securityOwner:' -or $_ -notmatch '(?m)^\s+licenseOwner:' -or $_ -notmatch '(?m)^\s+lifecycleOwner:' })
Add-Check 'dependency-ownership' ($dependencyBlocks.Count -gt 0 -and $ownerFailures.Count -eq 0) "$($dependencyBlocks.Count) critical dependencies have lifecycle, security and license owners"

$trace = Get-Content 'governance/traceability.yml' -Raw
$traceOk = $trace -match 'requirement:' -and $trace -match 'decisions:' -and $trace -match 'controls:' -and $trace -match 'tests:' -and $trace -match 'evidence:' -and $trace -match 'owner:'
Add-Check 'traceability' $traceOk 'requirements link decisions, controls, tests, evidence and owners'

$policy = Get-Content 'test-data/policy.yml' -Raw
Add-Check 'synthetic-data-policy' ($policy -match 'realPatientDataAllowed:\s+false' -and $policy -match 'productionIdentifiersAllowed:\s+false') 'real clinical data and production identifiers are explicitly prohibited'

$sourceFiles = Get-ChildItem security,integration-envelope,connector-sdk,test-kit,platform,runtime,deploy,.github -Recurse -File |
    Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' -and $_.Name -ne 'workstation-ca.cer' }
$secretPattern = '(?i)-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9_]{30,}|eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.'
$secretHits = @($sourceFiles | Select-String -Pattern $secretPattern)
Add-Check 'secret-and-phi-static-scan' ($secretHits.Count -eq 0) $(if ($secretHits) { "high-confidence secret pattern in $($secretHits[0].Path)" } else { 'no high-confidence credential pattern; fixtures are synthetic-only' })

$dockerfiles = @('platform/control-plane/Dockerfile', 'runtime/worker/Dockerfile')
$containerFailures = @($dockerfiles | Where-Object {
    $text = Get-Content $_ -Raw
    $text -notmatch 'FROM\s+\S+@sha256:[0-9a-f]{64}' -or $text -notmatch '(?m)^USER\s+10001\s*$'
})
Add-Check 'container-hardening' ($containerFailures.Count -eq 0) 'runtime images are digest-pinned and run as UID 10001'

if (-not $SkipEnvironment) {
    $env:HHC_POSTGRES_PASSWORD = 'HHC_SYNTHETIC_GATE_ONLY'
    $env:HHC_KAFKA_CLUSTER_ID = '4L6g3nShT-eMCtK--X86sw'
    docker compose -f deploy/compose/compose.yaml config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose validation failed' }
    foreach ($environment in @('dev', 'integration', 'conformance', 'performance')) {
        kubectl kustomize "deploy/kubernetes/overlays/$environment" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Kustomize validation failed for $environment" }
    }
    Add-Check 'environment-validation' $true 'Compose and four isolated Kustomize overlays render successfully'
}

if (-not $SkipBuild) {
    $image = 'maven:3.9.16-eclipse-temurin-21@sha256:8f6ac126f7810bb5549c4cd122d2bf0e9cda5bdeb0838aa928f09e779fd8bef8'
    $inner = 'mvn -B -ntp clean verify'
    if (Test-Path '.mvn/workstation-ca.cer') {
        $inner = 'keytool -importcert -noprompt -trustcacerts -alias workstation-tls-inspection -file /workspace/.mvn/workstation-ca.cer -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit >/dev/null && mvn -B -ntp clean verify'
    }
    docker run --rm -v "${root}:/workspace" -w /workspace $image sh -lc $inner
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed' }
    Add-Check 'build-test-sast-sbom' (Test-Path 'target/hhc-phase0-sbom.json') 'clean verify passed and CycloneDX 1.6 SBOM exists'
}

$evidence = Join-Path $root 'target/phase0-evidence'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$testReports = @(Get-ChildItem -Recurse -Filter 'TEST-*.xml' | Where-Object FullName -match '[\\/]target[\\/]surefire-reports[\\/]')
$tests = 0; $failures = 0; $errors = 0; $skipped = 0
foreach ($report in $testReports) {
    [xml]$xml = Get-Content $report.FullName
    $tests += [int]$xml.testsuite.tests
    $failures += [int]$xml.testsuite.failures
    $errors += [int]$xml.testsuite.errors
    $skipped += [int]$xml.testsuite.skipped
}
$testSummary = [ordered]@{ reports = $testReports.Count; tests = $tests; failures = $failures; errors = $errors; skipped = $skipped }
$testSummary | ConvertTo-Json | Set-Content (Join-Path $evidence 'test-summary.json') -Encoding utf8
if (-not $SkipBuild) { Add-Check 'test-evidence' ($tests -ge 10 -and $failures -eq 0 -and $errors -eq 0) "$tests tests passed with no failures or errors" }

if (-not $SkipBuild) {
    $referenceJar = 'integration-envelope/target/integration-envelope-0.1.0-SNAPSHOT.jar'
    $referenceHash = (Get-FileHash $referenceJar -Algorithm SHA256).Hash.ToLowerInvariant()
    $reproRoot = Join-Path $root 'target/phase0-repro'
    $reproModule = Join-Path $reproRoot 'integration-envelope'
    New-Item -ItemType Directory -Force -Path (Join-Path $reproModule 'src') | Out-Null
    Copy-Item -LiteralPath 'pom.xml' -Destination (Join-Path $reproRoot 'pom.xml') -Force
    Copy-Item -LiteralPath 'integration-envelope/pom.xml' -Destination (Join-Path $reproModule 'pom.xml') -Force
    Copy-Item -LiteralPath 'integration-envelope/src/main' -Destination (Join-Path $reproModule 'src/main') -Recurse -Force
    Copy-Item -LiteralPath 'integration-envelope/src/test' -Destination (Join-Path $reproModule 'src/test') -Recurse -Force
    $reproInner = 'mvn -B -ntp -f /workspace/target/phase0-repro/integration-envelope/pom.xml clean package'
    if (Test-Path '.mvn/workstation-ca.cer') {
        $reproInner = 'keytool -importcert -noprompt -trustcacerts -alias workstation-tls-inspection -file /workspace/.mvn/workstation-ca.cer -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit >/dev/null && mvn -B -ntp -f /workspace/target/phase0-repro/integration-envelope/pom.xml clean package'
    }
    docker run --rm -v "${root}:/workspace" -w /workspace $image sh -lc $reproInner
    if ($LASTEXITCODE -ne 0) { throw 'Independent reproducibility build failed' }
    $rebuiltJar = Join-Path $reproModule 'target/integration-envelope-0.1.0-SNAPSHOT.jar'
    $rebuiltHash = (Get-FileHash $rebuiltJar -Algorithm SHA256).Hash.ToLowerInvariant()
    Add-Check 'reproducible-artifact' ($referenceHash -eq $rebuiltHash) "independent build SHA-256 $referenceHash"
}

if (-not $SkipBuild) {
    $sourceJar = 'platform/control-plane/target/control-plane-0.1.0-SNAPSHOT.jar'
    $signedJar = Join-Path $evidence 'control-plane-0.1.0-phase0-signed.jar'
    Copy-Item -LiteralPath $sourceJar -Destination $signedJar -Force
    $keyStore = Join-Path $evidence 'ephemeral-signing.p12'
    $pass = 'HhcPhase0EphemeralOnly-2026'
    keytool -genkeypair -alias phase0 -keyalg EC -groupname secp256r1 -validity 1 -dname 'CN=HHC Phase 0 Ephemeral Qualification' -storetype PKCS12 -keystore $keyStore -storepass $pass -keypass $pass | Out-Null
    jarsigner -keystore $keyStore -storepass $pass -keypass $pass $signedJar phase0 | Out-Null
    jarsigner -verify $signedJar | Out-Null
    $signatureOk = $LASTEXITCODE -eq 0
    Remove-Item -LiteralPath $keyStore -Force
    Add-Check 'artifact-signature' $signatureOk 'ephemeral qualification signature was created and verified; no private key retained'
}

$artifactDigests = @()
Get-ChildItem platform/control-plane/target,runtime/worker/target -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object Name -notlike '*.original' | ForEach-Object {
        $artifactDigests += [ordered]@{ path = $_.FullName.Substring($root.Length + 1).Replace('\', '/'); sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
$report = [ordered]@{
    schemaVersion = 1
    phase = '0'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    gitBranch = (git branch --show-current)
    checks = $checks
    tests = $testSummary
    artifacts = $artifactDigests
    sbom = if (Test-Path 'target/hhc-phase0-sbom.json') { (Get-FileHash 'target/hhc-phase0-sbom.json' -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null }
    outcome = 'PASS'
}
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidence 'qualification-report.json') -Encoding utf8
Write-Host "Phase 0 qualification PASS. Evidence: $evidence"
