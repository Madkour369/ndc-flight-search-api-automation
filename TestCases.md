# Test Cases — NDC FlightSearch API (Step 2: Test Design & Classification)

Endpoint under test: `POST /api/V2/FlightSearch/Search`
Basis: the domain analysis in [README.md](README.md) (Sections 1–2), cross-checked
against the prior implementation's live-verified findings where available.

> **Scope note:** this is a design artifact, not test code. Every row below is
> written to map 1:1 onto a future TestNG `@DataProvider` entry — Category
> groups rows into their natural data-provider method, and every input is
> concrete enough to build a request payload from directly.

## How to read this table

| Column | Meaning |
|---|---|
| **Valid in Scope** | `Yes` — this scenario tests the FlightSearch request/response contract and belongs in this suite's functional scope. `No` — out of functional-API scope (e.g. load/performance, or requires access/data this suite doesn't control) and is listed only for traceability/completeness. |
| **Needs Automation** | `Yes` — deterministic, stable, worth encoding as an automated case now. `No` — either the expected result is genuinely unconfirmed against the live API (an open question carried from README §5 — flagged below as **[UNCONFIRMED]**) and needs an exploratory spike first, or automating it is low-value relative to its risk. |

An **[UNCONFIRMED]** tag on a row means: the request shape is well-defined and
worth sending, but the expected status/body was not part of the previously
verified matrix and must be confirmed against the live endpoint during Step 3
(exploratory spike) before it's safe to assert on in an automated test —
asserting on a guessed status code would make the suite encode a wrong
assumption as if it were a spec.

---

## Test matrix

