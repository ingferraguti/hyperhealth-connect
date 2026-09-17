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

$matrixPath = 'docs/testing/phase-1-tenant-isolation-matrix.yml'
$implementationPath = 'docs/roadmap/phase-1-p1-0108-implementation.md'
$required = @(
    $matrixPath,
    $implementationPath,
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/JdbcScopedEndpointRepositoryTest.java',
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/ScopedInventoryServiceTest.java',
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/PlatformCoreMigrationTest.java',
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/inventory/api/ScopedEndpointControllerTest.java',
    'platform/control-plane/src/test/java/io/hyperhealth/connect/controlplane/security/OidcSecurityWebIntegrationTest.java'
)
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath $_) })
Add-Check 'p1-0108-artifacts' ($missing.Count -eq 0) $(if ($missing) { "missing: $($missing -join ', ')" } else { 'matrix, implementation record and executable tests are present' })

$matrix = Get-Content -LiteralPath $matrixPath -Raw
$caseCount = [regex]::Matches($matrix, '(?m)^\s+- \{id: [A-Z0-9-]+, layer:').Count
Add-Check 'matrix-size' ($caseCount -ge 40) "$caseCount negative isolation cases are declared"

$requiredLayers = @('repository', 'service', 'api', 'security', 'secret-repository', 'database', 'audit')
$missingLayers = @($requiredLayers | Where-Object { $matrix -notmatch "layer: $([regex]::Escape($_))([,}])" })
Add-Check 'matrix-layers' ($missingLayers.Count -eq 0) $(if ($missingLayers) { "missing layers: $($missingLayers -join ', ')" } else { 'API, service, repository, security, secret, database and audit layers are covered' })

$requiredTargets = @('sibling-facility', 'other-tenant')
$requiredOperations = @('read', 'list', 'create', 'update', 'scope-propagation', 'rbac', 'secret-find', 'secret-export', 'secret-bind', 'ancestry', 'audit')
$missingDimensions = @(
    @($requiredTargets | Where-Object { $matrix -notmatch "target: $([regex]::Escape($_))([,}])" } | ForEach-Object { "target:$_" })
    @($requiredOperations | Where-Object { $matrix -notmatch "operation: $([regex]::Escape($_))([,}])" } | ForEach-Object { "operation:$_" })
)
Add-Check 'matrix-dimensions' ($missingDimensions.Count -eq 0) $(if ($missingDimensions) { "missing dimensions: $($missingDimensions -join ', ')" } else { 'sibling facility, other tenant and every implemented operation are represented' })

$requiredOracles = @('indistinguishable-404', 'empty-result', 'no-side-effect', 'denied-before-persistence', 'db-constraint', 'requester-scope-audit')
$missingOracles = @($requiredOracles | Where-Object { $matrix -notmatch "oracle: $([regex]::Escape($_))([,}])" })
Add-Check 'matrix-oracles' ($missingOracles.Count -eq 0) $(if ($missingOracles) { "missing oracles: $($missingOracles -join ', ')" } else { 'non-enumeration, no-effect, fail-before-persistence, constraints and scoped audit are explicit' })

$testCorpus = @($required | Where-Object { $_ -like '*.java' } | ForEach-Object { Get-Content -LiteralPath $_ -Raw }) -join "`n"
$requiredTestMethods = @(
    'deniesTheCompleteCrossFacilityAndCrossTenantMatrixWithoutSideEffectsOrEnumeration',
    'keepsScopeAsTheFirstParameterOfEveryRepositoryOperation',
    'keepsScopeAsTheFirstParameterOfEveryPublicServiceAndApiOperation',
    'rejectsAConfusedDeputyScopeBeforePersistence',
    'constraintsRejectCrossFacilityApplicationEndpointAndAuditAncestry',
    'humanTokenCreatesScopeAndOverwritesSpoofedHeadersAndRequestAttributes',
    'onlyFacilityOperatorReceivesTheInventoryWriteCapability'
)
$missingTests = @($requiredTestMethods | Where-Object { $testCorpus -notmatch [regex]::Escape($_) })
Add-Check 'matrix-automation' ($missingTests.Count -eq 0) $(if ($missingTests) { "missing test methods: $($missingTests -join ', ')" } else { 'matrix cases are backed by structural, web, PostgreSQL and OIDC tests' })

$productionPaths = @(
    'platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/inventory',
    'platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/secret'
)
$cacheMatches = @(
    Get-ChildItem -LiteralPath $productionPaths -Recurse -File -Filter '*.java' |
        Select-String -Pattern '@Cacheable|@CachePut|@CacheEvict|CacheManager|RedisTemplate|Caffeine|org\.springframework\.cache'
)
Add-Check 'inventory-cache-scope' ($cacheMatches.Count -eq 0) $(if ($cacheMatches) { 'an inventory cache was introduced without a scoped negative matrix' } else { 'no inventory cache exists; no unscoped cache path can bypass persistence scope' })

$scopeFirstSources = @(
    'platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/inventory/ScopedEndpointRepository.java',
    'platform/control-plane/src/main/java/io/hyperhealth/connect/controlplane/secret/SecretReferenceRepository.java'
)
$scopeViolations = @()
foreach ($source in $scopeFirstSources) {
    $sourceText = Get-Content -LiteralPath $source -Raw
    $methodCount = [regex]::Matches($sourceText, '(?ms)^\s+[A-Za-z][A-Za-z0-9_<>, ?]+\s+[a-z][A-Za-z0-9]+\s*\(.*?\)\s*;').Count
    $scopeFirstCount = [regex]::Matches($sourceText, '(?ms)^\s+[A-Za-z][A-Za-z0-9_<>, ?]+\s+[a-z][A-Za-z0-9]+\s*\(\s*VerifiedFacilityScope scope(?:,|\))').Count
    if ($methodCount -eq 0 -or $scopeFirstCount -ne $methodCount) {
        $scopeViolations += "${source}:methods=$methodCount,scopeFirst=$scopeFirstCount"
    }
}
Add-Check 'repository-scope-signatures' ($scopeViolations.Count -eq 0) $(if ($scopeViolations) { "scope-first violations: $($scopeViolations -join ', ')" } else { 'all inventory and secret repository operations are scope-first' })

$evidenceDirectory = Join-Path $root 'target/phase1-p1-0108-evidence'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$report = [ordered]@{
    schemaVersion = 1
    activity = 'P1-0108'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    gitBranch = (git branch --show-current)
    gitCommit = (git rev-parse HEAD)
    matrixCaseCount = $caseCount
    targets = $requiredTargets
    layers = $requiredLayers
    checks = $checks
    outcome = 'PASS'
}
$report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidenceDirectory 'tenant-isolation-gate-report.json') -Encoding utf8
Write-Host "P1-0108 tenant isolation gate PASS. Evidence: $evidenceDirectory"
