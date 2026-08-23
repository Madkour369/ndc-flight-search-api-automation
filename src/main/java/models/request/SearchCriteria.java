package models.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchCriteria {

    private String origin;
    private String destination;

    /**
     * Kept as a raw String (not LocalDate) so negative tests can
     * deliberately submit malformed date values (TC_DATE_* in
     * TestCases.md) without the client itself rejecting them before the
     * request ever reaches the API.
     */
    private String date;
}
