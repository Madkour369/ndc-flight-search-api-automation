#!/usr/bin/env node
'use strict';

/**
 * Generates Execution_QA_Report.html — a single self-contained, executive-grade
 * HTML report — from the Allure results produced by `mvn test`
 * (target/allure-results/*.json + attachments).
 *
 * Zero npm dependencies by design: parses the Allure result/attachment files
 * with plain fs + regex rather than pulling in an HTML/XML parser, so this
 * script runs anywhere Node does, with no `npm install` step.
 *
 * Usage:
 *   node generate_html_report.js
 *   ALLURE_RESULTS_DIR=./other/path QA_REPORT_OUTPUT=./out.html node generate_html_report.js
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const RESULTS_DIR = process.env.ALLURE_RESULTS_DIR || path.join(__dirname, 'target', 'allure-results');
const OUTPUT_FILE = process.env.QA_REPORT_OUTPUT || path.join(__dirname, 'Execution_QA_Report.html');
const POM_FILE = path.join(__dirname, 'pom.xml');
const TESTNG_XML_FILE = path.join(__dirname, 'testng.xml');

// Status palette — fixed/reserved status colors (never themed), colorblind-safe
// and pre-validated. See the dataviz skill's reference palette.
const STATUS_COLORS = {
  passed: '#0ca30c',
  failed: '#d03b3b',
  blocked: '#fab219',
};

// -----------------------------------------------------------------------
// Small utilities
// -----------------------------------------------------------------------

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/**
 * Masks credential-shaped header values (x-api-key, Authorization, etc.)
 * wherever they appear in attachment content — including inside the curl
 * command allure-rest-assured also records — so a report meant for wide
 * (executive) distribution never carries a live secret.
 */
