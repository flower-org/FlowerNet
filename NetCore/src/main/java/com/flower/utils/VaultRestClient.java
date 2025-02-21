package com.flower.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONObject;

public class VaultRestClient {
    private static final String KUBERNETES_JWT_TOKEN_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String VAULT_URL = "http://vault.vault.svc.cluster.local";
    private static final int VAULT_PORT = 8200;

    public static String kubernetesAuth()
            throws IOException, InterruptedException {
        String jwt = Files.readString(Paths.get(KUBERNETES_JWT_TOKEN_FILE));
        return kubernetesAuth(VAULT_URL, VAULT_PORT, jwt);
    }

    public static String kubernetesAuth(String vaultUrl, int vaultPort, String jwt)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        String jsonPayload = "{\"jwt\": \"" + jwt + "\", \"role\": \"vault-role\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vaultUrl + ":" + vaultPort + "/v1/auth/kubernetes/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HttpResponse<String> authResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

        String authResponseStr = authResponse.body();
        JSONObject responseObject = new JSONObject(authResponseStr);
        if (responseObject.has("errors")) {
            throw new RuntimeException("Authentication error: " + authResponseStr);
        } else if (responseObject.has("auth")) {
            JSONObject authObject = responseObject.getJSONObject("auth");
            return authObject.getString("client_token");
        } else {
            throw new RuntimeException("Authentication error: unknown response format " + authResponseStr);
        }
    }

    public static String createWrappedToken(String vaultAuthToken, String jsonPayload)
            throws IOException, InterruptedException {
        return VaultRestClient.createWrappedToken(VAULT_URL, VAULT_PORT, vaultAuthToken, jsonPayload);
    }

    public static String createWrappedToken(String vaultUrl, int vaultPort, String vaultAuthToken,
                                            String jsonPayload) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vaultUrl + ":" + vaultPort + "/v1/sys/wrapping/wrap"))
                .header("X-Vault-Token", vaultAuthToken)
                .header("X-Vault-Wrap-TTL", "900")//15 minutes
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HttpResponse<String> tokenResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

        String tokenResponseStr = tokenResponse.body();
        JSONObject responseObject = new JSONObject(tokenResponseStr);
        if (responseObject.has("errors")) {
            throw new RuntimeException("Wrapped token creation error: " + tokenResponseStr);
        } else if (responseObject.has("wrap_info")) {
            JSONObject authObject = responseObject.getJSONObject("wrap_info");
            return authObject.getString("token");
        } else {
            throw new RuntimeException("Wrapped token creation error: unknown response format " + tokenResponseStr);
        }
    }
}
