package com.salesforce.androidsdk.auth;

import static java.util.Collections.singletonList;

import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class JwtAccessTokenTest {

    private static final String TEST_RAW_JWT = "eyJ0bmsiOiJjb3JlL2ZhbGNvbnRlc3QxLWNvcmU0c2RiNi8wMERTRzAwMDAwOUZZVmQyQU8iLCJ2ZXIiOiIxLjAiLCJraWQiOiJDT1JFX0FUSldULjAwRFNHMDAwMDA5RllWZC4xNzI1NTc5NDIwMTI1IiwidHR5Ijoic2ZkYy1jb3JlLXRva2VuIiwidHlwIjoiSldUIiwiYWxnIjoiUlMyNTYifQ.eyJzY3AiOiJyZWZyZXNoX3Rva2VuIHdlYiBhcGkiLCJhdWQiOlsiaHR0cHM6Ly9tb2JpbGVzZGthdHNkYjYudGVzdDEubXkucGMtcm5kLnNhbGVzZm9yY2UuY29tIl0sInN1YiI6InVpZDowMDVTRzAwMDAwOGVpQWJZQUkiLCJuYmYiOjE3MzAzODY2MjAsIm10eSI6Im9hdXRoIiwic2ZpIjoiYTZjZjk1MjY2NjYzM2Q4ZDUxMDUzNjkzNDcwZDczYzVhOTY4ZTA4NmQ1OGQ2NzlmYTVjMzY1ZmNhMGZiZjhkYyIsInJvbGVzIjpbXSwiaXNzIjoiaHR0cHM6Ly9tb2JpbGVzZGthdHNkYjYudGVzdDEubXkucGMtcm5kLnNhbGVzZm9yY2UuY29tIiwiaHNjIjpmYWxzZSwiZXhwIjoxNzMwMzg2Njk1LCJpYXQiOjE3MzAzODY2MzUsImNsaWVudF9pZCI6IjNNVkc5LkFnd3RvSXZFUlNkOGk4bGVQcnFmczdDYXpSeDJsbGJMOHViTm9HNlIzSHNZb21RRlJwYmF5YU1INEh0ekgzemowTkRFbUMwUElvaHcwUGYifQ.R8RDUDlRD-6LIzV2epi8y7m1_zWBwfvmTAhUiGOjg1fDWGxsX48hSi95WITHtZ-D-gDQEjVl1GBGKsIe7jEBdGkhoFhbUuYFEnd15bcYlmLBIpmRdSbSvImusaeGVBx2hLhv4Icl7md_BuNoiz6BpuV-T_0a0QxRkpo97sGN1MghO6m9ItzXY9ldR7m5_pOORy3eZ1q4JZ1aj49pphom_O_ZQAeWYX7Gp9dZjhxlLFYgk0XrarC689LOhfSAyBhJO-OvtgKrvUY1XiWEaZR3A2FAk-AK1ZrNenKB_76JGEppuODCpRyqiUUlLmFkzcx897KeTQGoC_QDrdn0y4speA";

    @Test
    public void testDecodeValidJwtAndParseHeader() {
        JwtAccessToken decodedJwt = new JwtAccessToken(TEST_RAW_JWT);
        Assert.assertNotNull(decodedJwt);
        JwtHeader jwtHeader = decodedJwt.getHeader();
        Assert.assertNotNull(jwtHeader);

        Assert.assertEquals("RS256", jwtHeader.getAlgorithn());
        Assert.assertEquals("JWT", jwtHeader.getType());
        Assert.assertEquals("CORE_ATJWT.00DSG000009FYVd.1725579420125", jwtHeader.getKeyId());
        Assert.assertEquals("sfdc-core-token", jwtHeader.getTokenType());
        Assert.assertEquals("core/falcontest1-core4sdb6/00DSG000009FYVd2AO", jwtHeader.getTenantKey());
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
        Assert.assertEquals("uid:005SG000008eiAbYAI", jwtPayload.getSubject());
        Assert.assertEquals("refresh_token web api", jwtPayload.getScopes());
        Assert.assertEquals("3MVG9.AgwtoIvERSd8i8lePrqfs7CazRx2llbL8ubNoG6R3HsYomQFRpbayaMH4HtzH3zj0NDEmC0PIohw0Pf", jwtPayload.getClientId());
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
