#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

for file in .github/CODEOWNERS .github/branch-protection.yml governance/dependencies.yml governance/controls.yml governance/risks.yml governance/traceability.yml test-data/policy.yml; do
  test -s "$file"
done

for number in $(seq -w 1 10); do
  file=$(find docs/adr -maxdepth 1 -name "ADR-0${number}-*.md" -print -quit)
  test -n "$file"
  grep -q '^Stato: Accepted' "$file"
done

if grep -REn 'uses: [^[:space:]]+@([^0-9a-f]|[0-9a-f]{0,39}([[:space:]]|$))' .github/workflows; then
  echo 'GitHub Action not pinned to a full commit SHA' >&2
  exit 1
fi

./mvnw -B -ntp clean verify
test -s target/hhc-phase0-sbom.json

export HHC_POSTGRES_PASSWORD=HHC_SYNTHETIC_GATE_ONLY
export HHC_KAFKA_CLUSTER_ID=4L6g3nShT-eMCtK--X86sw
docker compose -f deploy/compose/compose.yaml config --quiet
for environment in dev integration conformance performance; do
  kubectl kustomize "deploy/kubernetes/overlays/$environment" >/dev/null
done

echo 'Phase 0 qualification PASS'

