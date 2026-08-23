package models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class SegmentDetail {

    String segmentRefId;
    String priceClassRefId;
    String baggageDetailsRefId;
    String cabinCode;
    String rbd;
}
