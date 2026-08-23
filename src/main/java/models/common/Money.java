package models.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Generic monetary value — {@code {amount, currency}} — shared by every
 * price/tax/fee field across the response, and by any future endpoint's
 * money fields. Not FlightSearch-specific.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Money {

    BigDecimal amount;
    String currency;
}
