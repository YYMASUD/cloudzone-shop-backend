package com.cloudzone.shopbackend.common.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

/**
 * Reads RSA public/private keys from PKCS#8 / X.509 PEM files.
 *
 * <p>Locations accept anything Spring's {@link ResourceLoader} understands —
 * {@code classpath:keys/private.pem}, {@code file:/run/secrets/private.pem}, …
 *
 * <p>Only the standard JCA {@link KeyFactory} is used: no third-party PEM parser, so the
 * behaviour is identical whether the key comes from the classpath in dev or a mounted
 * secret in production.
 */
@Component
public class RsaPemReader {

    private static final String BEGIN = "-----BEGIN";
    private static final String END = "-----END";

    private final ResourceLoader resourceLoader;

    public RsaPemReader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /** Loads and parses an X.509-encoded RSA public key. */
    public RSAPublicKey readPublicKey(String location) {
        byte[] encoded = decode(readPem(location));
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Invalid X.509 RSA public key at " + location, e);
        }
    }

    /**
     * Loads and parses a PKCS#8-encoded RSA private key — the format
     * {@code openssl genrsa -out private.pem 2048} writes.
     */
    public RSAPrivateKey readPrivateKey(String location) {
        byte[] encoded = decode(readPem(location));
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "Invalid PKCS#8 RSA private key at " + location
                            + " (PKCS#1 keys must be converted with: openssl pkcs8 -topk8 -nocrypt)", e);
        }
    }

    private String readPem(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return new String(FileCopyUtils.copyToByteArray(in), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read key at " + location, e);
        }
    }

    /** Strips the BEGIN/END armor and base64-decodes the body. */
    private byte[] decode(String pem) {
        StringBuilder base64 = new StringBuilder(pem.length());
        for (String line : pem.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(BEGIN) || trimmed.startsWith(END)) {
                continue;
            }
            base64.append(trimmed);
        }
        return Base64.getDecoder().decode(base64.toString());
    }
}
