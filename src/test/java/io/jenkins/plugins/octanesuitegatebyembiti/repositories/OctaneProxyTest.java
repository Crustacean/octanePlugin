package io.jenkins.plugins.octanesuitegatebyembiti.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ProxyConfiguration;
import hudson.util.Secret;
import io.jenkins.plugins.octanesuitegatebyembiti.security.OctaneTestHttpsServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class OctaneProxyTest {
  @Test
  void httpsUsesJenkinsProxyAndNewConnectionsRespectUpdatedBypass(JenkinsRule jenkins)
      throws Exception {
    var proxy = new ServerSocket();
    proxy.bind(new InetSocketAddress("127.0.0.1", 0));
    proxy.setSoTimeout(10000);
    var executor = Executors.newSingleThreadExecutor();
    var origin = OctaneTestHttpsServer.create();
    AtomicReference<String> originBody = new AtomicReference<>();
    // HttpServer routes paths, not CONNECT's authority-form target. Use a tiny tunnel stub.
    var tunnel =
        executor.submit(
            () -> {
              try (var socket = proxy.accept()) {
                socket.setSoTimeout(5000);
                var reader =
                    new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                StringBuilder headers = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                  headers.append(line).append('\n');
                }
                socket
                    .getOutputStream()
                    .write(
                        "HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                return headers.toString();
              }
            });
    origin.createContext(
        "/authentication/sign_in",
        exchange -> {
          originBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          try (var output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    origin.start();
    try {
      int proxyPort = proxy.getLocalPort();
      jenkins.jenkins.proxy = new ProxyConfiguration("127.0.0.1", proxyPort);
      String url = "https://127.0.0.1:" + origin.getAddress().getPort();
      try (var client = OctaneClient.withCredentials(url, "client", Secret.fromString("secret"))) {
        assertThrows(IOException.class, () -> client.authenticate());
        String headers = tunnel.get(10, TimeUnit.SECONDS);
        assertTrue(headers.startsWith("CONNECT 127.0.0.1:" + origin.getAddress().getPort()));
        assertFalse(headers.contains("client_secret"));
        assertFalse(headers.contains("Authorization:"));
        HttpClient transport = transport(client);
        assertEquals(HttpClient.Redirect.NEVER, transport.followRedirects());
        assertEquals(Duration.ofSeconds(30), transport.connectTimeout().orElseThrow());
      }
      jenkins.jenkins.proxy.setNoProxyHost("127.0.0.1|*.internal.example");
      jenkins.jenkins.proxy.setUserName("proxy-user");
      jenkins.jenkins.proxy.setSecretPassword(Secret.fromString("proxy-secret"));
      // A closed proxy proves a successful next request really bypasses it.
      proxy.close();
      try (var client = new OctaneClient(url, "client", Secret.fromString("secret"))) {
        HttpClient transport = transport(client);
        var selector = transport.proxy().orElseThrow();
        assertEquals(List.of(Proxy.NO_PROXY), selector.select(URI.create(url)));
        assertEquals(
            List.of(Proxy.NO_PROXY),
            selector.select(URI.create("https://octane.internal.example")));
        assertEquals(
            Proxy.Type.HTTP, selector.select(URI.create("https://external.example")).get(0).type());
        var authenticator = transport.authenticator().orElseThrow();
        var auth =
            authenticator.requestPasswordAuthenticationInstance(
                "127.0.0.1",
                null,
                proxyPort,
                "https",
                "proxy",
                "basic",
                null,
                Authenticator.RequestorType.PROXY);
        assertNotNull(auth);
        assertEquals("proxy-user", auth.getUserName());
        assertEquals("proxy-secret", new String(auth.getPassword()));
        client.authenticate();
        assertTrue(originBody.get().contains("\"client_id\":\"client\""));
      }
    } finally {
      jenkins.jenkins.proxy = null;
      proxy.close();
      executor.shutdownNow();
      origin.stop(0);
    }
  }

  private HttpClient transport(OctaneClient client) throws Exception {
    var field = OctaneClient.class.getDeclaredField("httpClient");
    field.setAccessible(true);
    return (HttpClient) field.get(client);
  }
}
