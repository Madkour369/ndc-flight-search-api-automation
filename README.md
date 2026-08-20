# NDC FlightSearch API Automation

API test automation suite for the NDC supplier integration endpoint
`POST /api/V2/FlightSearch/Search`, built with RestAssured and TestNG.

## 1. Project overview

This suite exercises the FlightSearch endpoint across one-way, round-trip, and
varied passenger-mix (ADT/CHD/INF) itineraries, and validates the API's error
contract for authentication failures, missing required fields, malformed
dates, and unsupported (multi-city) itineraries.

The response POJOs and negative-path assertions were built against the
**live** endpoint rather than assumed shapes — including the two different
error-body casings the API returns (PascalCase from the gateway/auth layer on
401s, camelCase from the application layer on 400/409s), and the business
rule that this supplier (flyadeal) only supports one-way and round-trip
itineraries, rejecting true multi-city with a 409.

### Tech stack

| Concern | Library |
|---|---|
| Language / build | Java 17, Maven |
| HTTP client / assertions | RestAssured 5.5 |
| Test runner | TestNG 7.10 |
| POJO (de)serialization | Jackson (`jackson-databind`, `jackson-datatype-jsr310`) |
| Boilerplate reduction | Lombok (`@Value` / `@Builder` / `@Jacksonized` for response models, `@Data` / `@Builder` for request models) |
| Assertions | AssertJ |
| Reporting | Allure (`allure-testng`, `allure-rest-assured`, `allure-maven`) |
| CI | GitHub Actions |

## 2. Project structure

All framework and test code lives under `src/test/java` — this is a pure
test-automation artifact with no shippable main code.

```
Task3_Automation_Project/
├── .github/
│   └── workflows/
│       └── test-automation.yml        CI pipeline: build, test, Allure report, artifact upload
├── src/
│   └── test/
│       ├── java/com/tildetech/ndc/automation/
│       │   ├── config/
│       │   │   └── ConfigManager.java         Loads config.properties, overridable via -D system properties
│       │   ├── models/
│       │   │   ├── request/
│       │   │   │   ├── FlightSearchRequest.java
│       │   │   │   ├── SearchCriteria.java
│       │   │   │   └── PassengerRequest.java
│       │   │   └── response/
│       │   │       ├── FlightSearchResponse.java
│       │   │       ├── Journey.java
│       │   │       ├── FlightSegment.java
│       │   │       ├── PriceClass.java
│       │   │       ├── BaggageDetail.java
│       │   │       ├── Offer.java
│       │   │       ├── PassengerFareBreakdown.java
│       │   │       ├── SegmentDetail.java
│       │   │       ├── PriceDetails.java
│       │   │       ├── TaxFee.java
│       │   │       ├── Money.java
│       │   │       ├── ErrorResponse.java
│       │   │       └── ErrorContext.java
│       │   ├── specs/
│       │   │   └── RequestSpecFactory.java    Base URI, headers, shared ObjectMapper, logging + Allure filters
│       │   ├── providers/
│       │   │   └── FlightSearchDataProvider.java   TestNG @DataProvider payload catalog
│       │   ├── base/
│       │   │   └── BaseTest.java              Wires up the shared RequestSpecification
│       │   └── tests/
│       │       ├── FlightSearchPositiveTests.java
│       │       └── FlightSearchNegativeTests.java
│       └── resources/
│           └── config.properties              Base URI, path, Client-Id, supplier defaults (no secrets)
├── testng.xml                                  Suite definition (parallel positive/negative test classes)
├── pom.xml
└── README.md
```

## 3. Prerequisites & environment setup

- **JDK 17** (Temurin, Oracle, or any 17 LTS distribution)
- **Maven 3.9+**
- Network access to `ndc-supplier-integration.azurewebsites.net` (this suite
  calls a live API — there is no mock/stub server)

Verify your environment:

```bash
java -version
mvn -v
```

Clone/open the project, then resolve dependencies with:

```bash
mvn -B dependency:resolve
```

### Configuration

Non-sensitive runtime values live in `src/test/resources/config.properties`
(base URI, API path, `Client-Id`, default supplier/credentials selector). Any
key in that file can be overridden per run without touching it:

```bash
mvn test -Dapi.baseUri=https://staging.example.com
```

### Secrets — the `x-api-key`

The real `x-api-key` is **not** committed to the repository. `ConfigManager`
reads it from, in order of precedence: the `-Dapi.key` system property, then
the `NDC_API_KEY` environment variable. If neither is set, tests fail fast
with a clear error instead of silently running unauthenticated.

Locally:

