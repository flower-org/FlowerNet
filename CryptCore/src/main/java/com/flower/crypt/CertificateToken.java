package com.flower.crypt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.commons.lang3.StringUtils;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

@Value.Immutable
@JsonSerialize(as = ImmutableCertificateToken.class)
@JsonDeserialize(as = ImmutableCertificateToken.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface CertificateToken {
    @Value.Immutable
    @JsonSerialize(as = ImmutableServerData.class)
    @JsonDeserialize(as = ImmutableServerData.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    interface ServerData {
        @JsonProperty
        String cert();
        @JsonProperty
        String pod();

        static ServerData create(X509Certificate certificate, String pod) throws CertificateEncodingException {
            return ImmutableServerData.builder().cert(PkiUtil.getCertificateAsPem(certificate)).pod(pod).build();
        }
    }
    @JsonProperty
    ServerData serverData();
    @JsonProperty
    String signature();

    static CertificateToken createToken(X509Certificate certificate, PrivateKey privateKey, @Nullable String pod)
            throws JsonProcessingException, CertificateEncodingException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        ObjectMapper mapper = new ObjectMapper();
        ServerData serverData = ServerData.create(certificate, pod == null || StringUtils.isBlank(pod) ? "POD_NAME_UNKNOWN" : pod);
        String serverDataStr = mapper.writeValueAsString(serverData);

        String sign = PkiUtil.signData(serverDataStr, privateKey);
        return ImmutableCertificateToken.builder().serverData(serverData).signature(sign).build();
    }

    static String createTokenString(CertificateToken certificateToken)
            throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(certificateToken);
    }

    static boolean verifyToken(String tokenStr, boolean showCert)
            throws JsonProcessingException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        ObjectMapper mapper = new ObjectMapper();
        CertificateToken certificateToken = mapper.readValue(tokenStr, CertificateToken.class);
        ServerData serverData = certificateToken.serverData();
        String serverDataStr = mapper.writeValueAsString(serverData);

        X509Certificate cert = PkiUtil.getCertificateFromString(serverData.cert());
        if (showCert) {
            System.out.println(serverData.cert());
        }

        return PkiUtil.verifySignature(serverDataStr, certificateToken.signature(), cert.getPublicKey());
    }

    static void main(String[] args) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, JsonProcessingException {
        String token = """
                {"serverData":{"cert":"-----BEGIN CERTIFICATE-----\\nMIIC0jCCAbqgAwIBAgIUIry3m6S+nq8GA+WuKjIOq5yWDngwDQYJKoZIhvcNAQELBQAwIzEhMB8G\\r\\nA1UEAxMYU29ja3MgU2VydmVyIENlcnRpZmljYXRlMB4XDTI1MDIyMTA4MDkxMloXDTI2MDIyMTA4\\r\\nMDkxMlowIzEhMB8GA1UEAxMYU29ja3MgU2VydmVyIENlcnRpZmljYXRlMIIBIjANBgkqhkiG9w0B\\r\\nAQEFAAOCAQ8AMIIBCgKCAQEAs5LGJUOV6U2GvgAHAD5xR1X4obAAS8tWOPGQyS9gJnBfQ6CMv/Wt\\r\\ntLy0g+7WG8EkXF302n8VXmVSbk2T+KZVl/za3bzhbdtyDhzPp2dmVhZG2N/Y4fSVFj+viWpL9qzE\\r\\n7tXopg5lMF21YeJO01uiCBWn8Sxge5gKXJDiuj2FRymekmsWu7paT5vjz+jhByP0dhhw8hMfpY3w\\r\\nGqXoc6aBotnOftE51k92UrpYCl5kL+k7vMzRMW8um9b6qxoKmWzfJELxO5LAkKUAEeaZtXyFuGti\\r\\ndolZPmfB2ZXo8nTaXLZj9Y6TnJ+GRn+nnb/MFSjCZAS+zoL4jSaVvajxdfa8UwIDAQABMA0GCSqG\\r\\nSIb3DQEBCwUAA4IBAQB0uqVAhcT6colNumtq3M4SSsw+45845CqW6gqni4TJTy1ZNZVGp6yOueee\\r\\nAxr4CiG7Q7mLRAzI9sQXi+Hkt3oXCcMp/MkJhJCRvSy+DJMJ171Ct9I6HdUgqUUUnTa1QRqnN39V\\r\\njlm9O0tHr1r+/+WVFhYVU2nxM6KljEYVPXJaBfdxquNxqpO3AGmbofIjdKfy+2plAG5pexSQHE1r\\r\\n6kmLKzzTfpbPNoSQX+d4XNIzJZCa27qqnukuUY1uz1K8ntFVhIWHMr3tEaGtXjj0/i/cbOFTuhPk\\r\\nLM8YgiEfN2b1Pa2+v/3CXvI7SJSRi6FI+ZRGcjQCwflLwfPvuqXrt6CU\\n-----END CERTIFICATE-----\\n","pod":"pod1"},"signature":"EINhIztdMb3K3l1OtiJZ/qxW8q0/RTQejvOA7ydXyBORqSgc5SMFNm8/ZsIppdXRhLQHM2t1hvlfrVq1nK5o1A88i/Tg/oq1zBJws2u//eOBR4iXhYPpkiGMJHVXAh0LTH60W/K9haTeWSnGbszOeJKJL3XJLV5cY4eJxPrDi6YkTmPLtBFApBBIx8jrn5SCJzO8p2AuE2Cgp0XkGZiEE+NqWHeihgwNnm7hJ6M6rCnttrIXiayIecMO6EdahDDS4lmPxBHmZO5iHlYrCrqjBPuAGqc4pwM/2FydldzADAHFtqGtEeKcW94k6jFOSblEjljbrwwq+ifnCd7pg4xUeQ=="}
        """;
        System.out.println(verifyToken(token, true) ? "Verified" : "Failed to verify");
    }
}
