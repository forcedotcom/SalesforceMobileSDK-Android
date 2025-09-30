package com.salesforce.androidsdk.auth;

import static java.util.Collections.singletonList;

import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JwtAccessTokenTest {

    private static final String HEADER = "{\"tnk\":\"some-tnk\",\"ver\":\"1.0\",\"kid\":\"some-kid\",\"tty\":\"sfdc-core-token\",\"typ\":\"JWT\",\"alg\":\"RS256\"}";
    private static final String PAYLOAD = "{\"scp\":\"refresh_token web api\",\"aud\":[\"https://mobilesdkatsdb6.test1.my.pc-rnd.salesforce.com\"],\"sub\":\"uid:some-uid\",\"nbf\":1730386620,\"mty\":\"oauth\",\"sfi\":\"some-sfi\",\"roles\":[],\"iss\":\"https://mobilesdkatsdb6.test1.my.pc-rnd.salesforce.com\",\"hsc\":false,\"exp\":1730386695,\"iat\":1730386635,\"client_id\":\"some-client-id\"}";
    private static final String SIGNATURE = "FAKE_SIGNATURE";
    private static final String TEST_RAW_JWT = Stream.of(HEADER, PAYLOAD, SIGNATURE)
            .map(s -> Base64.getEncoder().encodeToString(s.getBytes()))
            .collect(Collectors.joining("."));

    @Test
    public void testDecodeValidJwtAndParseHeader() {
        JwtAccessToken decodedJwt = new JwtAccessToken(TEST_RAW_JWT);
        Assert.assertNotNull(decodedJwt);
        JwtHeader jwtHeader = decodedJwt.getHeader();
        Assert.assertNotNull(jwtHeader);

        Assert.assertEquals("RS256", jwtHeader.getAlgorithn());
        Assert.assertEquals("JWT", jwtHeader.getType());
        Assert.assertEquals("some-kid", jwtHeader.getKeyId());
        Assert.assertEquals("sfdc-core-token", jwtHeader.getTokenType());
        Assert.assertEquals("some-tnk" , jwtHeader.getTenantKey());
        Assert.assertEquals("1.0", decodedJwt.getHeader().getVersion());
    }

    @Test
    public void testDecodeValidJwtAndParsePayload() {
        JwtAccessToken decodedJwt = new JwtAccessToken(TEST_RAW_JWT);
        Assert.assertNotNull(decodedJwt);
        JwtPayload jwtPayload = decodedJwt.getPayload();
        Assert.assertNotNull(jwtPayload);

        Assert.assertEquals(singletonList("https://mobilesdkatsdb6.test1.my.pc-rnd.salesforce.com"), jwtPayload.getAudience());
        Assert.assertEquals(1730386695, (int) jwtPayload.getExpirationTime());
        Assert.assertEquals("https://mobilesdkatsdb6.test1.my.pc-rnd.salesforce.com", jwtPayload.getIssuer());
        Assert.assertEquals(1730386620, (int) jwtPayload.getNotBeforeTime());
        Assert.assertEquals("uid:some-uid", jwtPayload.getSubject());
        Assert.assertEquals("refresh_token web api", jwtPayload.getScopes());
        Assert.assertEquals("some-client-id", jwtPayload.getClientId());
    }

    @Test
    public void testExpirationDate() {
        JwtAccessToken decodedJwt = new JwtAccessToken(TEST_RAW_JWT);
        Assert.assertNotNull(decodedJwt);
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Assert.assertEquals("2024-10-31 07:58:15", dateFormatter.format(decodedJwt.expirationDate()));
    }


    @Test
    public void testInvalidJwt() {
        String invalidRawJwt = "invalid-jwt-string";

        try {
            new JwtAccessToken(invalidRawJwt);
            Assert.fail("Expected illegal argument exception");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Wrong exception thrown", "Invalid JWT format", e.getMessage());
        }
    }
}
