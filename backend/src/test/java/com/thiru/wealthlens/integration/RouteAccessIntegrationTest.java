package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every route the application serves, read from Spring's own route table, against the zones (W-358a, D-850, D-855): a route sits in
 * exactly one of `/api/v1/{public|me|admin}/` (or is the notice page or the error page); outside public it refuses a caller without a
 * token; an admin route refuses a user's token; a `/me` route never names a user, so it cannot reach another user's data. A route added
 * later is in the table and so in this test, which is the point — an unlisted path must never open by accident, as
 * {@code POST /properties/user/{email}/sales} did while the fallback was {@code permitAll}.
 */
class RouteAccessIntegrationTest extends AbstractIntegrationTest {

    private static final String PUBLIC = "/api/v1/public/";
    private static final String ME = "/api/v1/me/";
    private static final String ADMIN = "/api/v1/admin/";

    /** Outside the zones by D-855 C5a: the notice as a page, and where a refusal is rendered. */
    private static final Set<String> OUTSIDE_THE_ZONES = Set.of("/privacy", "/error");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private record Route(RequestMethod method, String pattern) {

        String path() {
            return pattern.replaceAll("\\{[^}]+}", "x");
        }

        boolean isPublic() {
            return pattern.startsWith(PUBLIC) || OUTSIDE_THE_ZONES.contains(pattern);
        }

        boolean isAdmin() {
            return pattern.startsWith(ADMIN);
        }

        boolean isMe() {
            return pattern.startsWith(ME);
        }

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }

    private List<Route> routes() {
        Set<String> seen = new TreeSet<>();
        List<Route> routes = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    if (seen.add(method + " " + pattern)) {
                        routes.add(new Route(method, pattern));
                    }
                }
            }
        }
        return routes;
    }

    /**
     * What a call answered. A refusal by the security chain carries {@code WWW-Authenticate}; the application's own 401 (a
     * wrong login, {@code ControllerAdviser}'s {@code BadCredentialsException}) does not, and is not a refusal of the token.
     */
    private record Answer(int status, boolean challenged) {

        boolean refusedForTheToken() {
            return status == 401 && challenged;
        }
    }

    /** Over {@code java.net.http}: RestAssured's GET throws an NPE in this suite's Groovy dispatch (session 59). */
    private static Answer call(Route route, String token) {
        return call(route, route.path(), token);
    }

    private static Answer call(Route route, String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":" + RestAssured.port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        boolean hasBody = route.method() == RequestMethod.POST || route.method() == RequestMethod.PUT || route.method() == RequestMethod.PATCH;
        request.method(route.method().name(), hasBody ? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody());
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding());
            return new Answer(response.statusCode(), response.headers().firstValue("WWW-Authenticate").isPresent());
        } catch (IOException e) {
            throw new UncheckedIOException(route.toString(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(route.toString(), e);
        }
    }

    @Test
    void routes_whenReadFromTheRouteTable_includeTheRoutesThisTestWasWrittenFor() {
        // When
        List<String> names = routes().stream().map(Route::toString).toList();

        // Then
        assertThat(names).contains("POST /api/v1/me/portfolio/properties/sales", "POST /api/v1/public/auth/login",
                "GET /api/v1/me/portfolio/stocks", "GET /api/v1/admin/portfolio/users/{email}/stocks");
    }

    @Test
    void everyRoute_whenReadFromTheRouteTable_sitsInOneZone() {
        // When
        List<String> outside = routes().stream().filter(route -> !route.isPublic() && !route.isMe() && !route.isAdmin())
                .map(Route::toString).toList();

        // Then — a controller without a zone annotation, or in a module ApiZones does not name, lands here
        assertThat(outside).as("routes outside /api/v1/{public|me|admin}/ and not named in D-855 C5a").isEmpty();
    }

    @Test
    void everyMeRoute_whenReadFromTheRouteTable_namesNoUser() {
        // When
        List<String> naming = routes().stream().filter(Route::isMe).filter(route -> route.pattern().contains("{email}"))
                .map(Route::toString).toList();

        // Then — the user of a /me route is the token's (D-855 C4a)
        assertThat(naming).as("/me routes that take a user from the path").isEmpty();
    }

    @Test
    void everyRoute_whenCalledWithoutAToken_refusesUnlessPublic() {
        // Given
        List<String> open = new ArrayList<>();

        // When
        for (Route route : routes()) {
            Answer answer = route.isPublic() ? null : call(route, null);
            if (answer != null && !answer.refusedForTheToken()) {
                open.add(route + " answered " + answer.status());
            }
        }

        // Then
        assertThat(open).as("routes that answered without a token but are not public").isEmpty();
    }

    @Test
    void everyPublicRoute_whenCalledWithoutAToken_isNotRefused() {
        // Given
        List<String> refused = new ArrayList<>();

        // When
        for (Route route : routes()) {
            if (route.isPublic() && call(route, null).refusedForTheToken()) {
                refused.add(route.toString());
            }
        }

        // Then
        assertThat(refused).as("public routes that refused a caller without a token").isEmpty();
    }

    @Test
    void everyAdminRoute_whenCalledWithAUsersToken_isForbidden() {
        // Given
        String user = generateToken("an-ordinary-user@example.com");
        List<String> reached = new ArrayList<>();

        // When
        for (Route route : routes()) {
            if (route.isAdmin()) {
                Answer answer = call(route, user);
                if (answer.status() != 403) {
                    reached.add(route + " answered " + answer.status());
                }
            }
        }

        // Then
        assertThat(reached).as("admin routes that a user's token was not refused on").isEmpty();
    }

    @Test
    void everyMeRoute_whenCalledWithAUsersToken_isNotRefusedForTheToken() {
        // Given — a fresh user per route: an erasure route puts its user on hold, after which that user's token is refused
        // by design (W-237d), and would otherwise refuse every route the loop reaches after it
        List<String> refused = new ArrayList<>();
        int next = 0;

        // When
        for (Route route : routes()) {
            if (route.isMe()) {
                String user = "owner-" + next++ + "@example.com";
                if (call(route, generateToken(user)).refusedForTheToken()) {
                    refused.add(route.toString());
                }
            }
        }

        // Then
        assertThat(refused).as("/me routes that refused their own user's token").isEmpty();
    }

    @Test
    void meRoute_whenTwoUsersHoldData_answersOnlyTheCallersOwn() throws IOException, InterruptedException {
        // Given — a grievance each
        raise("anu@example.com", "Anu's");
        raise("bala@example.com", "Bala's");

        // When
        HttpResponse<String> anu = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":"
                + RestAssured.port + ME + "privacy/grievances")).header("Authorization", "Bearer " + generateToken("anu@example.com"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        // Then
        assertThat(anu.statusCode()).isEqualTo(200);
        assertThat(anu.body()).contains("Anu's").doesNotContain("Bala's");
    }

    private void raise(String email, String text) throws IOException, InterruptedException {
        HttpResponse<String> raised = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(RestAssured.baseURI + ":"
                + RestAssured.port + ME + "privacy/grievances")).header("Authorization", "Bearer " + generateToken(email))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"text\": \"" + text + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(raised.statusCode()).as(raised.body()).isEqualTo(201);
    }
}
