package api.endpoints;

import api.base.BaseApiClient;
import api.base.RequestSpecFactory;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import models.request.FlightSearchRequest;

/**
 * Endpoint wrapper for {@code POST /api/V2/FlightSearch/Search}. This is
 * the only class in the framework that knows this specific resource path —
 * everything it's built on ({@link BaseApiClient}, {@link RequestSpecFactory})
 * has no idea FlightSearch exists. A future {@code BookingApi}/{@code
 * SeatMapApi} wrapper looks structurally identical: its own resource path
 * constant, composed with the same generic client.
 */
public class FlightSearchApi {

    /**
     * Public so the test layer can drive this same resource through
     * {@link BaseApiClient} directly for scenarios this wrapper's own API
     * doesn't model on purpose — a raw (non-POJO) body, or an
     * intentionally-wrong HTTP method for a 405 check.
     */
    public static final String SEARCH_PATH = "/api/V2/FlightSearch/Search";

    private final BaseApiClient apiClient;

    public FlightSearchApi() {
        this(new BaseApiClient());
    }

    public FlightSearchApi(BaseApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** Executes a search using the framework's default (fully authenticated) spec. */
    public Response executeSearch(FlightSearchRequest request) {
        return executeSearch(request, RequestSpecFactory.defaultSpec());
    }

    /**
     * Executes a search against a caller-supplied spec — the hook negative
     * tests use to inject a bad/missing header (via
     * {@link RequestSpecFactory}'s override methods) while still going
     * through the same endpoint call as the happy path.
     */
    public Response executeSearch(FlightSearchRequest request, RequestSpecification spec) {
        return apiClient.post(spec, SEARCH_PATH, request);
    }
}