```bash
# bash
export NDC_API_KEY="<your-key>"
mvn test

# PowerShell
$env:NDC_API_KEY = "<your-key>"
mvn test

# or, one-off, without an env var
mvn test -Dapi.key=<your-key>
```

In GitHub Actions: add a repository secret named `NDC_API_KEY`
(**Settings → Secrets and variables → Actions → New repository secret**).
The workflow already passes it through to the test run — see
[CI/CD](#6-cicd) below.

## 4. Running the suite

```bash
mvn test
```

This compiles the project and runs `testng.xml`, which executes
`FlightSearchPositiveTests` and `FlightSearchNegativeTests` in parallel.
Results land in `target/surefire-reports` and `target/allure-results`.

### Generating and viewing the Allure report locally

```bash
mvn allure:serve      # builds the report and opens it in your default browser
```

Or, for a static copy you can archive or host elsewhere:

```bash
mvn allure:report     # writes to target/site/allure-maven-plugin/index.html
```

Every test's request/response pair is attached automatically via
`allure-rest-assured`, and tests are grouped by `@Epic` / `@Feature` /
`@Story` / `@Severity` for easy drill-down in the report UI.

## 5. Test coverage

### Positive — `FlightSearchPositiveTests`

One data-driven test method (`searchReturnsValidOffers`) covering 5
itinerary/passenger-mix scenarios via `positiveSearchScenarios`. Each
scenario asserts: HTTP 200, a non-blank `responseId`, a `supplier` value
matching the request, a non-empty `offers` array, and structural integrity
of the first offer (non-blank `offerId`, populated fare breakdown, positive
total price).

| # | Scenario | Purpose |
|---|---|---|
| 1 | One-way \| ADT+CHD+INF mix | The full sample payload — all three passenger types on one leg |
| 2 | One-way \| Single adult | Simplest itinerary, one passenger |
| 3 | One-way \| Adults with children, no infants | ADT+CHD combination on a different route (CAI–JED) |
| 4 | Round-trip \| CAI-RUH outbound + RUH-CAI return | Two-leg round trip via two `searchCriteria` entries |
| 5 | One-way \| Adult with infant, alternate route | ADT+INF combination on a third route |

### Negative — `FlightSearchNegativeTests`

Four test methods, 8 executions total (3 are data-driven).

| Test method | Scenario(s) | Expected result |
|---|---|---|
| `invalidApiKeyReturns401` | Well-formed payload, bogus `x-api-key` | 401, `ErrorCode: AuthenticationError` |
| `missingRequiredFieldReturns400` | Missing `searchCriteria` / missing `passengers` / missing `supplier` (3 cases) | 400, error context names the missing field |
| `multiCityItineraryReturns409` | Genuine 3-city itinerary (CAI→RUH→JED) | 409 — flyadeal's real business rule: "only oneway or round trips allowed" |
| `invalidDateFormatReturns400` | Date as `DD-MM-YYYY` / free text / `YYYY/MM/DD` (3 cases) | 400, non-empty validation context |

## 6. CI/CD

[`​.github/workflows/test-automation.yml`](.github/workflows/test-automation.yml)
runs on every push and pull request targeting `main`/`master` (and supports
manual triggering via `workflow_dispatch`):

1. Checks out the repository.
2. Sets up JDK 17 (Temurin) via `actions/setup-java`, with its built-in Maven
   dependency cache enabled — subsequent runs restore `~/.m2/repository`
   instead of re-downloading every dependency.
3. Runs `mvn -B test` — the full positive + negative TestNG suite against the
   live API — with `NDC_API_KEY` injected from the `NDC_API_KEY` repository
   secret (see [Secrets](#secrets--the-x-api-key) above; the workflow fails
   fast with a clear error if the secret isn't configured).
4. Generates the Allure HTML report with `mvn -B allure:report` and uploads
   it as a build artifact (`allure-report`), alongside the raw
   `allure-results` and `surefire-reports` — all three steps run with
   `if: always()`, so the report and logs are still available for a failed
   run, not just a passing one.

To review a run: open the workflow run in the **Actions** tab on GitHub,
download the `allure-report` artifact, unzip it, and open `index.html` in a
browser (Allure's static report needs to be served rather than opened via
`file://`, e.g. `npx http-server target/site/allure-maven-plugin` or Python's
`python -m http.server` from inside the extracted folder).

## 7. Extending the suite

- New itineraries/passenger mixes: add a case to `FlightSearchDataProvider`.
- New negative scenarios: add a data provider entry, or a dedicated `@Test`
  method in `FlightSearchNegativeTests` for one-off cases like the API-key
  test.
- New response fields: extend the relevant response POJO — all response
  models ignore unknown JSON properties, so partial modeling never breaks
  deserialization.