function redactSecrets(text) {
  return String(text).replace(
    /((?:x-api-key|api[-_]?key|authorization|x-auth-token)\s*:\s*)([^\s<&'"]+)/gi,
    (_m, prefix) => `${prefix}••••••••(redacted)`
  );
}

/**
 * Lightweight, dependency-free JSON syntax highlighting for the request/
 * response body blocks -- no highlight.js/Prism CDN, consistent with the
 * report's "single self-contained file" design (same reasoning as choosing
 * inline SVG over Mermaid for the architecture diagram). Re-serializes with
 * 2-space indentation for consistent formatting, then wraps each token in a
 * `<span>` via one pass over the already-HTML-escaped text so the tagging
 * can't reintroduce unescaped `<`/`&`. Returns null (never throws) when the
 * text isn't valid JSON -- e.g. a curl command, or a body a redaction pass
 * turned into invalid JSON -- so the caller can fall back to plain text
 * rather than mis-render it.
 */
function syntaxHighlightJson(rawText) {
  let pretty;
  try {
    pretty = JSON.stringify(JSON.parse(rawText), null, 2);
  } catch (err) {
    return null;
  }

  const escaped = escapeHtml(pretty);
  const tokenPattern = /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\btrue\b|\bfalse\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g;

  return escaped.replace(tokenPattern, (match) => {
    let cls = 'json-number';
    if (/^"/.test(match)) {
      cls = /:\s*$/.test(match) ? 'json-key' : 'json-string';
    } else if (match === 'true' || match === 'false') {
      cls = 'json-boolean';
    } else if (match === 'null') {
      cls = 'json-null';
    }
    return `<span class="${cls}">${match}</span>`;
  });
}

function readJsonSafe(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (err) {
    console.warn(`  ! Skipping unreadable file: ${path.basename(filePath)} (${err.message})`);
    return null;
  }
}

function labelValue(labels, name) {
  const found = (labels || []).find((l) => l.name === name);
  return found ? found.value : null;
}

function formatDuration(ms) {
  if (!Number.isFinite(ms) || ms < 0) return 'n/a';
  const totalSeconds = ms / 1000;
  if (totalSeconds < 60) return `${totalSeconds.toFixed(1)}s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = Math.round(totalSeconds % 60);
  return `${minutes}m ${seconds}s`;
}

function formatTimestamp(ms) {
  if (!Number.isFinite(ms)) return 'n/a';
  return new Date(ms).toISOString().replace('T', ' ').replace('Z', ' UTC');
}

// -----------------------------------------------------------------------
// Environment metadata detection — everything here is derived from real,
// present-on-disk project files or the actual machine that ran the tests
// (never hardcoded/guessed), with an honest fallback string when a source
// isn't available (e.g. `mvn` not on PATH on the machine generating the
// report).
// -----------------------------------------------------------------------

function readPomProperty(name) {
  try {
    const pom = fs.readFileSync(POM_FILE, 'utf8');
    const match = pom.match(new RegExp(`<${name}>([^<]+)</${name}>`));
    return match ? match[1].trim() : null;
  } catch (err) {
    return null;
  }
}

function detectTestFramework() {
  const parts = [
    ['TestNG', readPomProperty('testng.version')],
    ['RestAssured', readPomProperty('rest-assured.version')],
    ['Allure', readPomProperty('allure.version')],
  ]
    .filter(([, version]) => Boolean(version))
    .map(([name, version]) => `${name} ${version}`);
  return parts.length ? parts.join(' · ') : 'TestNG + RestAssured + Allure';
}

function detectRuntimeVersions() {
  let jdk = readPomProperty('maven.compiler.target') ? `Java ${readPomProperty('maven.compiler.target')}` : 'Java (unknown)';
  let maven = 'Maven (not detected on this machine)';

  try {
    const javaOut = execSync('java -version 2>&1', { encoding: 'utf8' });
    const javaMatch = javaOut.match(/version "([^"]+)"/);
    if (javaMatch) jdk = `Java ${javaMatch[1]}`;
  } catch (err) {
    // Fall back to the pom.xml-derived value above -- the machine
    // generating the report may not have `java` on PATH under this name.
  }

  try {
    const mavenOut = execSync('mvn -v 2>&1', { encoding: 'utf8' });
    const mavenMatch = mavenOut.match(/Apache Maven ([^\s]+)/);
    if (mavenMatch) maven = `Maven ${mavenMatch[1]}`;
  } catch (err) {
    // `mvn` isn't installed/on PATH here -- say so rather than guessing a
    // version number that can't be verified.
  }

  return `${jdk} · ${maven}`;
}

function detectExecutionMode() {
  try {
    const xml = fs.readFileSync(TESTNG_XML_FILE, 'utf8');
    const parallelMatch = xml.match(/<suite[^>]*\sparallel="([^"]+)"/);
    const threadMatch = xml.match(/<suite[^>]*\sthread-count="(\d+)"/);
    if (parallelMatch) {
      const threads = threadMatch ? ` (${threadMatch[1]} threads)` : '';
      return `Parallel by ${parallelMatch[1]}${threads}`;
    }
    return 'Sequential';
  } catch (err) {
    return 'Sequential';
  }
}

/**
 * Pulls supplier/credentialsSelector out of an actual captured request body
 * rather than hardcoding them, so this stays correct if the default test
 * data ever changes suppliers.
 */
function detectSupplierConfig(cases) {
  const withBody = cases.find((c) => c.request && c.request.body && /"supplier"/i.test(c.request.body));
  if (!withBody) return 'N/A';

  const supplierMatch = withBody.request.body.match(/"supplier"\s*:\s*"([^"]+)"/i);
  const selectorMatch = withBody.request.body.match(/"credentialsSelector"\s*:\s*"([^"]+)"/i);
  if (!supplierMatch) return 'N/A';

  return selectorMatch ? `${supplierMatch[1]} / ${selectorMatch[1]}` : supplierMatch[1];
}

function pickCategoryIcon(category) {
  const c = (category || '').toLowerCase();
  if (/auth|security|token|credential/.test(c)) return '\u{1F510}'; // lock
  if (/business|rule|policy|conflict/.test(c)) return '⚖️'; // scales
  if (/valid|schema|contract|format|airport/.test(c)) return '\u{1F9EA}'; // test tube
  if (/perf|load|latency|speed/.test(c)) return '⚡'; // bolt
  if (/success|functional|search|flow|debug|structural|compat/.test(c)) return '✅'; // check mark
  return '\u{1F4C1}'; // folder
}

function statusIcon(status) {
  if (status === 'Passed') return '✓';
  if (status === 'Failed') return '✗';
  return '⚠'; // Blocked
}

// -----------------------------------------------------------------------
// Allure attachment parsing (allure-rest-assured's fixed HTML template)
// -----------------------------------------------------------------------

/**
 * The real http-request.ftl/http-response.ftl templates (verified directly
 * against the allure-attachments:2.29.0 jar -- see the git history for that
 * investigation) render each header as its own `<div>Name: Value</div>`
 * line, nested inside one outer `<div>...</div>` wrapper. That's real
 * structural markup from Allure itself, not attacker-controlled content --
 * unlike the Body block (arbitrary request/response JSON), which genuinely
 * needs HTML-escaping before display. Passing the headers block's raw inner
 * HTML through escapeHtml() at render time (as a prior pass did, to close
 * an XSS gap in the Body block) turned those legitimate wrapper `<div>`
 * tags into literal visible text -- "<div>Content-Type: ...</div>" showing
 * up as a line of the report instead of being invisible structural markup.
 * The fix belongs here, at extraction time: pull each header's plain text
 * out of its `<div>` wrapper now, so what render time escapes is already
 * clean "Name: Value" text with no markup left to leak.
 */
function parseHeaderLines(headersHtml) {
  const lines = [];
  const perHeaderDiv = /<div>([\s\S]*?)<\/div>/g;
  let match;
  while ((match = perHeaderDiv.exec(headersHtml)) !== null) {
    lines.push(match[1].trim());
  }
  // Fall back to the raw (stripped-of-tags) text if the block didn't match
  // the expected per-header <div> shape at all, rather than silently
  // dropping it.
  return lines.length ? lines : [headersHtml.replace(/<[^>]+>/g, '').trim()].filter(Boolean);
}

function extractHttpParts(html) {
  const lineMatch = html.match(/^<div>([\s\S]*?)<\/div>/);
  const bodyMatch = html.match(/<h4>Body<\/h4>[\s\S]*?<pre class="preformated-text">([\s\S]*?)<\/pre>/);
  const headersMatch = html.match(/<h4>Headers<\/h4>\s*<div>([\s\S]*?)<\/div>\s*(?=<h4>|$)/);

  const headerLines = headersMatch ? parseHeaderLines(headersMatch[1]) : [];

  return {
    line: lineMatch ? lineMatch[1].trim() : '',
    body: bodyMatch ? redactSecrets(bodyMatch[1].trim()) : '',
    headers: headerLines.length ? redactSecrets(headerLines.join('\n')) : '',
  };
}

function loadAttachment(sourceFile) {
  const filePath = path.join(RESULTS_DIR, sourceFile);
  if (!fs.existsSync(filePath)) return null;
  const raw = fs.readFileSync(filePath, 'utf8');
  return extractHttpParts(raw);
}

/**
 * Walks a test result's attachments AND every nested step's attachments,
 * recursively (a step can itself contain sub-steps). This is not optional
 * politeness -- Allure's own AllureLifecycle#addAttachment Javadoc says it
 * "adds attachment to current running test OR STEP", and AllureRestAssured
 * fires while whatever @Step wraps the HTTP call is the active context (see
 * base.BaseTest's @Step-annotated search()/searchRaw()/etc. helpers). So in
 * a real run, every request/response attachment lives one level down inside
 * that step's own `attachments` array, never in the top-level result's
 * `attachments` array. Reading only `result.attachments` (the previous
 * version of this function) would silently find nothing for every single
 * test case.
 */
function collectAttachmentsDeep(node) {
  const own = Array.isArray(node.attachments) ? node.attachments : [];
  const nested = Array.isArray(node.steps) ? node.steps.flatMap(collectAttachmentsDeep) : [];
  return own.concat(nested);
}

// -----------------------------------------------------------------------
// Load + normalize test results
// -----------------------------------------------------------------------

function mapStatus(allureStatus) {
  if (allureStatus === 'passed') return 'Passed';
  if (allureStatus === 'failed') return 'Failed';
  return 'Blocked'; // broken / skipped / unknown
}

function loadTestCases() {
  if (!fs.existsSync(RESULTS_DIR)) {
    console.error(`Allure results directory not found: ${RESULTS_DIR}`);
    console.error('Run the test suite first (e.g. "mvn test") to produce target/allure-results.');
    process.exit(1);
  }

  const resultFiles = fs
    .readdirSync(RESULTS_DIR)
    .filter((f) => f.endsWith('-result.json'));

  if (resultFiles.length === 0) {
    console.error(`No *-result.json files found in ${RESULTS_DIR}.`);
    console.error('Run the test suite first (e.g. "mvn test") to produce test results.');
    process.exit(1);
  }

  const cases = resultFiles
    .map((file) => readJsonSafe(path.join(RESULTS_DIR, file)))
    .filter(Boolean)
    .map((result) => {
      const status = mapStatus(result.status);
      const category = labelValue(result.labels, 'story') || labelValue(result.labels, 'feature') || 'Uncategorized';
      const severity = labelValue(result.labels, 'severity') || 'normal';
      const suite = labelValue(result.labels, 'suite') || '';
      const scenarioParam = (result.parameters || []).find((p) => p.name === 'arg0');

      // AllureRestAssured names the request attachment "Request" and (unless
      // explicitly overridden, which RequestSpecFactory doesn't do) names the
      // response attachment after the HTTP status line (e.g. "200 OK") --
      // not literally "Response". So "first non-request attachment" is the
      // correct match, not a search for the word "response".
      const allAttachments = collectAttachmentsDeep(result);
      const requestAttachment = allAttachments.find((a) => /request/i.test(a.name));
      const responseAttachment = allAttachments.find((a) => a !== requestAttachment && !/request/i.test(a.name));

      return {
        uuid: result.uuid,
        suite,
        name: result.name || result.fullName,
        scenario: scenarioParam ? scenarioParam.value : null,
        description: result.description || '',
        category,
        severity,
        status,
        start: result.start,
        stop: result.stop,
        duration: (result.stop || 0) - (result.start || 0),
        statusMessage: result.statusDetails && result.statusDetails.message,
        statusTrace: result.statusDetails && result.statusDetails.trace,
        request: requestAttachment ? loadAttachment(requestAttachment.source) : null,
        response: responseAttachment ? loadAttachment(responseAttachment.source) : null,
      };
    })
    .sort((a, b) => (a.start || 0) - (b.start || 0));

  // A run-sequence label (RUN-POS-001, RUN-NEG-007, ...), numbered in
  // execution order within each suite -- deliberately NOT styled as a
  // TestCases.md ID. Several test methods here are data-driven across
  // multiple TestCases.md rows in one call (e.g. one @Test covers
  // TC_DATE_001, 002, 005, 006...), so there's no honest 1:1
  // invocation-to-TC-ID mapping to fabricate. The real TC ID(s) for a test
  // are in its description paragraph below (from @Description), shown
  // verbatim and included in the search index, so searching "TC_FLD_002"
  // still finds the right card.
  const counters = {};
  cases.forEach((tc) => {
    const prefix = /positive/i.test(tc.suite) ? 'POS' : /negative/i.test(tc.suite) ? 'NEG' : 'GEN';
    counters[prefix] = (counters[prefix] || 0) + 1;
    tc.id = `RUN-${prefix}-${String(counters[prefix]).padStart(3, '0')}`;
  });

  return cases;
}

// -----------------------------------------------------------------------
// KPI + donut chart
// -----------------------------------------------------------------------

function computeKpis(cases) {
  const total = cases.length;
  const passed = cases.filter((c) => c.status === 'Passed').length;
  const failed = cases.filter((c) => c.status === 'Failed').length;
  const blocked = cases.filter((c) => c.status === 'Blocked').length;
  const passRate = total > 0 ? (passed / total) * 100 : 0;

  const starts = cases.map((c) => c.start).filter(Number.isFinite);
  const stops = cases.map((c) => c.stop).filter(Number.isFinite);
  const wallClockMs = starts.length && stops.length ? Math.max(...stops) - Math.min(...starts) : 0;

  return { total, passed, failed, blocked, passRate, wallClockMs };
}

function buildDonutSvg(kpis) {
  const size = 220;
  const stroke = 30;
  const r = (size - stroke) / 2;
  const cx = size / 2;
  const cy = size / 2;
  const circumference = 2 * Math.PI * r;
  const total = kpis.total || 1;
  const gapPx = 5;

  const segments = [
    { label: 'Passed', value: kpis.passed, color: STATUS_COLORS.passed },
    { label: 'Failed', value: kpis.failed, color: STATUS_COLORS.failed },
    { label: 'Blocked', value: kpis.blocked, color: STATUS_COLORS.blocked },
  ].filter((s) => s.value > 0);

  let cumulative = 0;
  const circles = segments
    .map((s) => {
      const fraction = s.value / total;
      const rawLength = fraction * circumference;
      const length = Math.max(rawLength - gapPx, 1);
      const dashoffset = -cumulative;
      cumulative += rawLength;
      const pct = (fraction * 100).toFixed(1);
      return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${s.color}" stroke-width="${stroke}" stroke-dasharray="${length} ${(circumference - length).toFixed(2)}" stroke-dashoffset="${dashoffset.toFixed(2)}" transform="rotate(-90 ${cx} ${cy})"><title>${escapeHtml(s.label)}: ${s.value} (${pct}%)</title></circle>`;
    })
    .join('\n      ');

  return `<svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" role="img" aria-label="Test status distribution: ${kpis.passed} passed, ${kpis.failed} failed, ${kpis.blocked} blocked">
      <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#e1e0d9" stroke-width="${stroke}"/>
      ${circles}
      <text x="${cx}" y="${cy - 4}" text-anchor="middle" class="donut-center-value">${kpis.passRate.toFixed(0)}%</text>
      <text x="${cx}" y="${cy + 18}" text-anchor="middle" class="donut-center-label">Pass rate</text>
    </svg>`;
}

