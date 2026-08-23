package models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import models.common.Money;

import java.util.List;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class PassengerFareBreakdown {

    Money paxTotalAmount;
    String passengerTypeCode;
    Money paxTotalTaxAmount;
    Money paxBaseAmount;
    List<TaxFee> taxesAndFees;
    List<SegmentDetail> segmentDetails;

    /** Shape not yet pinned down by a verified response — kept opaque rather than guessed. */
    Object discount;
}
