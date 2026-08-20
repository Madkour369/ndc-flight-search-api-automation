package com.tildetech.ndc.automation.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

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
    Boolean haveBundles;
    Boolean canBeHeld;
    Boolean isDealCodeApplied;
    String appliedDealCode;
    Boolean isPromoted;
    String appliedPromotionCode;
    Boolean isAncillaryRequired;
    Boolean offerHasAncillary;
    Boolean offerHasSeat;
    Boolean isBaggageRequired;
    Boolean isMealRequired;
    Boolean isSeatMapRequired;
    Boolean isBundleRequired;
    Object sellBundleRequiresAncillary;
}