/**
 * Renders the architectural call-flow sequence diagram as hand-generated
 * inline SVG rather than pulling in Mermaid.js from a CDN. This report is
 * explicitly a single self-contained file meant to be archived/emailed/
 * opened from disk (see the module doc comment) -- a CDN `<script>` would
 * silently fail to render the diagram the moment the viewer is offline or
 * on a network that blocks third-party scripts (both common for a
 * downloaded CI artifact), which defeats the point of "self-contained."
 * Zero new dependencies, and it inherits the same light/print-safe palette
 * as the rest of the report for free.
 */
function buildSequenceDiagramSvg() {
  const actors = [
    'TestNG Suite',
    'Test Class',
    'API Client /\nSpecFactory',
    'Azure NDC\nGateway',
    'Supplier Backend\n(e.g. Flyadeal)',
  ];

  const steps = [
    { from: 0, to: 1, label: 'invoke @Test (DataProvider row)' },
    { from: 1, to: 2, label: 'build request POJO + RequestSpecification' },
    { from: 2, to: 3, label: 'POST /api/V2/FlightSearch/Search (x-api-key, Client-Id)' },
    { from: 3, to: 4, label: 'forward normalized NDC request' },
    { from: 4, to: 3, label: 'raw supplier response', dashed: true },
    { from: 3, to: 2, label: '200 / 4xx + JSON body', dashed: true },
    { from: 2, to: 1, label: 'RestAssured Response', dashed: true },
    { from: 1, to: 0, label: 'assertion result (pass / fail)', dashed: true },
  ];

  const laneWidth = 210;
  const leftPad = 20;
  const topPad = 62;
  const rowHeight = 44;
  const actorBoxHeight = 50;
  const laneX = (i) => leftPad + 80 + i * laneWidth;
  const width = laneX(actors.length - 1) + 80 + leftPad;
  const height = topPad + steps.length * rowHeight + 30;

  const actorMarkup = actors
    .map((name, i) => {
      const x = laneX(i);
      const lines = name.split('\n');
      const labelLines = lines
        .map((line, li) => `<text x="${x}" y="${28 + li * 15}" text-anchor="middle" class="seq-actor-label">${escapeHtml(line)}</text>`)
        .join('');
      return `<rect x="${x - 78}" y="8" width="156" height="${actorBoxHeight}" rx="8" class="seq-actor-box" />
      ${labelLines}
      <line x1="${x}" y1="${8 + actorBoxHeight}" x2="${x}" y2="${height - 14}" class="seq-lifeline" />`;
    })
    .join('\n      ');

  const arrowMarkup = steps
    .map((step, i) => {
      const y = topPad + i * rowHeight;
      const x1 = laneX(step.from);
      const x2 = laneX(step.to);
      const midX = (x1 + x2) / 2;
      const cls = step.dashed ? 'seq-arrow-return' : 'seq-arrow-call';
      const marker = step.dashed ? 'seqArrowReturn' : 'seqArrowCall';
      return `<line x1="${x1}" y1="${y}" x2="${x2}" y2="${y}" class="${cls}" marker-end="url(#${marker})" />
      <text x="${midX}" y="${y - 6}" text-anchor="middle" class="seq-arrow-label">${escapeHtml(step.label)}</text>`;
    })
    .join('\n      ');

  return `<svg viewBox="0 0 ${width} ${height}" class="seq-diagram-svg" role="img" aria-label="Sequence diagram: TestNG Suite calls the Test Class, which calls the API Client and RequestSpecFactory, which calls the Azure NDC Gateway, which forwards to the Supplier Backend, with responses flowing back the same path">
      <defs>
        <marker id="seqArrowCall" markerWidth="9" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L7,3 L0,6 Z" class="seq-arrowhead-call" /></marker>
        <marker id="seqArrowReturn" markerWidth="9" markerHeight="8" refX="7" refY="3" orient="auto"><path d="M0,0 L7,3 L0,6 Z" class="seq-arrowhead-return" /></marker>
      </defs>
      ${actorMarkup}
      ${arrowMarkup}
    </svg>`;
}

