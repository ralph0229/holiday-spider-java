package holiday.service.impl;

import cn.hutool.http.HttpUtil;
import holiday.service.HttpService;

import java.util.Map;

/**
 * 2024/9/10
 *
 * @author Href
 * @version 1.0.0
 */
public class HutoolHttpService implements HttpService {

    /**
     * 访问Url并获取body
     *
     * @param targetUrl 目标url
     * @param params 参数
     * @return body
     */
    @Override
    public String getBody(String targetUrl, Map<String, Object> params) {
        return HttpUtil.createGet(targetUrl, true)
                .form(params)
                .execute()
                .body();
    }

}
