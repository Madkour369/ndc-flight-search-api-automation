package models.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * Explicit @JsonProperty for documentation: the wire field is the
     * single lowercase word "isdebug", not the camelCase "isDebug" a
     * Lombok/Jackson boolean-getter convention might otherwise suggest.
     */
    @JsonProperty("isdebug")
    private Boolean isdebug;

    private List<SearchCriteria> searchCriteria;
    private List<Passenger> passengers;
}
