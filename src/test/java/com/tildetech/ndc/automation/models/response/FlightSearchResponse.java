package com.tildetech.ndc.automation.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

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

    /**
     * Only populated when the request is sent with "isdebug": true.
     */
    String supplierRequest;
    String supplierResponse;
}
