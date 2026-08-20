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
public class PriceDetails {

    Money totalAmount;
    Money totalTaxAmount;
    Money totalBaseAmount;
    List<TaxFee> taxesAndFees;
    Object discount;
}
