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

$migrationDirectory = 'platform/control-plane/src/main/resources/db/migration'
$manifestPath = 'governance/platform-db-schema.yml'
$required = @(
    "$migrationDirectory/V001__expand__platform_core_inventory.sql",
    "$migrationDirectory/V002__migrate__validate_inventory_constraints.sql",
    "$migrationDirectory/V003__expand__inventory_query_indexes.sql",
    "$migrationDirectory/V003__expand__inventory_query_indexes.sql.conf",
    $manifestPath,
    'docs/roadmap/phase-1-p1-0102-implementation.md',
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/PlatformCoreMigrationTest.java'
)
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath $_) })
Add-Check 'p1-0102-artifacts' ($missing.Count -eq 0) $(if ($missing) { "missing: $($missing -join ', ')" } else { 'schema, migration, test, manifest and implementation record present' })

$sqlFiles = @(Get-ChildItem -LiteralPath $migrationDirectory -File -Filter '*.sql' | Sort-Object Name)
$badNames = @($sqlFiles | Where-Object { $_.Name -notmatch '^V[0-9]{3}__(expand|migrate|contract)__[a-z0-9_]+\.sql$' })
$versions = @($sqlFiles | ForEach-Object { [regex]::Match($_.Name, '^V([0-9]{3})__').Groups[1].Value })
Add-Check 'migration-naming' ($badNames.Count -eq 0 -and ($versions -join ',') -eq '001,002,003') 'forward migrations are ordered and phase-labelled V001 through V003'

$destructive = @($sqlFiles | Where-Object { (Get-Content -LiteralPath $_.FullName -Raw) -match '(?im)^\s*(DROP|TRUNCATE)\s' })
Add-Check 'greenfield-nondestructive' ($destructive.Count -eq 0) $(if ($destructive) { "destructive DDL in: $($destructive.Name -join ', ')" } else { 'baseline contains no DROP or TRUNCATE operation' })

$indexSql = Get-Content -LiteralPath "$migrationDirectory/V003__expand__inventory_query_indexes.sql" -Raw
$indexConfig = Get-Content -LiteralPath "$migrationDirectory/V003__expand__inventory_query_indexes.sql.conf" -Raw
$concurrentCount = [regex]::Matches($indexSql, '(?im)^CREATE (UNIQUE )?INDEX CONCURRENTLY').Count
Add-Check 'concurrent-index-policy' ($concurrentCount -eq 10 -and $indexConfig -match '(?m)^executeInTransaction=false\s*$') '10 application indexes are concurrent and the script is non-transactional'

$manifest = Get-Content -LiteralPath $manifestPath -Raw
$manifestFiles = @{}
$migrationMatches = [regex]::Matches(
    $manifest,
    '(?m)^\s+file:\s+(?<file>\S+)\s*\r?\n\s+sha256:\s+(?<sha>[0-9a-f]{64})\s*$'
)
foreach ($match in $migrationMatches) {
    $manifestFiles[$match.Groups['file'].Value] = $match.Groups['sha'].Value
}
$configurationMatch = [regex]::Match(
    $manifest,
    '(?m)^\s+configurationFile:\s+(?<file>\S+)\s*\r?\n\s+configurationSha256:\s+(?<sha>[0-9a-f]{64})\s*$'
)
if ($configurationMatch.Success) {
    $manifestFiles[$configurationMatch.Groups['file'].Value] = $configurationMatch.Groups['sha'].Value
}

$digestFailures = @()
foreach ($path in @($required | Where-Object { $_ -like "$migrationDirectory/*" })) {
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if (-not $manifestFiles.ContainsKey($path)) {
        $digestFailures += "${path}:missing-manifest-entry"
    } elseif ($manifestFiles[$path] -ne $actual) {
        $digestFailures += "${path}:digest-mismatch"
    }
}

$canonicalLines = @(Get-ChildItem -LiteralPath $migrationDirectory -File | Sort-Object Name | ForEach-Object {
    '{0} {1}' -f $_.Name, (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()
})
$canonicalBytes = [Text.Encoding]::UTF8.GetBytes(($canonicalLines -join "`n") + "`n")
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $aggregate = ([BitConverter]::ToString($sha256.ComputeHash($canonicalBytes))).Replace('-', '').ToLowerInvariant()
} finally {
    $sha256.Dispose()
}
$expectedAggregate = [regex]::Match($manifest, '(?m)^aggregateDigest:\s+(?<sha>[0-9a-f]{64})\s*$').Groups['sha'].Value
if ($aggregate -ne $expectedAggregate) { $digestFailures += 'aggregate:digest-mismatch' }
Add-Check 'schema-digests' ($digestFailures.Count -eq 0) $(if ($digestFailures) { $digestFailures -join ', ' } else { "individual and aggregate SHA-256 verified: $aggregate" })

$evidenceDirectory = Join-Path $root 'target/phase1-p1-0102-evidence'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$report = [ordered]@{
    schemaVersion = 1
    activity = 'P1-0102'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    gitBranch = (git branch --show-current)
    gitCommit = (git rev-parse HEAD)
    databaseBaseline = 'PostgreSQL 18.6'
    schemaDigest = $aggregate
    checks = $checks
    outcome = 'PASS'
}
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidenceDirectory 'schema-gate-report.json') -Encoding utf8
Write-Host "P1-0102 schema gate PASS. Evidence: $evidenceDirectory"
