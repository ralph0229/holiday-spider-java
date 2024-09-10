package holiday.factory;

import holiday.service.HttpService;
import holiday.service.impl.HutoolHttpService;

/**
 * 2024/9/10
 *
 * @author Href
 * @version 1.0.0
 */
public class HttpServiceFactory {

    /**
     * 创建httpService
     * @return HttpService
     */
    public static HttpService createHttpService() {
        // 根据配置或环境条件选择实现
        return new HutoolHttpService();
    }
}