// -----------------------------------------------------------------------
// HTML rendering
// -----------------------------------------------------------------------

function renderPayloadBlock(label, http) {
  if (!http) return '';
  // http.headers/http.body come from redactSecrets() on the raw captured
  // attachment text -- redaction, not HTML-escaping. Escaping happens here,
  // at render time, same as every other field in this file. (This used to
  // be skipped for headers/body specifically, which meant a captured body
  // like TC_SEC_001's "<script>...</script>" payload would land in the
  // report as a live, unescaped tag -- fixed alongside adding highlighting
  // below, since both touch the same two lines.)
  const highlightedBody = http.body ? syntaxHighlightJson(http.body) : null;
  const bodyMarkup = http.body
    ? `<div class="payload-subhead">Body</div><pre class="code-block${highlightedBody ? ' json-highlighted' : ''}">${highlightedBody || escapeHtml(http.body)}</pre>`
    : '';

  return `<div class="payload-block">
              <h5>${escapeHtml(label)}</h5>
              ${http.line ? `<div class="payload-line">${escapeHtml(http.line)}</div>` : ''}
              ${http.headers ? `<div class="payload-subhead">Headers</div><pre class="code-block">${escapeHtml(http.headers)}</pre>` : ''}
              ${bodyMarkup}
            </div>`;
}

function renderTestCase(tc, { fullDetail } = {}) {
  const statusClass = tc.status.toLowerCase();
  const scenarioLine = tc.scenario ? `<div class="tc-scenario">${escapeHtml(tc.scenario)}</div>` : '';

  const failureBlock =
    tc.status !== 'Passed'
      ? `<div class="failure-block">
              <h5>Failure Message (Expected vs. Actual)</h5>
              <pre class="code-block error-text">${tc.statusMessage ? escapeHtml(tc.statusMessage) : 'No assertion message captured.'}</pre>
              <h5>Stack Trace</h5>
              <pre class="code-block error-text">${tc.statusTrace ? escapeHtml(tc.statusTrace) : 'No stack trace captured.'}</pre>
            </div>`
      : '';

  const payloadSection =
    tc.request || tc.response
      ? `<div class="payload-snapshot">
              <h5 class="payload-title">Payload Snapshot</h5>
              ${renderPayloadBlock('Request', tc.request)}
              ${renderPayloadBlock('Response', tc.response)}
            </div>`
      : '<p class="muted">No request/response payload was captured for this test.</p>';

  return `<details class="test-case" data-status="${statusClass}" data-category="${escapeHtml(tc.category)}" data-search="${escapeHtml(
    `${tc.id} ${tc.name} ${tc.scenario || ''} ${tc.category} ${tc.description || ''} ${tc.statusMessage || ''}`.toLowerCase()
  )}">
        <summary class="tc-summary">
          <span class="status-dot status-${statusClass}" aria-hidden="true">${statusIcon(tc.status)}</span>
          <span class="tc-id">${tc.id}</span>
          <span class="tc-name">${escapeHtml(tc.name)}</span>
          <span class="badge severity-${escapeHtml(tc.severity)}">${escapeHtml(tc.severity)}</span>
          <span class="tc-duration">${formatDuration(tc.duration)}</span>
        </summary>
        <div class="tc-body">
          ${scenarioLine}
          <p class="tc-description">${escapeHtml(tc.description)}</p>
          ${failureBlock}
          ${fullDetail !== false ? payloadSection : ''}
        </div>
      </details>`;
}

