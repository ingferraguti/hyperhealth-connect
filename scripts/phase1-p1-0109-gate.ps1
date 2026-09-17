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

$matrixPath = 'docs/testing/phase-1-internationalization-matrix.yml'
$implementationPath = 'docs/roadmap/phase-1-p1-0109-implementation.md'
$testPaths = @(
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/InventoryInternationalizationTest.java',
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/InventoryIdTest.java',
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/JdbcScopedEndpointRepositoryTest.java',
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/PlatformCoreMigrationTest.java'
)
$required = @($matrixPath, $implementationPath) + $testPaths
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath $_) })
Add-Check 'p1-0109-artifacts' ($missing.Count -eq 0) $(if ($missing) { "missing: $($missing -join ', ')" } else { 'matrix, implementation record and executable tests are present' })

$matrix = Get-Content -LiteralPath $matrixPath -Raw
$caseCount = [regex]::Matches($matrix, '(?m)^\s+- \{id: [A-Z0-9-]+, dimension:').Count
Add-Check 'matrix-size' ($caseCount -ge 30) "$caseCount internationalization cases are declared"

$requiredDimensions = @('unicode', 'identifier', 'locale', 'timezone')
$requiredLayers = @('domain', 'service', 'api', 'jdbc', 'postgresql', 'audit')
$missingCoverage = @(
    @($requiredDimensions | Where-Object { $matrix -notmatch "dimension: $([regex]::Escape($_))([,}])" } | ForEach-Object { "dimension:$_" })
    @($requiredLayers | Where-Object { $matrix -notmatch "layer: $([regex]::Escape($_))([,}])" } | ForEach-Object { "layer:$_" })
)
Add-Check 'matrix-coverage' ($missingCoverage.Count -eq 0) $(if ($missingCoverage) { "missing: $($missingCoverage -join ', ')" } else { 'Unicode, identifiers, locale and timezone cross API, domain, service, JDBC, PostgreSQL and audit' })

$testCorpus = @($testPaths | ForEach-Object { Get-Content -LiteralPath $_ -Raw }) -join "`n"
$requiredMethods = @(
    'preservesUnicodeCodePointsWithoutLocaleDependentOrImplicitNormalization',
    'countsSupplementaryCharactersAsCodePointsInsteadOfUtf16CodeUnits',
    'rejectsMalformedUtf16AndControlCharactersBeforePersistence',
    'trimsOnlyBoundaryWhitespaceAndPreservesInternalUnicodeWhitespace',
    'canonicalIdentifiersRoundTripExactlyUnderEuropeanAndTurkishLocales',
    'rejectsWhitespaceCaseVariantsAndUnicodeConfusablesInsteadOfRewritingIdentifiers',
    'roundTripsUnicodeThroughServicePostgresqlAndUtf8JsonWithoutSilentNormalization',
    'preservesTheSameInstantAcrossSessionTimezonesAtADstOverlap',
    'usesUtf8AndTimezoneAwareColumnsForEveryPersistedInstant'
)
$missingMethods = @($requiredMethods | Where-Object { $testCorpus -notmatch [regex]::Escape($_) })
Add-Check 'matrix-automation' ($missingMethods.Count -eq 0) $(if ($missingMethods) { "missing test methods: $($missingMethods -join ', ')" } else { 'all qualification oracles have executable tests' })

$serviceSource = Get-Content -LiteralPath 'platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/inventory/ScopedInventoryService.java' -Raw
Add-Check 'well-formed-unicode' ($serviceSource -match 'requireWellFormedUnicode' -and $serviceSource -match 'Character\.isHighSurrogate' -and $serviceSource -match 'Character\.isLowSurrogate') 'malformed UTF-16 is rejected before persistence'
Add-Check 'no-silent-normalization' ($serviceSource -notmatch 'Normalizer\.normalize') 'the accepted Unicode sequence is not silently normalized'
Add-Check 'code-point-limit' ($serviceSource -match 'codePointCount') 'display-name limit is measured in Unicode code points'

$controllerSource = Get-Content -LiteralPath 'platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/inventory/api/ScopedEndpointController.java' -Raw
Add-Check 'locale-root-lifecycle' ($controllerSource -match 'toUpperCase\(Locale\.ROOT\)') 'lifecycle validation is independent from the JVM default locale'

$idSources = Get-ChildItem -LiteralPath 'platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/inventory' -File -Filter '*Id.java'
$idCorpus = @($idSources | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
$prefixes = @('t-', 'o-', 'f-', 'a-', 'ep-', 'rc-')
$missingPrefixes = @($prefixes | Where-Object { $idCorpus -notmatch [regex]::Escape($_) })
Add-Check 'canonical-id-prefixes' ($missingPrefixes.Count -eq 0) $(if ($missingPrefixes) { "missing: $($missingPrefixes -join ', ')" } else { 'all six technical ID types use explicit ASCII prefixes' })

$migrationPaths = Get-ChildItem -LiteralPath 'platform/control-plane/src/main/resources/db/migration' -File -Filter '*.sql'
$migrationCorpus = @($migrationPaths | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
$bareTimestamp = [regex]::Matches($migrationCorpus, '(?im)\btimestamp\s+(?!with\s+time\s+zone|without\s+time\s+zone|cannot\b|column\b|order\b|constraint\b)[a-z_]')
Add-Check 'timezone-aware-schema' ($bareTimestamp.Count -eq 0) $(if ($bareTimestamp) { 'a migration may contain a bare timestamp domain type' } else { 'domain migrations use timestamptz for persisted instants' })

$evidenceDirectory = Join-Path $root 'target/phase1-p1-0109-evidence'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$report = [ordered]@{
    schemaVersion = 1
    activity = 'P1-0109'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    gitBranch = (git branch --show-current)
    gitCommit = (git rev-parse HEAD)
    matrixCaseCount = $caseCount
    dimensions = $requiredDimensions
    layers = $requiredLayers
    checks = $checks
    outcome = 'PASS'
}
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidenceDirectory 'internationalization-gate-report.json') -Encoding utf8
Write-Host "P1-0109 internationalization gate PASS. Evidence: $evidenceDirectory"
