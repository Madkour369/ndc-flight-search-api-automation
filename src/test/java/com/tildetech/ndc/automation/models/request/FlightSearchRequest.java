package com.tildetech.ndc.automation.models.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlightSearchRequest {

    private String supplier;
    private String credentialsSelector;
    private Boolean isdebug;
    private List<SearchCriteria> searchCriteria;
    private List<PassengerRequest> passengers;
}
