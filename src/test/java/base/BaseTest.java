package base;

import api.base.BaseApiClient;
import api.base.RequestSpecFactory;
import api.endpoints.FlightSearchApi;
import config.ConfigManager;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import models.request.FlightSearchRequest;
import org.testng.annotations.BeforeSuite;

/**
 * Shared TestNG base for the FlightSearch suite. Structurally reusable by
 * any future module's test classes too: the only FlightSearch-specific
 * member is the {@link FlightSearchApi} field, and every helper is built on
 * the endpoint-agnostic {@link BaseApiClient} / {@link RequestSpecFactory}
 * from the core engine.
 */
public abstract class BaseTest {

    protected final FlightSearchApi flightSearchApi = new FlightSearchApi();
    protected final BaseApiClient apiClient = new BaseApiClient();

    /**
     * Fails the whole suite fast, before a single test runs, if the
     * environment isn't fully configured — see {@link ConfigManager} for
     * the -D/environment-variable precedence rules. The API key's value is
     * deliberately never logged, only whether one is present.
     */
    @BeforeSuite(alwaysRun = true)
    public void verifyEnvironmentConfiguration() {
        System.out.println("[BaseTest] Target base URI : " + ConfigManager.baseUri());
        System.out.println("[BaseTest] Client-Id        : " + ConfigManager.clientId());
        ConfigManager.apiKey();
        System.out.println("[BaseTest] API key          : configured (value withheld from logs)");
    }

    @Step("Execute FlightSearch with the default authenticated spec")
    protected Response search(FlightSearchRequest request) {
        return flightSearchApi.executeSearch(request);
    }

    @Step("Execute FlightSearch with a custom request spec")
    protected Response search(FlightSearchRequest request, RequestSpecification spec) {
        return flightSearchApi.executeSearch(request, spec);
    }

    /**
     * Bypasses the {@link FlightSearchRequest} POJO entirely and posts a
     * raw JSON string — for negative scenarios (malformed JSON, wrong field
     * types, wrong container shapes) that can't be represented by a
     * strongly-typed request model in the first place.
     */
    @Step("Execute FlightSearch with a raw JSON body")
    protected Response searchRaw(String rawJsonBody) {
        return apiClient.post(RequestSpecFactory.defaultSpec(), FlightSearchApi.SEARCH_PATH, rawJsonBody);
    }

    @Step("Call the FlightSearch resource with GET (expected to be unsupported)")
    protected Response searchViaGet() {
        return apiClient.get(RequestSpecFactory.defaultSpec(), FlightSearchApi.SEARCH_PATH);
    }

    @Step("Call the FlightSearch resource with PUT (expected to be unsupported)")
    protected Response searchViaPut(FlightSearchRequest request) {
        return apiClient.put(RequestSpecFactory.defaultSpec(), FlightSearchApi.SEARCH_PATH, request);
    }
}
