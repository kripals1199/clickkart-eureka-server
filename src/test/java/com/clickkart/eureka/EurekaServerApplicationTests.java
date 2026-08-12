// src/test/java/com/clickkart/eureka/EurekaServerApplicationTests.java
package com.clickkart.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Uses the JDK's own {@link HttpClient} against the real embedded server ({@code webEnvironment
 * = RANDOM_PORT}), not MockMvc: Eureka Server registers its own Jersey-based REST resources for
 * {@code /eureka/**} (Netflix Eureka's own JAX-RS layer, not Spring MVC), and MockMvc's
 * filter-chain-wrapping setup - which assumes it owns the whole request-dispatch pipeline -
 * throws {@code IllegalStateException: The resource configuration is not modifiable in this
 * context} when it collides with that separately-initialized Jersey resource config.
 * {@code TestRestTemplate} would be the usual RANDOM_PORT tool, but empirically isn't resolvable
 * on any Spring Boot 4.0.7 test starter available here - rather than guess further at where it
 * moved to in Boot 4's split, {@code java.net.http.HttpClient} needs no extra dependency at all
 * and is just as suited to "make a real HTTP call against a real running server".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class EurekaServerApplicationTests {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void actuatorHealthIsReachableWithoutAuthentication() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl("/actuator/health"))).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void eurekaAppsRegistryRejectsUnauthenticatedRequests() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl("/eureka/apps"))).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void eurekaAppsRegistryAllowsAuthenticatedRequests() throws Exception {
        String credentials = Base64.getEncoder()
                .encodeToString("admin:dev-only-secret-change-me".getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl("/eureka/apps")))
                .header("Authorization", "Basic " + credentials)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
