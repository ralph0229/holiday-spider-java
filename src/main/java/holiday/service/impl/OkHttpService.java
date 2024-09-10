package holiday.service.impl;

import holiday.service.HttpService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

/**
 * 2024/9/10
 *
 * @author Href
 * @version 1.0.0
 */
public class OkHttpService implements HttpService {

    /**
     * 访问Url并获取body
     *
     * @param targetUrl 目标url
     * @param params 参数
     * @return body
     */
    @Override
    public String getBody(String targetUrl, Map<String, Object> params) {
        String url = generateUrlParam(targetUrl, params);
        Request request = new Request.Builder()
                .get()
                .url(url)
                .build();
        try (Response response = new OkHttpClient().newCall(request).execute()){
            String result = null;
            if (response.body() != null) {
                result = response.body().string();
            }
            return result;
        } catch (IOException ignored) {}
        return "";
    }

    /**
     * 生成带参数的url
     *
     * @param url url
     * @param params 参数
     * @return 新url
     */
    private String generateUrlParam(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(url);
        if (url.contains("?")) {
            sb.append("&");
        } else {
            sb.append("?");
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }
}
