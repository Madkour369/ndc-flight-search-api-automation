package models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * The {@code is-}/{@code has-}prefixed Boolean flags carry explicit
 * {@link JsonProperty} annotations: {@code Boolean} (wrapper, not
 * primitive {@code boolean}) fields get a {@code getXxx()} getter from
 * Lombok rather than {@code isXxx()}, and pinning the wire name explicitly
 * keeps deserialization correct regardless of that convention.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Offer {

    String offerId;
    List<String> offerJourneys;
    List<PassengerFareBreakdown> passengerFareBreakdown;
    PriceDetails priceDetails;
    String refundability;

    @JsonProperty("haveBundles")
    Boolean haveBundles;

    @JsonProperty("canBeHeld")
    Boolean canBeHeld;

    @JsonProperty("isDealCodeApplied")
    Boolean isDealCodeApplied;

    String appliedDealCode;

    @JsonProperty("isPromoted")
    Boolean isPromoted;

    String appliedPromotionCode;

    @JsonProperty("isAncillaryRequired")
    Boolean isAncillaryRequired;

    @JsonProperty("offerHasAncillary")
    Boolean offerHasAncillary;

    @JsonProperty("offerHasSeat")
    Boolean offerHasSeat;

    @JsonProperty("isBaggageRequired")
    Boolean isBaggageRequired;

    @JsonProperty("isMealRequired")
    Boolean isMealRequired;

    @JsonProperty("isSeatMapRequired")
    Boolean isSeatMapRequired;

    @JsonProperty("isBundleRequired")
    Boolean isBundleRequired;

    /** Shape not yet pinned down by a verified response — kept opaque rather than guessed. */
    Object sellBundleRequiresAncillary;
}
