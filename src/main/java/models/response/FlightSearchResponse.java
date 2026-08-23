package models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

/**
 * Root success response for {@code POST /api/V2/FlightSearch/Search}. The
 * {@code journeys}/{@code flightSegments}/{@code priceClasses}/
 * {@code baggageDetails} maps are a normalized, ID-referenced graph — see
 * README.md §1.3 — not flattened here, so a future ref-resolution helper
 * can walk {@code Offer -> Journey -> FlightSegment/PriceClass/BaggageDetail}
 * without any information having been lost at deserialization time.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightSearchResponse {

    String responseId;
    String supplier;
    Map<String, Journey> journeys;
    Map<String, FlightSegment> flightSegments;
    Map<String, PriceClass> priceClasses;
    Map<String, BaggageDetail> baggageDetails;
    List<Offer> offers;

    /** Only populated when the request is sent with "isdebug": true. */
    String supplierRequest;
    String supplierResponse;
}