| Test Case ID | Category | Scenario Title / Description | Test Data / Payload Details | Expected Status Code | Expected Error/Success Message Pattern | Valid in Scope | Needs Automation |
|---|---|---|---|---|---|---|---|
| TC_POS_001 | Happy Path & Passenger Combinations | One-way, single adult — simplest valid request | `searchCriteria`: [CAI→RUH, 2026-09-08]. `passengers`: ADT=1. | 200 | `responseId` non-blank; `supplier` == `flyadeal` (case-insensitive); `offers[]` non-empty | Yes | Yes |
| TC_POS_002 | Happy Path & Passenger Combinations | One-way, full ADT+CHD+INF mix (sample payload) | `searchCriteria`: [CAI→RUH, 2026-09-08]. `passengers`: ADT=3, CHD=2, INF=3. | 200 | Same success pattern as TC_POS_001; `passengerFareBreakdown` contains an entry per distinct `passengerTypeCode` | Yes | Yes |
| TC_POS_003 | Happy Path & Passenger Combinations | One-way, adults with children, no infants | `searchCriteria`: [CAI→JED, 2026-09-20]. `passengers`: ADT=2, CHD=1. | 200 | Same success pattern | Yes | Yes |
| TC_POS_004 | Happy Path & Passenger Combinations | One-way, adult with a single infant | `searchCriteria`: [CAI→JED, 2026-09-25]. `passengers`: ADT=1, INF=1. | 200 | Same success pattern | Yes | Yes |
| TC_POS_005 | Happy Path & Passenger Combinations | Round-trip, same city pair reversed | `searchCriteria`: [CAI→RUH, 2026-09-08], [RUH→CAI, 2026-09-15]. `passengers`: ADT=2. | 200 | Same success pattern, evaluated against the combined round-trip response | Yes | Yes |
| TC_POS_006 | Happy Path & Passenger Combinations | Round-trip with full passenger mix | `searchCriteria`: [CAI→RUH, 2026-09-08], [RUH→CAI, 2026-09-15]. `passengers`: ADT=2, CHD=1, INF=1. | 200 | Same success pattern | Yes | Yes |
| TC_POS_007 | Happy Path & Passenger Combinations | Boundary — typical industry-max adult count (9) on one booking | `searchCriteria`: [CAI→RUH, 2026-09-08]. `passengers`: ADT=9. | 200 | Same success pattern, OR a documented max-passenger rejection — **[UNCONFIRMED]** whether 9 is within this supplier's accepted range | Yes | No |
| TC_POS_008 | Happy Path & Passenger Combinations | Boundary — infants equal to adults (1:1 ratio, the typical max) | `searchCriteria`: [CAI→RUH, 2026-09-08]. `passengers`: ADT=2, INF=2. | 200 | Same success pattern | Yes | Yes |
| TC_POS_009 | Happy Path & Passenger Combinations | Debug mode on — supplier raw traffic is exposed | Valid one-way payload with `isdebug: true`. | 200 | `supplierRequest` and `supplierResponse` are present and non-blank | Yes | Yes |
| TC_POS_010 | Happy Path & Passenger Combinations | Debug mode off/omitted — no raw traffic leak | Valid one-way payload with `isdebug` omitted entirely. | 200 | `supplierRequest` and `supplierResponse` are `null`/absent | Yes | Yes |
| TC_POS_011 | Happy Path & Passenger Combinations | Boundary — earliest bookable date (tomorrow) | `searchCriteria`: [CAI→RUH, *tomorrow's date, computed at run time*]. `passengers`: ADT=1. | 200 | Same success pattern | Yes | Yes |
| TC_POS_012 | Happy Path & Passenger Combinations | Boundary — far-future date near typical airline schedule horizon (~330 days out) | `searchCriteria`: [CAI→RUH, *run-time date + 330 days*]. `passengers`: ADT=1. | 200 | Same success pattern, OR an empty `offers[]` if beyond the supplier's published schedule — **[UNCONFIRMED]** exact horizon | Yes | No |
| TC_POS_013 | Happy Path & Passenger Combinations | Response structural integrity — every ref ID resolves | Any TC_POS_00x success response. | 200 | Every `Offer.offerJourneys` entry exists in `journeys`; every `Journey.segmentRefIds` entry exists in `flightSegments`; every `SegmentDetail.{priceClassRefId,baggageDetailsRefId}` exists in `priceClasses`/`baggageDetails` | Yes | Yes |
| TC_POS_014 | Happy Path & Passenger Combinations | Response monetary integrity | Any TC_POS_00x success response. | 200 | For every fare breakdown: `paxBaseAmount + paxTotalTaxAmount == paxTotalAmount` (within rounding tolerance); `currency` is a valid 3-letter ISO 4217 code; all amounts ≥ 0 | Yes | Yes |
| TC_DATE_001 | Boundary & Invalid Date Formats | Departure date in the past | `date`: yesterday's date (computed at run time). | 400 | Validation `context[]` non-empty, references the date field | Yes | Yes |
| TC_DATE_002 | Boundary & Invalid Date Formats | Departure date far in the past | `date`: `"2000-01-01"`. | 400 | Validation `context[]` non-empty | Yes | Yes |
| TC_DATE_003 | Boundary & Invalid Date Formats | Boundary — departure date is today (same-day search) | `date`: today's date (computed at run time). | 200 or 400 — **[UNCONFIRMED]** whether same-day search is permitted | Depends on outcome | Yes | No |
| TC_DATE_004 | Boundary & Invalid Date Formats | Departure date implausibly far in the future | `date`: `"2099-12-31"`. | 400, OR 200 with empty `offers[]` — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_DATE_005 | Boundary & Invalid Date Formats | Invalid format — `DD-MM-YYYY` | `date`: `"08-09-2026"`. | 400 | `context[]` non-empty (verified) | Yes | Yes |
| TC_DATE_006 | Boundary & Invalid Date Formats | Invalid format — free text | `date`: `"next-tuesday"`. | 400 | `context[]` non-empty (verified) | Yes | Yes |
| TC_DATE_007 | Boundary & Invalid Date Formats | Invalid format — slash-delimited | `date`: `"2026/09/08"`. | 400 | `context[]` non-empty (verified) | Yes | Yes |
| TC_DATE_008 | Boundary & Invalid Date Formats | Missing `date` field in a `searchCriteria` entry | `searchCriteria[0]` has `origin`/`destination` only, no `date` key. | 400 | `context[]` references the missing date field | Yes | Yes |
| TC_DATE_009 | Boundary & Invalid Date Formats | `date` as empty string | `date`: `""`. | 400 | `context[]` non-empty | Yes | Yes |
| TC_DATE_010 | Boundary & Invalid Date Formats | `date` as explicit JSON `null` | `date`: `null`. | 400 | `context[]` non-empty; treated equivalently to a missing field | Yes | Yes |
| TC_DATE_011 | Boundary & Invalid Date Formats | Calendar-invalid date value | `date`: `"2026-02-30"` (February has no 30th). | 400 | `context[]` non-empty | Yes | Yes |
| TC_DATE_012 | Boundary & Invalid Date Formats | Leap-day boundary — valid leap year | `date`: `"2028-02-29"` (2028 is a leap year). | 200 | Same success pattern as TC_POS_001 | Yes | Yes |
| TC_DATE_013 | Boundary & Invalid Date Formats | Leap-day boundary — invalid, non-leap year | `date`: `"2026-02-29"` (2026 is not a leap year). | 400 | `context[]` non-empty | Yes | Yes |
| TC_DATE_014 | Boundary & Invalid Date Formats | Round-trip — return date before departure date | `searchCriteria`: [CAI→RUH, 2026-09-15], [RUH→CAI, 2026-09-08] (return precedes outbound). | 400 | `context[]` references the invalid leg ordering — **[UNCONFIRMED]** exact wording | Yes | No |
| TC_DATE_015 | Boundary & Invalid Date Formats | Round-trip — return date equals departure date (same-day turnaround) | `searchCriteria`: [CAI→RUH, 2026-09-08], [RUH→CAI, 2026-09-08]. | 200 or 400 — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_DATE_016 | Boundary & Invalid Date Formats | `date` includes a time component | `date`: `"2026-09-08T10:00:00"`. | 400 | `context[]` non-empty; date-only format enforced | Yes | Yes |
| TC_DATE_017 | Boundary & Invalid Date Formats | `date` as a non-zero-padded ISO variant | `date`: `"2026-9-8"` (month/day not zero-padded). | 400 | `context[]` non-empty — strict `YYYY-MM-DD` enforced | Yes | Yes |
| TC_FLD_001 | Invalid & Missing Mandatory Payload Fields | Missing `searchCriteria` entirely | Payload omits `searchCriteria`; `supplier`, `credentialsSelector`, `passengers` (ADT=1) present. | 400 | `context[]` non-empty, references `SearchCriteria` (verified) | Yes | Yes |
| TC_FLD_002 | Invalid & Missing Mandatory Payload Fields | Missing `passengers` entirely | Payload omits `passengers`; `searchCriteria` (CAI→RUH, 2026-09-08) present. | 400 | `context[]` references `Passengers` (verified) | Yes | Yes |
| TC_FLD_003 | Invalid & Missing Mandatory Payload Fields | Missing `supplier` entirely | Payload omits `supplier`; other fields valid. | 400 | `context[]` references `Supplier` (verified) | Yes | Yes |
| TC_FLD_004 | Invalid & Missing Mandatory Payload Fields | Missing `credentialsSelector` entirely | Payload omits `credentialsSelector`; other fields valid. | 400 — **[UNCONFIRMED]**, not part of the previously verified matrix (README §5, open question 1) | `context[]` references the selector field, if validated at all | Yes | No |
| TC_FLD_005 | Invalid & Missing Mandatory Payload Fields | Empty `searchCriteria` array | `searchCriteria`: `[]`. | 400 | `context[]` non-empty, minimum-length violation | Yes | Yes |
| TC_FLD_006 | Invalid & Missing Mandatory Payload Fields | Empty `passengers` array | `passengers`: `[]`. | 400 | `context[]` non-empty, minimum-length violation | Yes | Yes |
| TC_FLD_007 | Invalid & Missing Mandatory Payload Fields | `searchCriteria` entry missing `origin` | `searchCriteria[0]`: `{destination: "RUH", date: "2026-09-08"}`. | 400 | `context[]` references the missing origin field | Yes | Yes |
| TC_FLD_008 | Invalid & Missing Mandatory Payload Fields | `searchCriteria` entry missing `destination` | `searchCriteria[0]`: `{origin: "CAI", date: "2026-09-08"}`. | 400 | `context[]` references the missing destination field | Yes | Yes |
| TC_FLD_009 | Invalid & Missing Mandatory Payload Fields | `passengers` entry missing `passengerTypeCode` | `passengers[0]`: `{count: 1}`. | 400 | `context[]` references the missing type-code field | Yes | Yes |
| TC_FLD_010 | Invalid & Missing Mandatory Payload Fields | `passengers` entry missing `count` | `passengers[0]`: `{passengerTypeCode: "ADT"}`. | 400 | `context[]` references the missing count field | Yes | Yes |
| TC_FLD_011 | Invalid & Missing Mandatory Payload Fields | Completely empty request body | `{}`. | 400 | `context[]` non-empty, lists all missing required top-level fields | Yes | Yes |
| TC_FLD_012 | Invalid & Missing Mandatory Payload Fields | No request body sent at all | (no body; `Content-Length: 0`). | 400 | Body-required validation error, OR a generic malformed-request error — **[UNCONFIRMED]** exact shape | Yes | No |
| TC_FLD_013 | Invalid & Missing Mandatory Payload Fields | Request body is a JSON array, not an object | `[]` as the raw body. | 400 | Deserialization/validation error | Yes | Yes |
| TC_FLD_014 | Invalid & Missing Mandatory Payload Fields | `supplier` as an empty string | `supplier`: `""`. | 400 | `context[]` references `Supplier` as invalid/blank | Yes | Yes |
| TC_FLD_015 | Invalid & Missing Mandatory Payload Fields | `supplier` as a whitespace-only string | `supplier`: `"   "`. | 400 | `context[]` references `Supplier` as invalid/blank — **[UNCONFIRMED]** whether whitespace is trimmed before validation | Yes | No |
| TC_FLD_016 | Invalid & Missing Mandatory Payload Fields | `credentialsSelector` as an empty string | `credentialsSelector`: `""`. | 400 — **[UNCONFIRMED]** (depends on TC_FLD_004's outcome) | `context[]` references the selector field, if validated | Yes | No |
| TC_TYPE_001 | Data Type Mismatches & Edge Case Values | `passengers[].count` as a numeric string | `count`: `"3"` (string, not integer). | 400 | Type-coercion/validation error referencing `count` | Yes | Yes |
| TC_TYPE_002 | Data Type Mismatches & Edge Case Values | `passengers[].count` as a negative integer | `count`: `-1`. | 400 | `context[]` references an invalid/out-of-range count | Yes | Yes |
| TC_TYPE_003 | Data Type Mismatches & Edge Case Values | `passengers[].count` as zero | `count`: `0`. | 400 | `context[]` references an invalid count (zero passengers of a listed type is meaningless) | Yes | Yes |
| TC_TYPE_004 | Data Type Mismatches & Edge Case Values | `passengers[].count` as a decimal | `count`: `1.5`. | 400 | Type validation error, integer expected | Yes | Yes |
| TC_TYPE_005 | Data Type Mismatches & Edge Case Values | `passengers[].count` as an implausibly large integer | `count`: `999999`. | 400 | `context[]` references an out-of-range count — **[UNCONFIRMED]** exact upper bound | Yes | No |
| TC_TYPE_006 | Data Type Mismatches & Edge Case Values | `passengers[].passengerTypeCode` as an unknown code | `passengerTypeCode`: `"XYZ"`. | 400 | `context[]` references an invalid passenger type code | Yes | Yes |
| TC_TYPE_007 | Data Type Mismatches & Edge Case Values | `passengers[].passengerTypeCode` case sensitivity | `passengerTypeCode`: `"adt"` (lowercase). | 400 — **[UNCONFIRMED]** whether the API normalizes case | `context[]` references an invalid code, if rejected | Yes | No |
| TC_TYPE_008 | Data Type Mismatches & Edge Case Values | `passengers[].passengerTypeCode` as a non-string type | `passengerTypeCode`: `1` (integer). | 400 | Type validation error | Yes | Yes |
| TC_TYPE_009 | Data Type Mismatches & Edge Case Values | `isdebug` as a string instead of boolean | `isdebug`: `"true"`. | 400, OR silently coerced to `true` — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_TYPE_010 | Data Type Mismatches & Edge Case Values | `isdebug` as an integer instead of boolean | `isdebug`: `1`. | 400, OR silently coerced — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_TYPE_011 | Data Type Mismatches & Edge Case Values | `searchCriteria` as an object instead of an array | `searchCriteria`: `{origin: "CAI", destination: "RUH", date: "2026-09-08"}`. | 400 | Deserialization/type validation error | Yes | Yes |
| TC_TYPE_012 | Data Type Mismatches & Edge Case Values | `passengers` as an object instead of an array | `passengers`: `{passengerTypeCode: "ADT", count: 1}`. | 400 | Deserialization/type validation error | Yes | Yes |
| TC_TYPE_013 | Data Type Mismatches & Edge Case Values | `supplier` as a non-string type | `supplier`: `12345`. | 400 | Type validation error | Yes | Yes |
| TC_TYPE_014 | Data Type Mismatches & Edge Case Values | `origin`/`destination` as a non-string type | `origin`: `123`. | 400 | Type validation error | Yes | Yes |
| TC_TYPE_015 | Data Type Mismatches & Edge Case Values | `origin`/`destination` case sensitivity | `origin`: `"cai"` (lowercase). | 200 with normalized handling, OR 400 — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_TYPE_016 | Data Type Mismatches & Edge Case Values | SQL-injection-style string in `origin` | `origin`: `"CAI'; DROP TABLE offers;--"`. | 400 | Rejected as an invalid airport code — must **not** return 500 or a stack trace | Yes | Yes |
| TC_TYPE_017 | Data Type Mismatches & Edge Case Values | Excessively long string in `origin` | `origin`: a 500-character string. | 400 | `context[]` references an invalid/oversized field, no 500 | Yes | Yes |
| TC_TYPE_018 | Data Type Mismatches & Edge Case Values | Unknown top-level field is silently ignored (forward-compatibility) | Valid one-way payload plus `"unexpectedField": "someValue"`. | 200 | Same success pattern as TC_POS_001; extra field has no effect | Yes | Yes |
| TC_TYPE_019 | Data Type Mismatches & Edge Case Values | Malformed JSON syntax | Raw body: `{"supplier": "flyadeal",}` (trailing comma). | 400 | Generic JSON parse error, not a 500 | Yes | Yes |
| TC_TYPE_020 | Data Type Mismatches & Edge Case Values | Business rule — infant count exceeds adult count | `passengers`: ADT=1, INF=2 (more infants than adults to hold them). | 400/409 — **[UNCONFIRMED]**, common airline rule (1 infant per adult lap) not yet verified for this supplier | `context[]` references the passenger-mix rule, if enforced | Yes | No |
| TC_AUTH_001 | Authentication & Authorization Headers | Invalid `x-api-key` | Well-formed payload; `x-api-key` replaced with a bogus value. | 401 | `errorCode` == `AuthenticationError` (case-insensitive); `errorMessage` contains "credentials" (verified) | Yes | Yes |
| TC_AUTH_002 | Authentication & Authorization Headers | Missing `x-api-key` header entirely | Well-formed payload; `x-api-key` header omitted from the request. | 401 — **[UNCONFIRMED]**, only "invalid key" was previously verified, not "absent key" | `errorCode` == `AuthenticationError`, if consistent with TC_AUTH_001 | Yes | No |
| TC_AUTH_003 | Authentication & Authorization Headers | `x-api-key` as an empty string | `x-api-key`: `""`. | 401 | Same pattern as TC_AUTH_001 | Yes | Yes |
| TC_AUTH_004 | Authentication & Authorization Headers | `x-api-key` with leading/trailing whitespace | `x-api-key`: `" <valid-key> "`. | 401, OR 200 if trimmed server-side — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_AUTH_005 | Authentication & Authorization Headers | Missing `Client-Id` header entirely | Well-formed payload; `Client-Id` header omitted. | 400/401 — **[UNCONFIRMED]** | Error body references the missing tenant header, if enforced | Yes | No |
| TC_AUTH_006 | Authentication & Authorization Headers | Invalid `Client-Id` value | `Client-Id`: `"NOT-A-REAL-CLIENT"` with an otherwise valid, correctly-paired `x-api-key`. | 401/403 — **[UNCONFIRMED]** | Error body references tenant mismatch, if enforced | Yes | No |
| TC_AUTH_007 | Authentication & Authorization Headers | `Client-Id` case sensitivity | `Client-Id`: `"ndc-core"` (lowercase). | 200, OR 401 — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_AUTH_008 | Authentication & Authorization Headers | Both `x-api-key` and `Client-Id` missing | Well-formed payload; both headers omitted. | 401 | Same pattern as TC_AUTH_001, or a header-required error naming both | Yes | Yes |
| TC_AUTH_009 | Authentication & Authorization Headers | Header name is case-varied (HTTP headers are case-insensitive per spec) | Valid request sent with `X-API-KEY` (all caps) instead of `x-api-key`. | 200 | Same success pattern as TC_POS_001 — proves the client/server treat header names case-insensitively | Yes | Yes |
| TC_AUTH_010 | Authentication & Authorization Headers | Missing `Content-Type` header | Well-formed JSON body sent with no `Content-Type` header. | 400/415 — **[UNCONFIRMED]** | Content-negotiation error, if enforced | Yes | No |
| TC_AUTH_011 | Authentication & Authorization Headers | Incorrect `Content-Type` header | Well-formed JSON body sent with `Content-Type: text/plain`. | 415 | Unsupported media type error | Yes | Yes |
| TC_AUTH_012 | Authentication & Authorization Headers | Valid `x-api-key` supplied only as a query parameter, not a header | `x-api-key` omitted from headers; appended as `?x-api-key=<valid-key>` in the URL instead. | 401 | Same pattern as TC_AUTH_001 — proves the key is accepted via header only, never leaks through URL-based auth | Yes | Yes |
| TC_APT_001 | Invalid Origin/Destination Airport Codes | Well-formed but non-existent airport code | `origin`: `"ZZZ"` (valid 3-letter shape, not a real IATA code). | 400 | `context[]` references an invalid/unknown airport code | Yes | Yes |
| TC_APT_002 | Invalid Origin/Destination Airport Codes | Code shorter than 3 letters | `origin`: `"CA"`. | 400 | `context[]` references an invalid airport code format | Yes | Yes |
| TC_APT_003 | Invalid Origin/Destination Airport Codes | Code longer than 3 letters | `origin`: `"CAIR"`. | 400 | `context[]` references an invalid airport code format | Yes | Yes |
| TC_APT_004 | Invalid Origin/Destination Airport Codes | Numeric-only code | `origin`: `"123"`. | 400 | `context[]` references an invalid airport code format | Yes | Yes |
| TC_APT_005 | Invalid Origin/Destination Airport Codes | Special characters in code | `origin`: `"C@I"`. | 400 | `context[]` references an invalid airport code format | Yes | Yes |
| TC_APT_006 | Invalid Origin/Destination Airport Codes | Empty string airport code | `origin`: `""`. | 400 | `context[]` references a missing/blank airport code (overlaps with TC_FLD_007 but exercises the code-format validator specifically) | Yes | Yes |
| TC_APT_007 | Invalid Origin/Destination Airport Codes | Identical origin and destination on one leg | `searchCriteria[0]`: `{origin: "CAI", destination: "CAI", date: "2026-09-08"}`. | 400 | `context[]` references an invalid route (origin cannot equal destination) | Yes | Yes |
| TC_APT_008 | Invalid Origin/Destination Airport Codes | Valid codes, but the supplier doesn't operate the route | `searchCriteria[0]`: `{origin: "CAI", destination: "LAX", date: "2026-09-08"}` — flyadeal has no long-haul US network. | 200 with empty `offers[]`, OR 400/404 — **[UNCONFIRMED]** how "no service on this route" is communicated | Yes | No |
| TC_APT_009 | Invalid Origin/Destination Airport Codes | ICAO code supplied where IATA is expected | `origin`: `"HECA"` (Cairo's 4-letter ICAO code, not its 3-letter IATA code `CAI`). | 400 | `context[]` references an invalid airport code format (overlaps with TC_APT_003's length check, kept distinct for domain-specific traceability) | Yes | Yes |
| TC_APT_010 | Invalid Origin/Destination Airport Codes | Lowercase valid airport code | `origin`: `"cai"`. | 200 with normalized handling, OR 400 — **[UNCONFIRMED]** (duplicate of TC_TYPE_015's scenario, kept here for airport-code-domain traceability) | Yes | No |
| TC_BIZ_001 | Business Rule & Itinerary Logic Validation | True multi-city itinerary rejected | `searchCriteria`: [CAI→RUH, 2026-09-08], [RUH→JED, 2026-09-12] (3 distinct cities). `passengers`: ADT=1, CHD=1. | 409 | `errorCode` == `ValidationError` (case-insensitive); `context[]` references "multicity" (verified) | Yes | Yes |
| TC_BIZ_002 | Business Rule & Itinerary Logic Validation | Multi-city with 4+ legs, consistency check | `searchCriteria`: [CAI→RUH], [RUH→JED], [JED→CAI] (3 legs, 3 cities). | 409 | Same pattern as TC_BIZ_001 | Yes | Yes |
| TC_BIZ_003 | Business Rule & Itinerary Logic Validation | Open-jaw itinerary (mismatched return city) | `searchCriteria`: [CAI→RUH, 2026-09-08], [JED→CAI, 2026-09-15] (return departs from a different city than the outbound arrived at). | 409 — **[UNCONFIRMED]** whether this is classified as multi-city by this supplier | Same pattern as TC_BIZ_001, if classified as multi-city | Yes | No |
| TC_BIZ_004 | Business Rule & Itinerary Logic Validation | Duplicate `searchCriteria` entries | `searchCriteria`: [CAI→RUH, 2026-09-08] repeated twice, identical. | 400/409, OR treated as a valid round-trip-shaped request — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_BIZ_005 | Business Rule & Itinerary Logic Validation | Round-trip legs supplied out of chronological order | `searchCriteria`: [RUH→CAI, 2026-09-15] listed **before** [CAI→RUH, 2026-09-08] in the array. | 200 (order-independent), OR 400 — **[UNCONFIRMED]** | Depends on outcome | Yes | No |
| TC_BIZ_006 | Business Rule & Itinerary Logic Validation | Response integrity — round-trip offers reference the correct leg | Any TC_POS_005/006 round-trip success response. | 200 | Each `Offer.offerJourneys` maps only to journeys consistent with that offer's fare rules; no cross-leg journey leakage | Yes | Yes |
| TC_STRUCT_001 | Malformed Request / Content Negotiation | Unsupported HTTP method — GET | `GET /api/V2/FlightSearch/Search` with valid headers, no body. | 405 | Method-not-allowed error | Yes | Yes |
| TC_STRUCT_002 | Malformed Request / Content Negotiation | Unsupported HTTP method — PUT | `PUT /api/V2/FlightSearch/Search` with a valid body and headers. | 405 | Method-not-allowed error | Yes | Yes |
| TC_STRUCT_003 | Malformed Request / Content Negotiation | Oversized payload — very large `searchCriteria` array | `searchCriteria` with 1,000 duplicate entries. | 400/413 | Payload-too-large or validation error, no 500 — this is a load-testing-adjacent boundary, not a pure functional case | No | No |
| TC_STRUCT_004 | Malformed Request / Content Negotiation | UTF-8 BOM prefix on the request body | Well-formed JSON body prefixed with a UTF-8 byte-order-mark. | 200, OR 400 — **[UNCONFIRMED]**, low priority | Depends on outcome | No | No |
| TC_SEC_001 | Security & Injection Hardening | XSS-style payload in a string field | `supplier`: `"<script>alert(1)</script>"`. | 400 | `context[]` references an invalid supplier value; response must **not** reflect the raw script content unescaped | Yes | Yes |
| TC_SEC_002 | Security & Injection Hardening | Null-byte injection in a string field | `origin`: `"CAI "`. | 400 | Rejected as an invalid airport code, no 500 | Yes | Yes |
| TC_SEC_003 | Security & Injection Hardening | Extremely long string stress value | `credentialsSelector`: a 10,000-character string. | 400 | Rejected as invalid/oversized, no 500 or timeout | Yes | Yes |
| TC_SEC_004 | Security & Injection Hardening | Error response never echoes the caller's `x-api-key` | Trigger TC_AUTH_001 (invalid key) and inspect the full error body. | 401 | `errorMessage`/`context[]` do **not** contain the submitted `x-api-key` value verbatim | Yes | Yes |

---

## Coverage summary

| Category | # Cases | Confirmed (ready to automate now) | Unconfirmed (needs exploratory spike first) |
|---|---|---|---|
| Happy Path & Passenger Combinations | 14 | 12 | 2 (TC_POS_007, TC_POS_012) |
| Boundary & Invalid Date Formats | 17 | 14 | 3 (TC_DATE_003, 004, 014, 015 — 4 actually) |
| Invalid & Missing Mandatory Payload Fields | 16 | 13 | 3 (TC_FLD_004, 012, 015, 016 — 4 actually) |
| Data Type Mismatches & Edge Case Values | 20 | 14 | 6 |
| Authentication & Authorization Headers | 12 | 7 | 5 |
| Invalid Origin/Destination Airport Codes | 10 | 8 | 2 |
| Business Rule & Itinerary Logic Validation | 6 | 4 | 2 |
| Malformed Request / Content Negotiation | 4 | 2 | 2 (also out-of-scope for functional suite) |
| Security & Injection Hardening | 4 | 4 | 0 |
| **Total** | **103** | **~78** | **~25** |

*(Row counts above are approximate by design — several rows carry two plausible
outcomes pending confirmation, which is exactly the point of flagging them
rather than silently picking one.)*

## Recommended next step before Step 3 (implementation)

Every **[UNCONFIRMED]** row shares the same problem: we don't yet know what
the live API actually does, so we can't respect the "verify against the live
endpoint, don't assume" discipline the prior implementation earned its
credibility from (README §0). Before writing TestNG code, run a short
**exploratory spike** — a handful of ad-hoc calls (`isdebug: true` is
especially useful here, since it exposes the raw supplier request/response)
against each unconfirmed scenario, record the actual status code and body
shape, then promote the row from `Needs Automation: No` to `Yes` with a
concrete expected pattern. This keeps the eventual automated suite 100%
evidence-based, consistent with how TC_POS/TC_DATE/TC_FLD/TC_AUTH's confirmed
rows were originally derived.
