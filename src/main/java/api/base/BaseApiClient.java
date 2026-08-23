package api.base;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Generic wrapper over RestAssured's HTTP verbs. Knows nothing about any
 * endpoint, request/response shape, or business rule — only how to fire an
 * HTTP call against a given {@link RequestSpecification} and hand back the
 * raw {@link Response}. Status-code assertions and deserialization are
 * deliberately not this class's job — see {@link ResponseHandler}.
 * <p>
 * Every endpoint wrapper (FlightSearchApi today; a future
 * BookingApi/SeatMapApi tomorrow) is composed on top of this same class
 * rather than subclassing it, so a domain wrapper's public API only ever
 * exposes its own domain actions, not raw HTTP verbs.
 */
public class BaseApiClient {

    public Response post(RequestSpecification spec, String endpoint, Object body) {
        return RestAssured.given().spec(spec).body(body).when().post(endpoint);
    }

    public Response post(RequestSpecification spec, String endpoint) {
        return RestAssured.given().spec(spec).when().post(endpoint);
    }

    public Response get(RequestSpecification spec, String endpoint) {
        return RestAssured.given().spec(spec).when().get(endpoint);
    }

    public Response get(RequestSpecification spec, String endpoint, Map<String, ?> queryParams) {
        return RestAssured.given().spec(spec).queryParams(queryParams).when().get(endpoint);
    }

    public Response put(RequestSpecification spec, String endpoint, Object body) {
        return RestAssured.given().spec(spec).body(body).when().put(endpoint);
    }

    public Response patch(RequestSpecification spec, String endpoint, Object body) {
        return RestAssured.given().spec(spec).body(body).when().patch(endpoint);
    }

    public Response delete(RequestSpecification spec, String endpoint) {
        return RestAssured.given().spec(spec).when().delete(endpoint);
    }

    public Response delete(RequestSpecification spec, String endpoint, Object body) {
        return RestAssured.given().spec(spec).body(body).when().delete(endpoint);
    }
}
