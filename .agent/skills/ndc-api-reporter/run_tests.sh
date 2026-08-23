#!/usr/bin/env bash
# ndc-api-reporter skill: run the Maven/TestNG suite, then generate the
# executive Execution_QA_Report.html -- always, even if tests fail, so the
# report's "Failed & Blocked Issues" section is exactly when it matters most.
#
# Usage (from the project root you want to test):
#   bash .agent/skills/ndc-api-reporter/run_tests.sh
#   PROJECT_ROOT=/path/to/other/suite bash .agent/skills/ndc-api-reporter/run_tests.sh
#
# Secrets: if a .env file exists at the project root, KEY=VALUE lines from it
# are loaded into the environment -- but only for keys not already set, so a
# real secret injected by CI (e.g. NDC_API_KEY from a GitHub Actions secret)
# always wins over a local .env fallback. Never commit a real .env file.

set -uo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(pwd)}"
ENV_FILE="$PROJECT_ROOT/.env"

if [ -f "$ENV_FILE" ]; then
  echo "Loading environment secrets from $ENV_FILE (existing environment variables take precedence)"
  while IFS='=' read -r key value; do
    key="$(echo "$key" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    [ -z "$key" ] && continue
    case "$key" in \#*) continue ;; esac
    if [ -z "${!key:-}" ]; then
      export "$key=$value"
    fi
  done < "$ENV_FILE"
fi

echo "Running test suite in $PROJECT_ROOT ..."
TEST_EXIT_CODE=0
(cd "$PROJECT_ROOT" && mvn -B clean test) || TEST_EXIT_CODE=$?

echo "Generating executive QA report ..."
PROJECT_ROOT="$PROJECT_ROOT" node "$SKILL_DIR/generate_report.js"

if [ "$TEST_EXIT_CODE" -ne 0 ]; then
  echo "Test suite reported failures (exit code $TEST_EXIT_CODE) -- see Execution_QA_Report.html for details."
fi

exit "$TEST_EXIT_CODE"
