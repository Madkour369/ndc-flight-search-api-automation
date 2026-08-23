package models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceClass {

    String priceClassName;
    String fareDescription;

    /** Shape not yet pinned down by a verified response — kept opaque rather than guessed. */
    Object rulesAndPenalties;
}
