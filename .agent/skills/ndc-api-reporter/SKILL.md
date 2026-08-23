# ndc-api-reporter

**Automated execution wrapper and interactive single-file HTML report generator for RestAssured/Allure API suites.**

Runs a Maven/TestNG API test suite and turns its Allure results into
`Execution_QA_Report.html` — a single, dependency-free, self-contained file
with an executive summary, a colorblind-safe status donut chart, dynamic
search/filter tooling, per-category accordions, and a dedicated root-cause
breakdown for every failed or blocked test — ready to view in a browser or
print to PDF.

## What's in this folder

| File | Purpose |
|---|---|
| `generate_report.js` | The report generator. Zero npm dependencies — plain Node `fs`/`path`/`child_process`. |
| `run_tests.sh` | Bash wrapper: loads secrets, runs `mvn clean test`, then generates the report — always, even on failure. |
| `run_tests.ps1` | PowerShell equivalent of `run_tests.sh`, for Windows. |
| `SKILL.md` | This file. |

## Prerequisites (for the project this skill is applied to)

- Java 17+, Maven 3.9+
- Node.js 18+ (only for report generation — the test suite itself has no Node dependency)
- A RestAssured + TestNG + Allure suite: `pom.xml`, `testng.xml`, and a Maven
  run that produces `target/allure-results` (i.e. `allure-testng` +
  `allure-rest-assured` already wired into the suite's `RequestSpecFactory`
  or equivalent, the way this project's own suite does)

This skill does **not** add Allure to a project — it consumes Allure results
a project must already be producing. If `target/allure-results` doesn't
exist yet, the generator exits with a clear error telling you to run the
suite first.

## Applying this skill to a new project (e.g. SeatMap, Booking)

1. Copy this entire `ndc-api-reporter/` folder into the target project, at
   `.agent/skills/ndc-api-reporter/` (or anywhere else you like — nothing
   here is hardcoded to that path except the doc examples below).
2. From that project's own root, run `bash .agent/skills/ndc-api-reporter/run_tests.sh`
   (or the `.ps1` on Windows).

No code changes needed. Every path the generator touches —
`target/allure-results`, `Execution_QA_Report.html`, `pom.xml`, `testng.xml`
— resolves against `PROJECT_ROOT` (default: the current working directory
when you invoke it), never against the skill's own folder. Copy the skill
anywhere, run it from the project you want a report for.

## How to invoke

### For an AI agent

When asked to "run the tests and produce a report" (or similar) for a
RestAssured/TestNG/Allure suite that has this skill available: run
`run_tests.sh` (Linux/macOS/Git Bash) or `run_tests.ps1` (Windows
PowerShell) from that suite's project root, with no arguments. That single
command runs the full suite and regenerates the report — don't invoke
`generate_report.js` directly unless the suite has already been run in this
session and only the report needs refreshing (in that case, `node
.agent/skills/ndc-api-reporter/generate_report.js` from the project root is
enough). Report the resulting KPI counts (total/passed/failed/blocked) and
the output file's path back to the user; don't just say "done."

### For a human engineer

```bash
# One command: run the suite, then regenerate the report (recommended)
bash .agent/skills/ndc-api-reporter/run_tests.sh

# Windows PowerShell equivalent
.\.agent\skills\ndc-api-reporter\run_tests.ps1

# Report only, if target/allure-results is already fresh
node .agent/skills/ndc-api-reporter/generate_report.js
```

Open `Execution_QA_Report.html` directly in a browser. Use the search box,
status/category filters, and Expand/Collapse controls to navigate; use the
floating "Print / Save as PDF" button (or `Ctrl`/`Cmd`+`P`) for a clean PDF
— filters and controls are hidden and every accordion force-expands for
print automatically. The Executive Summary KPIs and donut chart update live
to reflect whatever the current filters leave visible, and "Reset Filters"
restores the overall totals.

## Configuration

All overridable via environment variable, all default to sensible values:

| Variable | Default | Purpose |
|---|---|---|
| `PROJECT_ROOT` | current working directory | Root the generator resolves every other path against |
| `ALLURE_RESULTS_DIR` | `$PROJECT_ROOT/target/allure-results` | Where to read Allure results from |
| `QA_REPORT_OUTPUT` | `$PROJECT_ROOT/Execution_QA_Report.html` | Where to write the report |

### Secrets

Neither script hardcodes a secret name — `run_tests.sh`/`run_tests.ps1`
simply run `mvn clean test` with whatever's already in the environment, plus
anything loaded from an optional `.env` file (`KEY=VALUE` per line) at
`$PROJECT_ROOT/.env`, **without overriding a variable already set** — so a
real secret injected by CI (e.g. a GitHub Actions repository secret exported
as an env var before this runs) always wins over a local `.env` fallback.
Never commit a real `.env` file; add it to `.gitignore` in the consuming
project.

For this project specifically, that's `NDC_API_KEY` (and `NDC_CLIENT_ID` in
CI) — see the root [README.md](../../../README.md#secrets--the-x-api-key).
A different suite (SeatMap, Booking, ...) would export whatever its own
`ConfigManager`-equivalent reads.

## What the report protects against

- **Secrets in payload snapshots.** `x-api-key`, `Authorization`, and
  similarly-named header values are redacted (regex-masked) wherever they
  appear in a captured request/response, including inside the recorded curl
  command — verified by generating a report and confirming zero occurrences
  of the real key.
- **XSS from response bodies.** A captured body or header value is
  HTML-escaped before being embedded — a test that legitimately sends a
  payload like `"<script>...</script>"` (e.g. an XSS-probe test case) can't
  turn into a live tag in the report.
- **Garbled JSON.** Allure's own attachment template HTML-encodes body text
  (`"` → `&quot;`) so it's safe to drop into its own report. This generator
  decodes that back to real JSON before re-escaping/highlighting it, so the
  payload displays as clean, syntax-highlighted JSON rather than double-escaped
  `&amp;quot;` noise.

## Compatibility notes

- **Categories** come from each Allure result's `story` label (falling back
  to `feature`, then `"Uncategorized"`) — i.e. a suite's `@Story` annotations
  (Allure TestNG) become the report's category accordions automatically. A
  suite that doesn't use `@Story`/`@Feature` still works; everything just
  lands in one `"Uncategorized"` section instead of crashing.
- **Severity badges** come from the `severity` label (`@Severity`); default
  to `"normal"` if absent.
- **Status mapping**: Allure's `passed` → Passed, `failed` → Failed,
  everything else (`broken`, `skipped`, `unknown`) → Blocked.
- **Run-sequence IDs** (`RUN-POS-001`, `RUN-NEG-007`, ...) are derived from
  each result's Allure `suite` label containing "positive"/"negative"
  (case-insensitive), falling back to a `GEN` prefix otherwise. These are
  *not* your test-plan's case IDs (e.g. `TC_POS_001` in this project's
  `TestCases.md`) — a single `@Test` method can cover several test-plan rows
  in one data-driven invocation, so there's no honest 1:1 mapping to
  fabricate. The real case ID(s) for a test belong in its `@Description`
  text, which the report shows verbatim and includes in the search index.
- Everything else — the sequence diagram, KPI cards, donut chart, filters —
  is generic across any RestAssured/TestNG/Allure suite; nothing in
  `generate_report.js` is NDC-FlightSearch-specific except the page
  `<title>` and the sequence diagram's actor labels (`buildSequenceDiagramSvg`),
  which are cosmetic and safe to leave as-is or edit per project.
