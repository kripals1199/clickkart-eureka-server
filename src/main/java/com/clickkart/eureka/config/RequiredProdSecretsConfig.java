// src/main/java/com/clickkart/eureka/config/RequiredProdSecretsConfig.java
package com.clickkart.eureka.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * eureka.client.service-url.defaultZone binds into a Map<String,String>, which Spring's
 * relaxed Binder does not validate for unresolved placeholders the way scalar properties
 * (e.g. spring.security.user.password) are validated - an unset EUREKA_PEER_URLS would
 * otherwise boot "successfully" with a literal, unparseable "${EUREKA_PEER_URLS}" peer URL
 * instead of failing fast, breaking prod clustering silently. This bean forces the same
 * eager resolution failure @Value already provides for scalar properties.
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
class RequiredProdSecretsConfig {

    RequiredProdSecretsConfig(@Value("${EUREKA_PEER_URLS}") String eurekaPeerUrls) {
        if (eurekaPeerUrls.isBlank()) {
            throw new IllegalStateException("EUREKA_PEER_URLS must not be blank in prod");
        }
    }
}
