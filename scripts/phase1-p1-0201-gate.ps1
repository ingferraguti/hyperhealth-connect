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

$sourcePath = 'integration-envelope/src/main/java/io/hyperhealth/connect/envelope/IntegrationEnvelope.java'
$testPath = 'integration-envelope/src/test/java/io/hyperhealth/connect/envelope/IntegrationEnvelopeTest.java'
$schemaPath = 'integration-envelope/src/main/resources/schema/hhc-envelope-v1.schema.json'
$examplePath = 'integration-envelope/src/test/resources/envelope/hhc-envelope-v1-synthetic.json'
$manifestPath = 'docs/testing/phase-1-envelope-v1-manifest.yml'
$implementationPath = 'docs/roadmap/phase-1-p1-0201-implementation.md'
$agenticManifestPath = 'governance/agentic-changes/P1-0201.yml'
$surefirePath = 'integration-envelope/target/surefire-reports/TEST-io.hyperhealth.connect.envelope.IntegrationEnvelopeTest.xml'
$required = @(
    $sourcePath,
    $testPath,
    $schemaPath,
    $examplePath,
    $manifestPath,
    $implementationPath,
    $agenticManifestPath,
    $surefirePath
)
$missing = @($required | Where-Object { -not (Test-Path -LiteralPath $_) })
Add-Check 'p1-0201-artifacts' ($missing.Count -eq 0) $(if ($missing) { "missing: $($missing -join ', ')" } else { 'implementation, schema, fixture, tests, manifests, record and Surefire result are present' })

$source = Get-Content -LiteralPath $sourcePath -Raw
$tests = Get-Content -LiteralPath $testPath -Raw
$manifest = Get-Content -LiteralPath $manifestPath -Raw
$agenticManifest = Get-Content -LiteralPath $agenticManifestPath -Raw
$schema = Get-Content -LiteralPath $schemaPath -Raw | ConvertFrom-Json
$example = Get-Content -LiteralPath $examplePath -Raw | ConvertFrom-Json

$expectedDigestMatch = [regex]::Match($manifest, '(?m)^\s+jsonSchemaSha256:\s+([0-9a-f]{64})\s*$')
$actualDigest = (Get-FileHash -Algorithm SHA256 -LiteralPath $schemaPath).Hash.ToLowerInvariant()
Add-Check 'schema-digest-declared' $expectedDigestMatch.Success 'manifest declares a lowercase SHA-256 schema digest'
Add-Check 'schema-digest-match' ($expectedDigestMatch.Groups[1].Value -eq $actualDigest) "schema SHA-256 is $actualDigest"

Add-Check 'json-schema-dialect' ($schema.'$schema' -eq 'https://json-schema.org/draft/2020-12/schema') 'contract uses JSON Schema Draft 2020-12'
Add-Check 'schema-stable-id' ($schema.'$id' -eq 'urn:hhc:schema:integration-envelope:v1') 'schema uses a stable logical URN independent from a mutable branch URL'
Add-Check 'schema-fail-closed' (
        $schema.additionalProperties -eq $false -and
        $schema.properties.scope.additionalProperties -eq $false -and
        $schema.properties.processing.additionalProperties -eq $false -and
        $schema.properties.time.additionalProperties -eq $false) 'unknown core and nested properties are rejected'
$expectedRequired = @('envelopeVersion', 'messageId', 'correlationId', 'scope', 'processing', 'time')
$missingRequired = @($expectedRequired | Where-Object { $_ -notin @($schema.required) })
Add-Check 'required-core' ($missingRequired.Count -eq 0 -and @($schema.required).Count -eq $expectedRequired.Count) $(if ($missingRequired) { "missing: $($missingRequired -join ', ')" } else { 'identity, correlation, scope, processing and time are mandatory' })
Add-Check 'causation-optional' ('causationId' -notin @($schema.required) -and $null -ne $schema.properties.causationId) 'causation is represented but optional for root events'
Add-Check 'version-marker' ($schema.properties.envelopeVersion.const -eq 'hhc-envelope/v1' -and $example.envelopeVersion -eq 'hhc-envelope/v1') 'schema and fixture carry the exact v1 marker'

$messagePattern = [regex]::new($schema.'$defs'.messageId.pattern)
$tenantPattern = [regex]::new($schema.properties.scope.properties.tenantId.pattern)
$facilityPattern = [regex]::new($schema.properties.scope.properties.facilityId.pattern)
$flowPattern = [regex]::new($schema.properties.processing.properties.flowId.pattern)
$correlationPattern = [regex]::new($schema.properties.correlationId.pattern)
Add-Check 'synthetic-example-identifiers' (
        $messagePattern.IsMatch([string]$example.messageId) -and
        $messagePattern.IsMatch([string]$example.causationId) -and
        $tenantPattern.IsMatch([string]$example.scope.tenantId) -and
        $facilityPattern.IsMatch([string]$example.scope.facilityId) -and
        $flowPattern.IsMatch([string]$example.processing.flowId) -and
        $correlationPattern.IsMatch([string]$example.correlationId)) 'synthetic example conforms to every identifier shape'