function renderFailedBlockedSection(cases) {
  const issues = cases.filter((c) => c.status !== 'Passed');
  if (issues.length === 0) {
    return `<section class="issues-section no-issues">
      <h2>Failed &amp; Blocked Issues</h2>
      <p class="empty-state">✅ No failed or blocked test cases in this run.</p>
    </section>`;
  }

  return `<section class="issues-section">
      <h2>Failed &amp; Blocked Issues <span class="badge critical-badge">${issues.length}</span></h2>
      ${issues.map((tc) => renderTestCase(tc)).join('\n      ')}
    </section>`;
}

function renderCategorySections(cases) {
  const categories = [...new Set(cases.map((c) => c.category))];

  return categories
    .map((category) => {
      const tests = cases.filter((c) => c.category === category);
      const passed = tests.filter((t) => t.status === 'Passed').length;
      const failed = tests.filter((t) => t.status === 'Failed').length;
      const blocked = tests.filter((t) => t.status === 'Blocked').length;
      const icon = pickCategoryIcon(category);

      return `<details class="category-section" open data-category="${escapeHtml(category)}">
      <summary class="category-header">
        <span class="category-icon" aria-hidden="true">${icon}</span>
        <span class="category-name">${escapeHtml(category)}</span>
        <span class="category-badges">
          <span class="badge badge-passed">${passed} passed</span>
          ${failed ? `<span class="badge badge-failed">${failed} failed</span>` : ''}
          ${blocked ? `<span class="badge badge-blocked">${blocked} blocked</span>` : ''}
        </span>
        <span class="category-controls no-print">
          <button type="button" class="btn-link section-expand">Expand section</button>
          <button type="button" class="btn-link section-collapse">Collapse section</button>
        </span>
      </summary>
      <div class="category-body">
        ${tests.map((tc) => renderTestCase(tc)).join('\n        ')}
      </div>
    </details>`;
    })
    .join('\n    ');
}

function renderKpiCards(kpis) {
  const cards = [
    { label: 'Total test cases', value: kpis.total },
    { label: 'Pass rate', value: `${kpis.passRate.toFixed(1)}%` },
    { label: 'Passed', value: kpis.passed, accent: 'passed' },
    { label: 'Failed', value: kpis.failed, accent: 'failed' },
    { label: 'Blocked', value: kpis.blocked, accent: 'blocked' },
    { label: 'Execution duration', value: formatDuration(kpis.wallClockMs) },
  ];

  return cards
    .map(
      (c) => `<div class="kpi-card${c.accent ? ` kpi-${c.accent}` : ''}">
            <div class="kpi-label">${c.label}</div>
            <div class="kpi-value">${c.value}</div>
          </div>`
    )
    .join('\n          ');
}

function renderMetaChips(meta) {
  const chips = [
    { label: 'Base URL / Endpoint', value: meta.endpoint },
    { label: 'Test Framework', value: meta.framework },
    { label: 'JDK / Maven Runtime', value: meta.runtime },
    { label: 'Execution Mode', value: meta.executionMode },
    { label: 'Supplier Config', value: meta.supplierConfig },
    { label: 'Generated', value: meta.generatedAt },
    { label: 'Run Duration', value: meta.runDuration },
  ];

  return chips
    .map(
      (c) => `<div class="meta-chip">
            <div class="meta-chip-label">${escapeHtml(c.label)}</div>
            <div class="meta-chip-value"><code>${escapeHtml(c.value)}</code></div>
          </div>`
    )
    .join('\n          ');
}

