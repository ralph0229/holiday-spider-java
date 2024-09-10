package holiday.service.impl;

import holiday.service.HttpService;
import lombok.extern.java.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 2024/9/10
 *
 * @author Href
 * @version 1.0.0
 */
@Log
public class UrlConnectionHttpService implements HttpService {

    // 定义最大重定向次数防止无限重定向
    private static final int MAX_REDIRECTS = 5;


    /**
     * Visit URL and retrieve the response body
     *
     * @param targetUrl The URL to be accessed
     * @param params Parameters to be appended to the URL
     * @return Response body as string
     */
    @Override
    public String getBody(String targetUrl, Map<String, Object> params) {
        String urlWithParams = generateUrlParam(targetUrl, params);
        try {
            return followRedirects(urlWithParams, 0);
        } catch (IOException e) {
            log.severe("Error following redirects: " + e.getMessage());
        }
        return "";
    }

    private String followRedirects(String url, int redirectCount) throws IOException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IOException("Too many redirects");
        }

        URL obj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
        // 禁用自动重定向
        conn.setInstanceFollowRedirects(false);
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            String newUrl = conn.getHeaderField("Location");
            log.info("Redirecting to URL: " + newUrl);
            return followRedirects(newUrl, redirectCount + 1);
        } else if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return in.lines().collect(Collectors.joining());
            }
        } else {
            log.warning("GET request not worked, response code: " + responseCode);
        }
        return "";
    }

    /**
     * Generate a URL with appended parameters
     *
     * @param url Base URL
     * @param params Map of query parameters
     * @return URL string with appended query parameters
     */
    private String generateUrlParam(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        if (!url.contains("?")) {
            sb.append("?");
        } else {
            sb.append("&");
        }
        params.forEach((key, value) -> {
            try {
                String encodedKey = java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8.toString());
                String encodedValue = java.net.URLEncoder.encode(String.valueOf(value), java.nio.charset.StandardCharsets.UTF_8.toString());
                sb.append(encodedKey).append("=").append(encodedValue).append("&");
            } catch (UnsupportedEncodingException e) {
                log.severe("Error encoding parameter: " + e.getMessage());
            }
        });
        // remove the last '&'
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}
