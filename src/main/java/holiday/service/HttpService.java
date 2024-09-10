package holiday.service;

import java.util.Map;

/**
 * 2024/9/10
 * @author Href
 * @version 1.0.0
 */
public interface HttpService {

    /**
     * 访问Url并获取body
     *
     * @param targetUrl 目标url
     * @param params 参数
     * @return body
     */
    String getBody(String targetUrl, Map<String, Object> params);

}
