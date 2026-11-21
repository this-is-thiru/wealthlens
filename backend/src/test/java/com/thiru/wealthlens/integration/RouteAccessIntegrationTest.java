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
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Every route the application serves, read from Spring's own route table, against the access rules (W-358a, D-850): a route
 * not named public refuses a caller without a token, and a route naming a user in its path refuses another user's token. A
 * route added later is in the table and so in this test, which is the point — an unlisted path must never open by accident,
 * as {@code POST /properties/user/{email}/sales} did while the fallback was {@code permitAll}.
 */
class RouteAccessIntegrationTest extends AbstractIntegrationTest {

    /** Public by name in {@code AuthConfig}; every other route needs a login. */
    private static final List<PathPattern> PUBLIC = parse("/auth/login", "/auth/register", "/helper/**", "/finances/**",
            "/template/**", "/privacy/rights", "/privacy", "/error");

    private static final String OWNER = "owner-of-the-path@example.com";
    private static final String INTRUDER = "someone-else@example.com";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private record Route(RequestMethod method, String pattern) {

        String path() {
            return path(OWNER);
        }

        String path(String user) {
            return pattern.replace("{email}", user).replaceAll("\\{[^}]+}", "x");
        }

        boolean namesAUser() {
            return pattern.contains("{email}");
        }

        boolean isPublic() {
            PathContainer path = PathContainer.parsePath(pattern);
            return PUBLIC.stream().anyMatch(p -> p.matches(path));
        }

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }

    private static List<PathPattern> parse(String... patterns) {
        PathPatternParser parser = new PathPatternParser();
        return java.util.Arrays.stream(patterns).map(parser::parse).toList();
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
        assertThat(names).contains("POST /properties/user/{email}/sales", "POST /auth/login", "GET /portfolio/user/{email}/stocks/all");
    }

    @Test
    void everyRoute_whenCalledWithoutAToken_refusesUnlessNamedPublic() {
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
        assertThat(open).as("routes that answered without a token but are not named public in AuthConfig").isEmpty();
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
    void everyRouteNamingAUser_whenCalledWithAnotherUsersToken_refuses() {
        // Given
        String intruder = generateToken(INTRUDER);
        List<String> reached = new ArrayList<>();

        // When
        for (Route route : routes()) {
            if (route.namesAUser()) {
                Answer answer = call(route, intruder);
                if (!answer.refusedForTheToken() && answer.status() != 403) {
                    reached.add(route + " answered " + answer.status());
                }
            }
        }

        // Then
        assertThat(reached).as("routes that let one user's token reach another user's path").isEmpty();
    }

    @Test
    void everyRouteNamingAUser_whenCalledWithItsOwnUsersToken_isNotRefusedForTheToken() {
        // Given — a fresh user per route: an erasure route puts its user on hold, after which that user's token is refused
        // by design (W-237d), and would otherwise refuse every route the loop reaches after it
        List<String> refused = new ArrayList<>();
        int next = 0;

        // When
        for (Route route : routes()) {
            if (route.namesAUser()) {
                String user = "owner-" + next++ + "@example.com";
                if (call(route, route.path(user), generateToken(user)).refusedForTheToken()) {
                    refused.add(route.toString());
                }
            }
        }

        // Then
        assertThat(refused).as("routes that refused their own user's token").isEmpty();
    }
}