function renderCategoryOptions(cases) {
  const categories = [...new Set(cases.map((c) => c.category))];
  return categories.map((c) => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('\n            ');
}

function buildHtml(cases, kpis, meta) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>NDC FlightSearch API — Execution QA Report</title>
<style>
${CSS}
</style>
</head>
<body>
<button type="button" id="printBtn" class="print-btn no-print" onclick="window.print()">\u{1F5A8}️ Print / Save as PDF</button>

<header class="report-header">
  <p class="report-eyebrow">NDC Supplier Integration &middot; Automated API Test Execution</p>
  <h1>NDC FlightSearch API — Execution QA Report</h1>
  <p class="report-subtitle">
    Run window: ${escapeHtml(formatTimestamp(meta.runStart))} &rarr; ${escapeHtml(formatTimestamp(meta.runStop))}
  </p>
  <div class="meta-grid">
    ${renderMetaChips(meta)}
  </div>
</header>

<details class="category-section" open>
  <summary class="category-header">
    <span class="category-icon" aria-hidden="true">🔀</span>
    <span class="category-name">Architecture &amp; Call Flow</span>
  </summary>
  <div class="category-body">
    <p class="muted architecture-caption">
      How one test method's HTTP call actually traverses the stack — request down, response back up.
    </p>
    <div class="seq-diagram-wrap">
      ${buildSequenceDiagramSvg()}
    </div>
  </div>
</details>

<section class="summary-grid">
  <div class="kpi-column">
    <h2>Executive Summary</h2>
    <div class="kpi-grid">
      ${renderKpiCards(kpis)}
    </div>
  </div>
  <div class="chart-column">
    <h2>Status Distribution</h2>
    <div class="donut-wrap">
      ${buildDonutSvg(kpis)}
      <ul class="legend">
        <li><span class="swatch" style="background:${STATUS_COLORS.passed}"></span>✓ Passed &mdash; ${kpis.passed} (${kpis.total ? ((kpis.passed / kpis.total) * 100).toFixed(1) : '0.0'}%)</li>
        <li><span class="swatch" style="background:${STATUS_COLORS.failed}"></span>✗ Failed &mdash; ${kpis.failed} (${kpis.total ? ((kpis.failed / kpis.total) * 100).toFixed(1) : '0.0'}%)</li>
        <li><span class="swatch" style="background:${STATUS_COLORS.blocked}"></span>⚠ Blocked &mdash; ${kpis.blocked} (${kpis.total ? ((kpis.blocked / kpis.total) * 100).toFixed(1) : '0.0'}%)</li>
      </ul>
    </div>
  </div>
</section>

<section class="toolbar no-print">
  <input type="search" id="searchInput" placeholder="Search by ID, title, category, TestCases.md TC_ID, or error text…" aria-label="Search test cases" />
  <select id="statusFilter" aria-label="Filter by status">
    <option value="all">All statuses</option>
    <option value="passed">Passed</option>
    <option value="failed">Failed</option>
    <option value="blocked">Blocked</option>
  </select>
  <select id="categoryFilter" aria-label="Filter by category">
    <option value="all">All categories</option>
    ${renderCategoryOptions(cases)}
  </select>
  <button type="button" id="expandAll" class="btn-secondary">Expand all</button>
  <button type="button" id="collapseAll" class="btn-secondary">Collapse all</button>
  <button type="button" id="resetFilters" class="btn-danger">Reset Filters</button>
</section>

<div id="noResultsState" class="no-results-state no-print" hidden>
  <p>🔍 No matching test cases found for your query.</p>
  <button type="button" id="resetFromEmptyState" class="btn-secondary">Reset filters</button>
</div>

${renderFailedBlockedSection(cases)}

<section class="categories">
  <h2>All Test Cases by Category</h2>
  ${renderCategorySections(cases)}
</section>

<footer class="report-footer">
  Generated by <code>generate_html_report.js</code> from Allure results
  (<code>target/allure-results</code>). Sensitive header values (e.g. <code>x-api-key</code>)
  are redacted from payload snapshots. The <code>RUN-*</code> ID on each card is a
  run-sequence label, not a TestCases.md ID -- the real TC_ID(s) for a test are in
  its description text (and are searchable above).
</footer>

<script>
${JS}
</script>
</body>
</html>
`;
}

// -----------------------------------------------------------------------
// CSS
// -----------------------------------------------------------------------

const CSS = `
  :root {
    color-scheme: light;
    --surface-1: #ffffff;
    --page: #f6f8fa;
    --border: #d0d7de;
    --text-primary: #0b0b0b;
    --text-secondary: #52514e;
    --text-muted: #898781;
    --status-good: ${STATUS_COLORS.passed};
    --status-critical: ${STATUS_COLORS.failed};
    --status-warning: ${STATUS_COLORS.blocked};
  }

  * { box-sizing: border-box; }

  body {
    margin: 0;
    padding: 24px 20px 60px;
    background: var(--page);
    color: var(--text-primary);
    font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
    max-width: 1200px;
    margin-inline: auto;
  }

  h1 { font-size: 1.6rem; margin: 0 0 4px; }
  h2 { font-size: 1.15rem; margin: 0 0 12px; }
  h5 { font-size: 0.85rem; margin: 12px 0 4px; color: var(--text-secondary); }
  code { background: #eef1f4; border-radius: 4px; padding: 1px 5px; font-size: 0.85em; }
  .muted { color: var(--text-muted); font-style: italic; }

  .print-btn {
    position: fixed;
    top: 16px;
    right: 20px;
    z-index: 100;
    background: var(--text-primary);
    color: #fff;
    border: none;
    border-radius: 6px;
    padding: 10px 16px;
    font-size: 0.9rem;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0,0,0,0.2);
  }
  .print-btn:hover { opacity: 0.85; }

  .report-header {
    margin-bottom: 24px;
    padding: 20px 200px 20px 24px;
    background: var(--text-primary);
    color: #fff;
    border-radius: 10px;
  }
  .report-eyebrow {
    margin: 0 0 6px;
    font-size: 0.75rem;
    font-weight: 600;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #b9b8b3;
  }
  .report-header h1 { color: #fff; }
  .report-subtitle { margin: 0 0 16px; color: #d7d6d1; font-size: 0.88rem; }

  .meta-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 12px;
  }
  .meta-chip {
    background: rgba(255,255,255,0.08);
    border: 1px solid rgba(255,255,255,0.18);
    border-radius: 8px;
    padding: 8px 12px;
    page-break-inside: avoid;
  }
  .meta-chip-label {
    font-size: 0.7rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: #b9b8b3;
    margin-bottom: 3px;
  }
  .meta-chip-value { font-size: 0.85rem; font-weight: 600; word-break: break-word; }
  .meta-chip-value code { background: rgba(255,255,255,0.12); color: #fff; }

  .summary-grid {
    display: grid;
    grid-template-columns: 1.3fr 1fr;
    align-items: center;
    gap: 20px;
    background: var(--surface-1);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 20px;
    margin-bottom: 20px;
  }
  @media (max-width: 800px) {
    .summary-grid { grid-template-columns: 1fr; }
  }

  .kpi-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  @media (max-width: 480px) {
    .kpi-grid { grid-template-columns: 1fr; }
  }

  .kpi-card {
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px 14px;
    background: #fcfcfb;
    page-break-inside: avoid;
  }
  .kpi-label { font-size: 0.78rem; color: var(--text-secondary); }
  .kpi-value { font-size: 1.5rem; font-weight: 600; margin-top: 2px; }
  .kpi-passed .kpi-value { color: var(--status-good); }
  .kpi-failed .kpi-value { color: var(--status-critical); }
  .kpi-blocked .kpi-value { color: #9a6b00; }

  .chart-column {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    padding-left: 20px;
    border-left: 1px solid var(--border);
  }
  @media (max-width: 800px) {
    .chart-column { border-left: none; padding-left: 0; border-top: 1px solid var(--border); padding-top: 16px; }
  }
  .donut-wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; }
  .donut-center-value { font-size: 28px; font-weight: 600; fill: var(--text-primary); font-family: system-ui, sans-serif; }
  .donut-center-label { font-size: 12px; fill: var(--text-secondary); font-family: system-ui, sans-serif; }

  .legend { list-style: none; margin: 0; padding: 0; font-size: 0.85rem; }
  .legend li { display: flex; align-items: center; gap: 8px; padding: 3px 0; color: var(--text-primary); }
  .swatch { width: 12px; height: 12px; border-radius: 3px; display: inline-block; flex-shrink: 0; }

  .toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    background: var(--surface-1);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px;
    margin-bottom: 20px;
    position: sticky;
    top: 0;
    z-index: 10;
  }
  .toolbar input[type="search"] { flex: 1 1 260px; }
  .toolbar input, .toolbar select {
    padding: 8px 10px;
    border: 1px solid var(--border);
    border-radius: 6px;
    font-size: 0.9rem;
  }
  .btn-secondary {
    background: #fff;
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 8px 12px;
    cursor: pointer;
    font-size: 0.85rem;
  }
  .btn-secondary:hover { background: #eef1f4; }

  .btn-danger {
    background: var(--status-critical);
    border: 1px solid var(--status-critical);
    color: #fff;
    border-radius: 6px;
    padding: 8px 14px;
    cursor: pointer;
    font-size: 0.85rem;
    font-weight: 600;
  }
  .btn-danger:hover { background: #a52d2d; border-color: #a52d2d; }

  .no-results-state {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    background: #fff8ec;
    border: 1px dashed #d9a441;
    border-radius: 10px;
    padding: 16px 20px;
    margin-bottom: 20px;
    font-weight: 600;
    color: #7a5300;
  }
  .no-results-state p { margin: 0; }

  .issues-section, .categories {
    background: var(--surface-1);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 20px;
    margin-bottom: 20px;
  }
  .issues-section:not(.no-issues) { border-color: var(--status-critical); border-width: 2px; }
  .empty-state { color: var(--status-good); font-weight: 600; }

  .badge {
    display: inline-block;
    border-radius: 999px;
    padding: 2px 10px;
    font-size: 0.75rem;
    font-weight: 600;
    background: #eef1f4;
    color: var(--text-secondary);
  }
  .badge-passed { background: rgba(12,163,12,0.12); color: #0a7a0a; }
  .badge-failed { background: rgba(208,59,59,0.12); color: #a52d2d; }
  .badge-blocked { background: rgba(250,178,25,0.18); color: #8a5c00; }
  .critical-badge { background: var(--status-critical); color: #fff; }
  .severity-blocker, .severity-critical { background: rgba(208,59,59,0.12); color: #a52d2d; }
  .severity-normal { background: #eef1f4; color: var(--text-secondary); }

  details.category-section { margin-bottom: 14px; page-break-inside: auto; }
  summary.category-header {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
    padding: 10px 12px;
    background: #fcfcfb;
    border: 1px solid var(--border);
    border-radius: 8px;
    list-style: none;
    page-break-inside: avoid;
  }
  summary.category-header::-webkit-details-marker { display: none; }
  .category-name { font-weight: 600; flex: 1 1 auto; }
  .category-badges { display: flex; gap: 6px; }
  .category-controls { display: flex; gap: 6px; }
  .btn-link {
    background: none;
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 3px 8px;
    font-size: 0.75rem;
    cursor: pointer;
    color: var(--text-secondary);
  }
  .btn-link:hover { background: #eef1f4; }
  .category-body { padding: 10px 6px 2px 12px; }

  .architecture-caption { margin: 2px 0 12px; }
  .seq-diagram-wrap {
    overflow-x: auto;
    background: #fcfcfb;
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px 8px;
  }
  .seq-diagram-svg { width: 100%; height: auto; display: block; min-width: 640px; }
  .seq-actor-box { fill: #eef1f4; stroke: var(--border); stroke-width: 1; }
  .seq-actor-label { font-size: 11px; font-family: system-ui, sans-serif; fill: var(--text-primary); font-weight: 600; }
  .seq-lifeline { stroke: var(--border); stroke-width: 1; stroke-dasharray: 4 3; }
  .seq-arrow-call { stroke: #2b6cb0; stroke-width: 1.6; }
  .seq-arrow-return { stroke: var(--text-muted); stroke-width: 1.4; stroke-dasharray: 5 3; }
  .seq-arrow-label { font-size: 9.5px; font-family: system-ui, sans-serif; fill: var(--text-secondary); }
  .seq-arrowhead-call { fill: #2b6cb0; }
  .seq-arrowhead-return { fill: var(--text-muted); }

  details.test-case {
    border: 1px solid var(--border);
    border-radius: 8px;
    margin: 8px 0;
    background: #fff;
    page-break-inside: avoid;
  }
  summary.tc-summary {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 12px;
    cursor: pointer;
    list-style: none;
  }
  summary.tc-summary::-webkit-details-marker { display: none; }
  .status-dot {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 0.7rem;
    color: #fff;
    flex-shrink: 0;
  }
  .status-passed { background: var(--status-good); }
  .status-failed { background: var(--status-critical); }
  .status-blocked { background: var(--status-warning); color: #4a3300; }
  .tc-id { font-family: monospace; font-size: 0.8rem; color: var(--text-secondary); flex-shrink: 0; }
  .tc-name { flex: 1 1 auto; }
  .tc-duration { font-size: 0.8rem; color: var(--text-muted); flex-shrink: 0; }
  .tc-body { padding: 4px 16px 14px 44px; }
  .tc-scenario { font-size: 0.85rem; color: var(--text-secondary); font-style: italic; margin-bottom: 6px; }
  .tc-description { margin: 4px 0 8px; }

  .failure-block, .payload-snapshot { margin-top: 8px; }
  .payload-title { margin-top: 14px; }
  .payload-block { margin-bottom: 10px; }
  .payload-line { font-family: monospace; font-size: 0.82rem; color: var(--text-secondary); }
  .payload-subhead { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--text-muted); margin-top: 6px; }

  .code-block {
    background: #0d1117;
    color: #d7dde3;
    border-radius: 6px;
    padding: 10px 12px;
    font-size: 0.78rem;
    line-height: 1.45;
    overflow: auto;
    max-height: 340px;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .error-text { background: #2b0f0f; color: #ffd8d8; }

  .json-highlighted .json-key { color: #79c0ff; }
  .json-highlighted .json-string { color: #a5d6a7; }
  .json-highlighted .json-number { color: #d2a8ff; }
  .json-highlighted .json-boolean { color: #ffab70; }
  .json-highlighted .json-null { color: #ff9e9e; }

  .report-footer {
    text-align: center;
    color: var(--text-muted);
    font-size: 0.8rem;
    margin-top: 30px;
  }

  @media print {
    body { background: #fff; padding: 0 12px; max-width: none; }
    .no-print { display: none !important; }
    .toolbar { position: static; }
    .summary-grid { grid-template-columns: 1.3fr 1fr; box-shadow: none; }
    .report-header {
      background: #fff;
      color: #000;
      border: 1px solid #ccc;
      padding: 16px;
    }
    .report-header h1, .report-eyebrow, .report-subtitle { color: #000; }
    .meta-chip { background: #f4f4f4; border-color: #ccc; }
    .meta-chip-label, .report-eyebrow, .report-subtitle { color: #444; }
    .meta-chip-value, .meta-chip-value code { color: #000; background: #eaeaea; }
    .chart-column { border-left-color: #ccc; }
    .code-block { max-height: none; overflow: visible; background: #f4f4f4; color: #111; border: 1px solid #ccc; }
    .error-text { background: #fbeaea; color: #7a1f1f; }
    .json-highlighted .json-key { color: #0550ae; }
    .json-highlighted .json-string { color: #1a7f37; }
    .json-highlighted .json-number { color: #6639ba; }
    .json-highlighted .json-boolean { color: #b35900; }
    .json-highlighted .json-null { color: #b31d1d; }
    details.test-case, summary.category-header, .kpi-card, .meta-chip, .seq-diagram-wrap { page-break-inside: avoid; }
    details.category-section { page-break-inside: auto; }
    a { color: inherit; text-decoration: none; }
  }
`;

// -----------------------------------------------------------------------
// Client-side JS (search/filter, expand-collapse, print hooks)
// -----------------------------------------------------------------------

const JS = `
  (function () {
    var searchInput = document.getElementById('searchInput');
    var statusFilter = document.getElementById('statusFilter');
    var categoryFilter = document.getElementById('categoryFilter');
    var noResultsState = document.getElementById('noResultsState');

    function applyFilters() {
      var term = (searchInput.value || '').toLowerCase().trim();
      var status = statusFilter.value;
      var category = categoryFilter.value;
      var totalVisible = 0;

      document.querySelectorAll('.test-case').forEach(function (el) {
        var matchesTerm = !term || el.getAttribute('data-search').indexOf(term) !== -1;
        var matchesStatus = status === 'all' || el.getAttribute('data-status') === status;
        var matchesCategory = category === 'all' || el.getAttribute('data-category') === category;
        var visible = matchesTerm && matchesStatus && matchesCategory;
        el.style.display = visible ? '' : 'none';
        if (visible && term) el.open = true;
        if (visible) totalVisible++;
      });

      document.querySelectorAll('.category-section').forEach(function (section) {
        var anyVisible = Array.prototype.some.call(
          section.querySelectorAll('.test-case'),
          function (el) { return el.style.display !== 'none'; }
        );
        section.style.display = anyVisible ? '' : 'none';
      });

      if (noResultsState) noResultsState.hidden = totalVisible > 0;
    }

    function resetFilters() {
      searchInput.value = '';
      statusFilter.value = 'all';
      categoryFilter.value = 'all';
      applyFilters();
    }

    searchInput.addEventListener('input', applyFilters);
    statusFilter.addEventListener('change', applyFilters);
    categoryFilter.addEventListener('change', applyFilters);

    var resetFiltersBtn = document.getElementById('resetFilters');
    if (resetFiltersBtn) resetFiltersBtn.addEventListener('click', resetFilters);
    var resetFromEmptyStateBtn = document.getElementById('resetFromEmptyState');
    if (resetFromEmptyStateBtn) resetFromEmptyStateBtn.addEventListener('click', resetFilters);

    document.getElementById('expandAll').addEventListener('click', function () {
      document.querySelectorAll('details').forEach(function (d) { d.open = true; });
    });
    document.getElementById('collapseAll').addEventListener('click', function () {
      document.querySelectorAll('details.test-case').forEach(function (d) { d.open = false; });
    });

    document.querySelectorAll('.category-section').forEach(function (section) {
      var expandBtn = section.querySelector('.section-expand');
      var collapseBtn = section.querySelector('.section-collapse');
      if (expandBtn) {
        expandBtn.addEventListener('click', function (evt) {
          evt.preventDefault();
          evt.stopPropagation();
          section.open = true;
          section.querySelectorAll('.test-case').forEach(function (d) { d.open = true; });
        });
      }
      if (collapseBtn) {
        collapseBtn.addEventListener('click', function (evt) {
          evt.preventDefault();
          evt.stopPropagation();
          section.querySelectorAll('.test-case').forEach(function (d) { d.open = false; });
        });
      }
    });

    var previousOpenState = null;
    window.addEventListener('beforeprint', function () {
      previousOpenState = Array.prototype.map.call(document.querySelectorAll('details'), function (d) { return d.open; });
      document.querySelectorAll('details').forEach(function (d) { d.open = true; });
    });
    window.addEventListener('afterprint', function () {
      if (!previousOpenState) return;
      var details = document.querySelectorAll('details');
      details.forEach(function (d, i) { d.open = previousOpenState[i]; });
      previousOpenState = null;
    });
  })();
`;

// -----------------------------------------------------------------------
// Main
// -----------------------------------------------------------------------

function main() {
  console.log(`Reading Allure results from ${RESULTS_DIR} ...`);
  const cases = loadTestCases();
  const kpis = computeKpis(cases);

  const firstWithRequest = cases.find((c) => c.request && c.request.line);
  const endpointMatch = firstWithRequest ? firstWithRequest.request.line.match(/to (\S+)/) : null;

  const meta = {
    endpoint: endpointMatch ? endpointMatch[1] : 'N/A',
    generatedAt: formatTimestamp(Date.now()),
    runStart: Math.min(...cases.map((c) => c.start).filter(Number.isFinite)),
    runStop: Math.max(...cases.map((c) => c.stop).filter(Number.isFinite)),
    framework: detectTestFramework(),
    runtime: detectRuntimeVersions(),
    executionMode: detectExecutionMode(),
    supplierConfig: detectSupplierConfig(cases),
    runDuration: formatDuration(kpis.wallClockMs),
  };

  const html = buildHtml(cases, kpis, meta);
  fs.writeFileSync(OUTPUT_FILE, html, 'utf8');

  console.log(`✓ ${cases.length} test cases processed (${kpis.passed} passed, ${kpis.failed} failed, ${kpis.blocked} blocked)`);
  console.log(`✓ Report written to ${OUTPUT_FILE}`);
}

main();
