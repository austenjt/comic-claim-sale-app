package org.example.functions.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.model.UserIdentity;

import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Validates Microsoft Entra External ID (CIAM) JWTs using Nimbus JOSE+JWT.
 *
 * Fetches the JWKS from the Entra CIAM well-known endpoint and caches keys
 * in memory. Nimbus handles TTL and key refresh internally.
 *
 * Reads env vars: MSAL_TENANT_SUBDOMAIN, MSAL_TENANT_ID, MSAL_CLIENT_ID
 */
@Slf4j
public class EntraJwtValidator {

    private static EntraJwtValidator INSTANCE;

    private final String tenantId;
    private final String clientId;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public static EntraJwtValidator getInstance() {
        if (Objects.isNull(INSTANCE)) {
            String subdomain = System.getenv("MSAL_TENANT_SUBDOMAIN");
            String tenantId  = System.getenv("MSAL_TENANT_ID");
            String clientId  = System.getenv("MSAL_CLIENT_ID");
            INSTANCE = new EntraJwtValidator(subdomain, tenantId, clientId);
        }
        return INSTANCE;
    }

    public EntraJwtValidator(String tenantSubdomain, String tenantId, String clientId) {
        requireNonBlank(tenantSubdomain, "MSAL_TENANT_SUBDOMAIN");
        requireNonBlank(tenantId,        "MSAL_TENANT_ID");
        requireNonBlank(clientId,        "MSAL_CLIENT_ID");

        this.tenantId = tenantId;
        this.clientId = clientId;

        // Entra CIAM JWKS URI — includes /discovery/ in the path (confirmed from OIDC metadata)
        String jwksUri = "https://" + tenantId + ".ciamlogin.com/" + tenantId + "/discovery/v2.0/keys";
        this.jwtProcessor = buildProcessor(jwksUri);
    }

    /**
     * Validates the raw Bearer token string.
     * Returns a UserIdentity on success, or throws RuntimeException on failure.
     */
    public UserIdentity validate(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new RuntimeException("No Bearer token provided");
        }
        try {
            JWTClaimsSet claims = jwtProcessor.process(bearerToken, null);

            String oid   = claims.getStringClaim("oid");
            // Entra External ID CIAM access tokens put the email in preferred_username, not email
            String email = claims.getStringClaim("email");
            if (email == null || email.isBlank()) {
                email = claims.getStringClaim("preferred_username");
            }
            String name  = claims.getStringClaim("name");
            String tid   = claims.getStringClaim("tid");

            if (oid == null || oid.isBlank()) {
                throw new RuntimeException("JWT is missing required 'oid' claim");
            }
            if (email == null || email.isBlank()) {
                throw new RuntimeException("JWT is missing required 'email' or 'preferred_username' claim");
            }
            if (!tenantId.equals(tid)) {
                throw new RuntimeException("JWT 'tid' claim does not match expected tenant");
            }

            return new UserIdentity(oid, email, name, tid);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            throw new RuntimeException("JWT validation failed: " + e.getMessage(), e);
        }
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(String jwksUri) {
        try {
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwksUri));

            JWSVerificationKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();

            // Accept tokens where typ is "JWT", "at+JWT", or absent — Entra omits typ on some tokens
            Set<JOSEObjectType> allowedTypes = new HashSet<>();
            allowedTypes.add(null);
            allowedTypes.add(new JOSEObjectType("JWT"));
            allowedTypes.add(new JOSEObjectType("at+JWT"));
            processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(allowedTypes));
            processor.setJWSKeySelector(keySelector);

            // Entra CIAM issues tokens with the tenant GUID as subdomain in the issuer:
            // https://{tenantId}.ciamlogin.com/{tenantId}/v2.0
            String issuer = "https://" + tenantId + ".ciamlogin.com/" + tenantId + "/v2.0";
            processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    clientId,
                    new JWTClaimsSet.Builder().issuer(issuer).build(),
                    Set.of("iss", "aud", "exp", "oid")
            ));

            return processor;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise Entra JWT processor for JWKS URI: " + jwksUri, e);
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " env var must not be null or blank");
        }
    }
}
