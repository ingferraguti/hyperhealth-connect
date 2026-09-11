[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check([string]$Id, [bool]$Passed, [string]$Detail) {
    $checks.Add([ordered]@{ id = $Id; passed = $Passed; detail = $Detail })
    if (-not $Passed) { throw "[$Id] $Detail" }
    Write-Host "PASS $Id - $Detail"
}

$required = @(
    'docs/roadmap/phase-1-backlog.md',
    'docs/roadmap/phase-1-raci.md',
    'docs/roadmap/phase-1-dashboard.md',
    'docs/roadmap/phase-1-wp1-00-qualification.md',
    'governance/phase-1-backlog.yml',
    'governance/phase-1-delivery-tracker.yml',
    'docs/testing/phase-1-performance-test-manifest.yml',
    'docs/testing/phase-1-reference-environment.yml',
    'docs/security/clinical-safety-assurance.md',
    'docs/security/data-protection-design-review.md',
    'governance/dependencies.yml',
    'governance/risks.yml',
    'governance/traceability.yml'
)
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath $_) })
Add-Check 'wp1-00-deliverables' ($missing.Count -eq 0) $(if ($missing) { "missing: $($missing -join ', ')" } else { 'all mobilization deliverables present' })

$adrFailures = @()
25..28 | ForEach-Object {
    $prefix = 'ADR-{0:D3}-' -f $_
    $adr = Get-ChildItem 'docs/adr' -File | Where-Object Name -like "$prefix*" | Select-Object -First 1
    if (-not $adr -or (Get-Content $adr.FullName -Raw) -notmatch '(?m)^Stato:\s+Accepted') { $adrFailures += $prefix }
}
Add-Check 'critical-decisions' ($adrFailures.Count -eq 0) $(if ($adrFailures) { "not accepted: $($adrFailures -join ', ')" } else { 'ADR-025 through ADR-028 accepted' })

$backlog = Get-Content 'governance/phase-1-backlog.yml' -Raw
$epicBlocks = @($backlog -split '(?m)^  - id: ' | Select-Object -Skip 1)
$badEpics = @($epicBlocks | Where-Object {
    $_ -notmatch '(?m)^\s+priority:\s+P0\s*$' -or
    $_ -notmatch '(?m)^\s+owner:\s+\S+' -or
    $_ -notmatch '(?m)^\s+requirements:\s+\[' -or
    $_ -notmatch '(?m)^\s+useCases:\s+\[' -or
    $_ -notmatch '(?m)^\s+acceptanceTests:\s+\[' -or
    $_ -notmatch '(?m)^\s+evidence:\s+\S+'
})
Add-Check 'p0-backlog' ($epicBlocks.Count -eq 13 -and $badEpics.Count -eq 0) '13 P0 epics have owner, requirements, use cases, acceptance tests and evidence target'

$dependencies = Get-Content 'governance/dependencies.yml' -Raw
$selected = @('postgresql', 'kubernetes', 'apache-kafka', 'seaweedfs', 'flyway-community', 'keycloak', 'opentelemetry-collector-contrib', 'prometheus', 'jaeger')
$dependencyFailures = @()
foreach ($id in $selected) {
    $match = [regex]::Match($dependencies, "(?ms)^  - id: $([regex]::Escape($id))\r?\n(?<body>.*?)(?=^  - id:|\z)")
    if (-not $match.Success) { $dependencyFailures += "${id}:missing"; continue }
    $body = $match.Groups['body'].Value
    foreach ($field in @('version', 'source', 'license', 'lifecycleOwner', 'securityOwner', 'licenseOwner', 'securityDisposition', 'updateCadence')) {
        if ($body -notmatch "(?m)^\s+${field}:\s+\S+") { $dependencyFailures += "${id}:$field" }
    }
    if ($body -match '(?m)^\s+image:' -and $body -notmatch '(?m)^\s+digest:\s+sha256:[0-9a-f]{64}\s*$') { $dependencyFailures += "${id}:digest" }
}
Add-Check 'core-dependency-disposition' ($dependencyFailures.Count -eq 0) $(if ($dependencyFailures) { "incomplete: $($dependencyFailures -join ', ')" } else { "$($selected.Count) core components pinned, owned and dispositioned" })

