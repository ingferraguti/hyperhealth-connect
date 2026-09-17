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

$seedPath = 'platform/control-plane/src/test/resources/seed/p1-0110-inventory-scale.sql'
$testPath = 'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/InventoryScaleQualificationTest.java'
$manifestPath = 'docs/testing/phase-1-inventory-scale-manifest.yml'
$implementationPath = 'docs/roadmap/phase-1-p1-0110-implementation.md'
$runtimeReportPath = 'platform/control-plane/target/phase1-p1-0110-evidence/inventory-scale-test-report.json'
$surefirePath = 'platform/control-plane/target/surefire-reports/TEST-io.hyperhealth.connect.controlplane.inventory.InventoryScaleQualificationTest.xml'
$required = @($seedPath, $testPath, $manifestPath, $implementationPath, $runtimeReportPath, $surefirePath)
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath $_) })
Add-Check 'p1-0110-artifacts' ($missing.Count -eq 0) $(if ($missing) { "missing: $($missing -join ', ')" } else { 'seed, manifest, implementation record, tests and runtime evidence are present' })

$manifest = Get-Content -LiteralPath $manifestPath -Raw
$seed = Get-Content -LiteralPath $seedPath -Raw
$test = Get-Content -LiteralPath $testPath -Raw
$expectedDigestMatch = [regex]::Match($manifest, '(?m)^\s+sha256:\s+([0-9a-f]{64})\s*$')
$actualDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $seedPath).Hash.ToLowerInvariant()
Add-Check 'seed-digest-declared' $expectedDigestMatch.Success 'manifest contains a lowercase SHA-256 seed digest'
Add-Check 'seed-digest-match' ($expectedDigestMatch.Groups[1].Value -eq $actualDigest) "seed SHA-256 is $actualDigest"

$requiredCardinality = @(
    'tenants: 2',
    'organizations: 2',
    'facilities: 4',
    'applications: 8',
    'endpoints: 12000',
    'runtimeCells: 2',
    'runtimeCellFacilityAssignments: 4'
)
$missingCardinality = @($requiredCardinality | Where-Object { $manifest -notmatch [regex]::Escape($_) })
Add-Check 'seed-cardinality' ($missingCardinality.Count -eq 0) $(if ($missingCardinality) { "missing: $($missingCardinality -join ', ')" } else { '2 tenants, 4 facilities and 12,000 endpoints plus the complete hierarchy are declared' })

Add-Check 'seed-atomicity' ($seed -match '(?im)^BEGIN;\s*$' -and $seed -match '(?im)^COMMIT;\s*$') 'hierarchy, Runtime Cells, assignments and endpoints load in one transaction'
$bypassPatterns = @('session_replication_role', 'DISABLE\s+TRIGGER', 'SET\s+CONSTRAINTS\s+ALL\s+DEFERRED')
$bypasses = @($bypassPatterns | Where-Object { $seed -match $_ })
Add-Check 'constraints-enabled' ($bypasses.Count -eq 0) $(if ($bypasses) { "forbidden constraint bypass: $($bypasses -join ', ')" } else { 'the seed does not disable triggers, replication checks or constraints' })
Add-Check 'synthetic-marker' ($seed -match 'HHC-SYNTHETIC' -and $manifest -match 'classification: synthetic-only') 'corpus is explicitly and visibly synthetic-only'
Add-Check 'bounded-generation' ($seed -match 'generate_series\(1, 12000\)' -and $seed -notmatch '\bCOPY\b.*\bFROM\b') 'Endpoint generation is deterministic, bounded and self-contained'

$requiredMethods = @(
    'loadsDeterministicSyntheticEnterpriseTopologyAndHotScope',
    'paginatesTheHotFacilityExactlyOnceWithBoundedKeysetPagesAndScopedAudit',
    'qualifiesScopedCoveringPlansAndPublishesNonProductionLatencyEvidence'
)
$missingMethods = @($requiredMethods | Where-Object { $test -notmatch [regex]::Escape($_) })
Add-Check 'scale-automation' ($missingMethods.Count -eq 0) $(if ($missingMethods) { "missing: $($missingMethods -join ', ')" } else { 'topology, pagination/audit and plan/latency oracles are executable' })
Add-Check 'plan-oracle' ($test -match 'EXPLAIN \(ANALYZE, BUFFERS, SETTINGS, FORMAT TEXT\)' -and $test -match 'doesNotContain\("Seq Scan on endpoint"\)') 'real execution plans include runtime/buffer evidence and reject Endpoint sequential scans'
Add-Check 'pagination-oracle' ($test -match 'EndpointQuery\.MAXIMUM_LIMIT' -and $test -match 'HOT_FACILITY_ENDPOINTS' -and $test -match 'auditJournal\.verify') 'hot-scope pagination is bounded, complete, unique and audit-verified'
Add-Check 'claim-boundary' ($manifest -match 'productionCapacityClaim: false' -and $test -match 'productionCapacityClaim') 'developer timing is explicitly non-qualifying and cannot become a production capacity claim'

$runtimeReport = Get-Content -LiteralPath $runtimeReportPath -Raw | ConvertFrom-Json
Add-Check 'runtime-report-outcome' ($runtimeReport.outcome -eq 'PASS') 'runtime scale report outcome is PASS'
Add-Check 'runtime-report-cardinality' (
        $runtimeReport.tenants -eq 2 -and
        $runtimeReport.facilities -eq 4 -and
        $runtimeReport.endpoints -eq 12000 -and
        $runtimeReport.hotFacilityEndpoints -eq 6000) 'runtime evidence matches the declared multi-tenant/multi-facility corpus'
Add-Check 'runtime-report-performance' (
        $runtimeReport.measuredSamples -ge 100 -and
        $runtimeReport.warmQueryP95Ms -le 500 -and
        $runtimeReport.productionCapacityClaim -eq $false) "developer regression p95 is $($runtimeReport.warmQueryP95Ms) ms across $($runtimeReport.measuredSamples) samples"
Add-Check 'runtime-report-digest' ($runtimeReport.seedSha256 -eq $actualDigest) 'runtime evidence was generated from the declared seed'

[xml]$surefire = Get-Content -LiteralPath $surefirePath -Raw
$suite = $surefire.testsuite
Add-Check 'surefire-result' ([int]$suite.failures -eq 0 -and [int]$suite.errors -eq 0 -and [int]$suite.tests -ge 3) "$($suite.tests) scale tests, zero failures and zero errors"

$evidenceDirectory = Join-Path $root 'target/phase1-p1-0110-evidence'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$report = [ordered]@{
    schemaVersion = 1
    activity = 'P1-0110'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    gitBranch = (git branch --show-current)
    gitCommit = (git rev-parse HEAD)
    seedSha256 = $actualDigest
    cardinality = [ordered]@{ tenants = 2; facilities = 4; endpoints = 12000; hotFacilityEndpoints = 6000 }
    runtime = $runtimeReport
    checks = $checks
    productionCapacityClaim = $false
    outcome = 'PASS'
}
$report | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $evidenceDirectory 'inventory-scale-gate-report.json') -Encoding utf8
Write-Host "P1-0110 inventory scale gate PASS. Evidence: $evidenceDirectory"
