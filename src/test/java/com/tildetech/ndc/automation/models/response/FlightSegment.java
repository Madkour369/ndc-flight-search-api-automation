package com.tildetech.ndc.automation.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightSegment {

    String origin;
    String destination;
    LocalDateTime departureDateTime;
    LocalDateTime arrivalDateTime;
    String departureTerminal;
    String arrivalTerminal;
    Integer flightTime;
    String operatingCarrierCode;
    String operatingFlightNumber;
    String marketingCarrierCode;
    String marketingFlightNumber;
    String equipment;
}
