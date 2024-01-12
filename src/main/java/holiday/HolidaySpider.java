package holiday;

import com.alibaba.fastjson2.JSON;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class HolidaySpider {

    /**
     * 政策搜索url
     */
    private static final String SEARCH_URL = "http://sousuo.gov.cn/s.htm";

    /**
     * 排除的文件url
     */
    private static final List<String> PAPER_EXCLUDE = Arrays.asList(
            "http://www.gov.cn/zhengce/content/2014-09/29/content_9102.htm",
            "http://www.gov.cn/zhengce/content/2015-02/09/content_9466.htm"
    );

    /**
     * 规则不包含的放假政策文件URL
     */
    private static final Map<Integer, List<String>> PAPER_INCLUDE = new HashMap<>();

    /**
     * 预解析的政策文件
     */
    private static final Map<String, List<Map<String, Object>>> PRE_PARSED_PAPERS = new HashMap<>();

    static {
        PAPER_INCLUDE.put(2015, Arrays.asList(
                "http://www.gov.cn/zhengce/content/2015-05/13/content_9742.htm"
        ));

        List<Map<String, Object>> paper20150513 = new ArrayList<>();
        paper20150513.add(createDay("抗日战争暨世界反法西斯战争胜利70周年纪念日", LocalDate.of(2015, 9, 3), true));
        paper20150513.add(createDay("抗日战争暨世界反法西斯战争胜利70周年纪念日", LocalDate.of(2015, 9, 4), true));
        paper20150513.add(createDay("抗日战争暨世界反法西斯战争胜利70周年纪念日", LocalDate.of(2015, 9, 5), true));
        paper20150513.add(createDay("抗日战争暨世界反法西斯战争胜利70周年纪念日", LocalDate.of(2015, 9, 6), false));
        PRE_PARSED_PAPERS.put("http://www.gov.cn/zhengce/content/2015-05/13/content_9742.htm", paper20150513);

        List<Map<String, Object>> paper20200127 = new ArrayList<>();
        paper20200127.add(createDay("春节", LocalDate.of(2020, 1, 31), true));
        paper20200127.add(createDay("春节", LocalDate.of(2020, 2, 1), true));
        paper20200127.add(createDay("春节", LocalDate.of(2020, 2, 2), true));
        paper20200127.add(createDay("春节", LocalDate.of(2020, 2, 3), false));
        PRE_PARSED_PAPERS.put("http://www.gov.cn/zhengce/content/2020-01/27/content_5472352.htm", paper20200127);
    }

    /**
     * 创建dayMap
     *
     * @param name      节日名称
     * @param date      日期
     * @param isOffDay  是否为休息日
     * @return          dayMap
     */
    private static Map<String, Object> createDay(String name, LocalDate date, boolean isOffDay) {
        Map<String, Object> holiday = new HashMap<>();
        holiday.put("name", name);
        holiday.put("date", date);
        holiday.put("isOffDay", isOffDay);
        return holiday;
    }

    /**
     * 获取政策文件详情页Urls
     *
     * @param year 年份
     * @return 详情页Urls
     */
    private static List<String> getPaperUrls(int year) {
        HashMap<String, Object> params = new HashMap<String, Object>() {{
            put("t", "paper");
            put("advance", "true");
            put("title", year);
            put("q", "假期");
            put("pcodeJiguan", "国办发明电");
            put("timetype", "puborg");
        }};
        String body = getBody(SEARCH_URL, params);
        Pattern p = Pattern.compile("<li class=\"res-list\".*?><a href=\"(.+?)\".*?></li>", Pattern.DOTALL);
        Matcher m = p.matcher(body);
        List<String> ret = new ArrayList<>();
        while (m.find()) {
            if(!PAPER_EXCLUDE.contains(m.group(1))) {
                ret.add(m.group(1));
            }
        }
        ret.addAll(PAPER_INCLUDE.getOrDefault(year, Collections.emptyList()));

        if (ret.isEmpty() && LocalDate.now().getYear() >= year) {
            throw new RuntimeException("无法获取" + year + "年的假期安排");
        }

        return ret;
    }

    /**
     * 解析政策文件
     *
     * @param year 年份
     * @param url 详情页url
     * @return 假期安排
     */
    private static List<Map<String, Object>> parsePaper(int year, String url) {
        if (PRE_PARSED_PAPERS.containsKey(url)) {
            return PRE_PARSED_PAPERS.get(url);
        }
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(getRules(getPaper(url)), 0), false)
                .map(rule -> new DescriptionParser(rule.v1(), rule.v2(), year).parse())
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 获取政策文件主体
     *
     * @param url 详情页url
     * @return 政策文件主体
     */
    private static String getPaper(String url) {
        String body = getBody(url, null);
        Document doc = Jsoup.parse(body);
        Element container = doc.getElementById("UCAP-CONTENT");
        assert container != null : "无法从url获取政策文件主体： " + url;

        StringBuilder content = new StringBuilder();
        for (Element p : container.getElementsByTag("p")) {
            content.append(p.text()).append("\n");
        }
        assert content.length() > 0 : "无法从url获取政策文件内容: " + url;
        return content.toString();
    }

    /**
     * 获取政策文件的普通规则
     *
     * @param lines 通知的关键行
     * @return 普通规则行<节日,描述>
     */
    public static Iterator<Tuple<String, String>> getNormalRules(Iterator<String> lines) {
        List<Tuple<String, String>> rules = new ArrayList<>();
        while (lines.hasNext()) {
            String line = lines.next();
            Matcher matcher = Pattern.compile("[一二三四五六七八九十]、(.+?)[：:](.+)").matcher(line);
            if (matcher.find()) {
                String name = matcher.group(1);
                String desc = matcher.group(2);
                rules.add(new Tuple<>(name, desc));
            }
        }
        return rules.iterator();
    }

    /**
     * 获取政策文件的补充规则
     *
     * @param lines 通知的关键行
     * @return 补充规则行<节日,描述>
     */
    public static Iterator<Tuple<String, String>> getPatchRules(Iterator<String> lines) {
        String name = null;
        List<Tuple<String, String>> rules = new ArrayList<>();
        while (lines.hasNext()) {
            String line = lines.next();
            Matcher matcher = Pattern.compile(".*\\d+年([^和、]{2,})(?:假期|放假).*安排").matcher(line);
            if (matcher.find()) {
                name = matcher.group(1);
            }
            if (name == null) {
                continue;
            }

            matcher = Pattern.compile("^[一二三四五六七八九十]、(.+)$").matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String desc = matcher.group(1);
            if (Pattern.matches(".*\\d+月\\d+日.*", desc)) {
                rules.add(new Tuple<>(name, desc));
            }
        }
        return rules.iterator();
    }

    /**
     * 获取政策文件的规则
     *
     * @param paper 政策文件主体
     * @return 规则行<节日,描述>
     */
    public static Iterator<Tuple<String, String>> getRules(String paper) {
        List<String> lines = Arrays.asList(paper.split("[\r\n]"));
        List<String> finalLines = lines;
        lines = new ArrayList<>(new HashSet<>(lines));
        lines.sort(Comparator.comparingInt(finalLines::indexOf));

        Iterator<Tuple<String, String>> rules = concatIterator(getNormalRules(lines.iterator()), getPatchRules(lines.iterator()));

        if (!rules.hasNext()) {
            throw new RuntimeException("文件不包含任何规则");
        }

        return rules;
    }

    /**
     * 获取假期安排JSON
     *
     * @param year 年份
     * @return 假期安排JSON
     */
    public static String fetchHoliday(int year) {
        List<String> paperUrls = getPaperUrls(year);
        HashMap<String, Object> result = new HashMap<>();
        result.put("year", year);
        result.put("papers", paperUrls);
        result.put("days", paperUrls.stream()
                .map(paperUrl -> parsePaper(year, paperUrl))
                .flatMap(List::stream)
                .collect(Collectors.toList()));
        return JSON.toJSONString(result);
    }

    /**
     * 访问Url并获取body
     *
     * @param targetUrl 目标url
     * @param params 参数
     * @return body
     */
    public static String getBody(String targetUrl, Map<String, Object> params) {
        String url = generateUrlParam(targetUrl, params);
        Request request = new Request.Builder()
                .get()
                .url(url)
                .build();
        try {
            Response response = new OkHttpClient().newCall(request).execute();
            String result = null;
            if (response.body() != null) {
                result = response.body().string();
            }
            return result;
        } catch (IOException ignored) {
        }
        return "";
    }

    /**
     * 生成带参数的url
     *
     * @param url url
     * @param params 参数
     * @return 新url
     */
    private static String generateUrlParam(String url, Map<String, Object> params) {
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

    private static Iterator<Tuple<String, String>> concatIterator(
            Iterator<Tuple<String, String>> iterator1,
            Iterator<Tuple<String, String>> iterator2) {
        return Stream.concat(
                        StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator1, 0), false),
                        StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator2, 0), false))
                .collect(Collectors.collectingAndThen(Collectors.toList(), List::iterator));
    }

    public static void main(String[] args){
        for (int i = 2007; i < 2024; i++) {
            System.out.println(fetchHoliday(i));
        }
    }
}