$exampleText = Get-Content -LiteralPath $examplePath -Raw
$forbiddenClinicalFields = @('patientId', 'mrn', 'fiscalCode', 'accessionNumber', 'payload', 'diagnosis')
$presentClinicalFields = @($forbiddenClinicalFields | Where-Object { $exampleText -match ('"' + [regex]::Escape($_) + '"\s*:') })
Add-Check 'synthetic-minimization' ($exampleText -match 'HHC-SYNTHETIC' -and $presentClinicalFields.Count -eq 0) 'fixture is visibly synthetic and contains no clinical or payload field'

$requiredTypes = @('MessageId', 'CorrelationId', 'Scope', 'Processing', 'Timing')
$missingTypes = @($requiredTypes | Where-Object { $source -notmatch ("record " + [regex]::Escape($_) + "\(") })
Add-Check 'typed-domain-contract' ($missingTypes.Count -eq 0) $(if ($missingTypes) { "missing: $($missingTypes -join ', ')" } else { 'identity, correlation, scope, Flow and time use immutable record types' })
Add-Check 'causation-cycle-guard' ($source -match 'causationId\.filter\(messageId::equals\)' -and $source -match 'must not reference the same message') 'direct self-causation is rejected'
Add-Check 'uuid-policy' ($source -match 'value\.version\(\) != 4 && value\.version\(\) != 7' -and $source -match 'value\.variant\(\) != 2') 'message IDs are RFC 9562 UUIDv4 or UUIDv7'
Add-Check 'scope-prefix-policy' (
        $source -match '"t-"' -and
        $source -match '"f-"' -and
        $source -match 'requireNonNil\(value, name\)' -and
        $schema.properties.scope.properties.tenantId.not.const -eq 't-00000000-0000-0000-0000-000000000000' -and
        $schema.properties.scope.properties.facilityId.not.const -eq 'f-00000000-0000-0000-0000-000000000000') 'Tenant and Facility use the canonical typed non-nil UUID forms accepted by WP1-01'
$floatingAliasPattern = [string]$schema.properties.processing.properties.flowVersion.not.pattern
Add-Check 'flow-version-pinned' (
        $source -match 'FLOATING_VERSION_ALIASES' -and
        $source -match 'toLowerCase\(Locale\.ROOT\)' -and
        $floatingAliasPattern -match '\[Ll\].*\[Cc\].*\[Hh\].*\[Ss\]') 'floating Flow aliases are rejected case-insensitively in domain and schema'
$prematureFieldPatterns = @(
    '\bString\s+payloadReference\b',
    '\bString\s+payloadSha256\b',
    '\bString\s+checksum\b',
    '\bAckPolicy\s+ackPolicy\b',
    '\bInstant\s+durableAt\b'
)
$prematureFields = @($prematureFieldPatterns | Where-Object { $source -match $_ })
Add-Check 'no-premature-durability-fields' ($prematureFields.Count -eq 0) 'P1-0201 does not pre-implement raw, checksum or ACK semantics'

$requiredMethods = @(
    'createsRootEnvelopeWithImmutableScopedIdentity',
    'preservesExplicitCausationWithoutConflatingItWithCorrelation',
    'acceptsRfc9562UuidVersion4AndVersion7MessageIds',
    'rejectsUnsupportedOrNonCanonicalMessageIds',
    'rejectsUnsupportedEnvelopeVersionFailClosed',
    'rejectsSelfCausation',
    'rejectsUnboundedOrUnsafeCorrelationValues',
    'rejectsCrossTypeAndNonCanonicalScopeIdentifiers',
    'acceptsCanonicalNonNilInventoryIdsSupportedByTheControlPlane',
    'rejectsNonCanonicalOrMutableFlowReferences',
    'rejectsMissingReceiveTime'
)
$missingMethods = @($requiredMethods | Where-Object { $tests -notmatch [regex]::Escape($_) })
Add-Check 'negative-and-positive-automation' ($missingMethods.Count -eq 0) $(if ($missingMethods) { "missing: $($missingMethods -join ', ')" } else { 'positive identity plus version, causation, scope, correlation, Flow and time negative paths are executable' })

Add-Check 'agentic-boundary' ($agenticManifest -match 'classification: A3' -and $agenticManifest -match 'pending-human-review' -and $agenticManifest -match 'does not qualify checksum, durability, ACK behavior') 'A3 provenance and pending human review are explicit'

[xml]$surefire = Get-Content -LiteralPath $surefirePath -Raw
$suite = $surefire.testsuite
Add-Check 'surefire-result' ([int]$suite.failures -eq 0 -and [int]$suite.errors -eq 0 -and [int]$suite.tests -ge 13) "$($suite.tests) envelope tests, zero failures and zero errors"

$evidenceDirectory = Join-Path $root 'target/phase1-p1-0201-evidence'
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$report = [ordered]@{
    schemaVersion = 1
    activity = 'P1-0201'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    gitBranch = (git branch --show-current)
    gitCommit = (git rev-parse HEAD)
    envelopeVersion = 'hhc-envelope/v1'
    jsonSchemaSha256 = $actualDigest
    tests = [int]$suite.tests
    checks = $checks
    durabilityQualified = $false
    ackBehaviorQualified = $false
    productionQualified = $false
    outcome = 'PASS'
}
$report | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $evidenceDirectory 'envelope-v1-gate-report.json') -Encoding utf8
Write-Host "P1-0201 envelope v1 gate PASS. Evidence: $evidenceDirectory"
