# Test Cases — NDC FlightSearch API

Endpoint under test: `POST /api/V2/FlightSearch/Search`
Suite: `FlightSearchPositiveTests` (5 scenarios) + `FlightSearchNegativeTests` (8 scenarios) — 13 automated test executions total.

All preconditions below assume, unless stated otherwise: valid `Client-Id: NDC-Core` header, valid `x-api-key`,
`Content-Type: application/json`, `supplier: flyadeal`, `credentialsSelector: EGY`, `isdebug: true`.

## Positive test cases

Implemented as one data-driven method — `FlightSearchPositiveTests.searchReturnsValidOffers` — parametrized via
`FlightSearchDataProvider.positiveSearchScenarios`.

| Test Case ID | Feature / Scenario Name | Test Type | Input Parameters & Pre-conditions | Expected Status Code & Assertions | Severity |
|---|---|---|---|---|---|
| TC_POS_001 | One-way search — ADT+CHD+INF mix | Positive / Functional | `searchCriteria`: CAI→RUH, 2026-09-08 (one leg). `passengers`: ADT=3, CHD=2, INF=3. All other preconditions valid. | **200 OK**. `responseId` non-blank. `supplier` in response equals `flyadeal` (case-insensitive). `offers` array non-empty. First offer has non-blank `offerId`, non-empty `passengerFareBreakdown`, and `priceDetails.totalAmount.amount` > 0. | BLOCKER |
| TC_POS_002 | One-way search — single adult | Positive / Functional | `searchCriteria`: CAI→RUH, 2026-09-15 (one leg). `passengers`: ADT=1. | Same assertion set as TC_POS_001. | BLOCKER |
| TC_POS_003 | One-way search — adults with children, no infants | Positive / Functional | `searchCriteria`: CAI→JED, 2026-09-20 (one leg). `passengers`: ADT=2, CHD=1. | Same assertion set as TC_POS_001. | BLOCKER |
| TC_POS_004 | Round-trip search — outbound + return | Positive / Functional | `searchCriteria`: [CAI→RUH, 2026-09-08], [RUH→CAI, 2026-09-15] (two legs, same city pair reversed). `passengers`: ADT=2. | Same assertion set as TC_POS_001, evaluated against the combined round-trip response. | BLOCKER |
| TC_POS_005 | One-way search — adult with infant, alternate route | Positive / Functional | `searchCriteria`: CAI→JED, 2026-09-25 (one leg). `passengers`: ADT=1, INF=1. | Same assertion set as TC_POS_001. | BLOCKER |

## Negative test cases

Implemented across four methods in `FlightSearchNegativeTests`; TC_NEG_002–004 and TC_NEG_006–008 are data-driven via
`FlightSearchDataProvider`.

| Test Case ID | Feature / Scenario Name | Test Type | Input Parameters & Pre-conditions | Expected Status Code & Assertions | Severity |
|---|---|---|---|---|---|
| TC_NEG_001 | Authentication failure — invalid API key | Negative / Security | Well-formed one-way payload (CAI→RUH, ADT=1). `x-api-key` replaced with a bogus, non-registered value. `Client-Id` still valid. | **401 Unauthorized**. `status` = 401. `errorCode` equals `AuthenticationError` (case-insensitive). `errorMessage` contains "credentials". | CRITICAL |
| TC_NEG_002 | Missing required field — `searchCriteria` | Negative / Validation | Payload omits `searchCriteria` entirely. `supplier`, `credentialsSelector`, `passengers` (ADT=1) present and valid. | **400 Bad Request**. `status` = 400. `context` array non-empty and contains an entry referencing "SearchCriteria". | CRITICAL |
| TC_NEG_003 | Missing required field — `passengers` | Negative / Validation | Payload omits `passengers` entirely. `searchCriteria` (CAI→RUH, 2026-09-08) present and valid. | **400 Bad Request**. `status` = 400. `context` non-empty and contains an entry referencing "Passengers". | CRITICAL |
| TC_NEG_004 | Missing required field — `supplier` | Negative / Validation | Payload omits `supplier` entirely. `searchCriteria` and `passengers` (ADT=1) present and valid. | **400 Bad Request**. `status` = 400. `context` non-empty and contains an entry referencing "Supplier". | CRITICAL |
| TC_NEG_005 | Unsupported itinerary — true multi-city | Negative / Business rule | `searchCriteria`: [CAI→RUH, 2026-09-08], [RUH→JED, 2026-09-12] — three distinct city codes (not a simple round trip). `passengers`: ADT=1, CHD=1. | **409 Conflict**. `status` = 409. `errorCode` equals `ValidationError` (case-insensitive). `context` contains an entry referencing "multicity" (flyadeal supports one-way/round-trip only). | NORMAL |
| TC_NEG_006 | Invalid date format — `DD-MM-YYYY` | Negative / Validation | `searchCriteria.date` = `"08-09-2026"` (day-month-year instead of ISO). `passengers`: ADT=1. | **400 Bad Request**. `status` = 400. `context` array non-empty. | NORMAL |
| TC_NEG_007 | Invalid date format — free text | Negative / Validation | `searchCriteria.date` = `"next-tuesday"`. `passengers`: ADT=1. | **400 Bad Request**. `status` = 400. `context` array non-empty. | NORMAL |
| TC_NEG_008 | Invalid date format — slash-delimited | Negative / Validation | `searchCriteria.date` = `"2026/09/08"` (slashes instead of hyphens). `passengers`: ADT=1. | **400 Bad Request**. `status` = 400. `context` array non-empty. | NORMAL |

## Traceability

| Test Case ID | Java method | Data provider |
|---|---|---|
| TC_POS_001–005 | `FlightSearchPositiveTests.searchReturnsValidOffers` | `FlightSearchDataProvider.positiveSearchScenarios` (indices 1–5) |
| TC_NEG_001 | `FlightSearchNegativeTests.invalidApiKeyReturns401` | — (single case, uses `FlightSearchDataProvider.validOneWayRequest()`) |
| TC_NEG_002–004 | `FlightSearchNegativeTests.missingRequiredFieldReturns400` | `FlightSearchDataProvider.missingRequiredFieldScenarios` (indices 1–3) |
| TC_NEG_005 | `FlightSearchNegativeTests.multiCityItineraryReturns409` | — (single case, uses `FlightSearchDataProvider.multiCityRequest()`) |
| TC_NEG_006–008 | `FlightSearchNegativeTests.invalidDateFormatReturns400` | `FlightSearchDataProvider.invalidDateFormatScenarios` (indices 1–3) |

## Notes

- Every status code and error-body shape listed above was verified against the live endpoint while building this
  suite — none are assumed.
- Error response casing differs by layer: 401 (`TC_NEG_001`) and 409 (`TC_NEG_005`) come back PascalCase
  (`Status`, `ErrorCode`, ...) from the gateway/auth layer, while 400s (`TC_NEG_002`–`004`, `TC_NEG_006`–`008`) come
  back camelCase from the application layer. Both are deserialized into the same `ErrorResponse` POJO via a
  case-insensitive Jackson `ObjectMapper` — see `RequestSpecFactory`.
- See [README.md](README.md) for how to run the suite and generate the Allure report, which renders these same
  scenarios grouped by `@Story` and `@Severity`.