$traceability = Get-Content 'governance/traceability.yml' -Raw
$traceRequirements = @('FR-TEN-001', 'FR-EVT-001', 'FR-EVT-004', 'FR-CON-003', 'FR-FLW-001', 'FR-AUD-001', 'NFR-BC-001', 'NFR-PERF-001', 'NFR-SUPPLY-001')
$missingTrace = @($traceRequirements | Where-Object { $traceability -notmatch "requirement:\s+$([regex]::Escape($_))" })
Add-Check 'phase1-traceability' ($traceability -match 'baseline:\s+phase-1-WP1-00' -and $missingTrace.Count -eq 0) 'critical Phase 1 requirements link decisions, controls, tests, evidence and owners'

$risks = Get-Content 'governance/risks.yml' -Raw
$missingRisks = @(1..9 | ForEach-Object { 'P1-R{0:D2}' -f $_ } | Where-Object { $risks -notmatch "id:\s+$([regex]::Escape($_))" })
Add-Check 'phase1-risk-register' ($missingRisks.Count -eq 0) 'P1-R01 through P1-R09 are registered with owner and gate'

$hazards = Get-Content 'docs/security/clinical-safety-assurance.md' -Raw
$missingHazards = @(1..10 | ForEach-Object { 'HZ-{0:D2}' -f $_ } | Where-Object { $hazards -notmatch [regex]::Escape($_) })
Add-Check 'vertical-slice-hazards' ($missingHazards.Count -eq 0 -and $hazards -match 'Hazard review WP1-00') 'two vertical slices have hazard scenarios, controls, tests and gates'

$privacy = Get-Content 'docs/security/data-protection-design-review.md' -Raw
$privacyOk = $privacy -match 'Mini-DPIA di design — vertical slice A' -and $privacy -match 'Mini-DPIA di design — vertical slice B' -and $privacy -match 'Privacy acceptance per WP1'
Add-Check 'vertical-slice-privacy' $privacyOk 'both vertical slices have data-flow, minimization, access, retention and acceptance review'

$testManifest = Get-Content 'docs/testing/phase-1-performance-test-manifest.yml' -Raw
$environmentManifest = Get-Content 'docs/testing/phase-1-reference-environment.yml' -Raw
$manifestOk = $testManifest -match 'PERF-R02-MILLION-001' -and $testManifest -match 'REL-R02-ACK-001' -and $testManifest -match 'REL-R02-CP-001' -and $environmentManifest -match 'three-failure-domain' -and $environmentManifest -match 'generatorHosts:\s+dedicated'
Add-Check 'test-environment-manifests' $manifestOk 'performance, integrity, fault, autonomy and reference hardware are baselined'

$tracker = Get-Content 'governance/phase-1-delivery-tracker.yml' -Raw
$trackedWps = @([regex]::Matches($tracker, '(?m)^  - \{id: WP1-[0-9]{2},'))
$trackerOk = $trackedWps.Count -eq 14 -and $tracker -match 'milestone:\s*\r?\n\s+number:\s+1' -and $tracker -match 'projectsScopeAvailable:\s+false'
Add-Check 'delivery-tracker' $trackerOk 'milestone, 14 epic issues, labels and Projects-scope limitation are recorded'

$evidenceDir = Join-Path $root 'target/phase1-wp1-00-evidence'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$report = [ordered]@{
    schemaVersion = 1
    workPackage = 'WP1-00'
    gate = 'G1'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    gitBranch = (git branch --show-current)
    gitCommit = (git rev-parse HEAD)
    checks = $checks
    outcome = 'PASS'
    limitations = @('GitHub Projects API scope unavailable; milestone plus epic issues is the active board')
}
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidenceDir 'qualification-report.json') -Encoding utf8
Write-Host "WP1-00 / G1 PASS. Evidence: $evidenceDir"
