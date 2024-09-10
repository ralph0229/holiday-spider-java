package holiday;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import holiday.factory.HttpServiceFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static holiday.util.CommonUtil.concatIterator;

/**
 * 2024/9/10
 *
 * @author Href
 * @version 1.0.0
 */
public class HolidaySpider {

    /**
     * 政策搜索url
     */
    private static final String SEARCH_URL = "https://sousuo.www.gov.cn/search-gov/data";

    /**
     * 排除的文件url
     */
    private static final List<String> PAPER_EXCLUDE = Arrays.asList(
            "http://www.gov.cn/zhengce/zhengceku/2014-09/29/content_9102.htm",
            "http://www.gov.cn/zhengce/zhengceku/2015-02/09/content_9466.htm"
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
        PAPER_INCLUDE.put(2015,
                Collections.singletonList("http://www.gov.cn/zhengce/zhengceku/2015-05/13/content_9742.htm"));

        List<Map<String, Object>> paper20150513 = new ArrayList<>();
        paper20150513.add(createDay("抗日战争暨世界反法西斯战争胜利70周年纪念日",
                LocalDate.of(2015, 9, 3), true));
        paper20150513.add(createDay("抗日战争暨世界反法西斯战争胜利70周年纪念日",
                LocalDate.of(2015, 9, 4), true));
        paper20150513.add(createDay("抗日战争暨世界反法西斯战争胜利70周年纪念日",
                LocalDate.of(2015, 9, 5), true));
        paper20150513.add(createDay("抗日战争暨世界反法西斯战争胜利70周年纪念日",
                LocalDate.of(2015, 9, 6), false));
        PRE_PARSED_PAPERS.put("http://www.gov.cn/zhengce/zhengceku/2015-05/13/content_9742.htm", paper20150513);

        List<Map<String, Object>> paper20200127 = new ArrayList<>();
        paper20200127.add(createDay("春节",
                LocalDate.of(2020, 1, 31), true));
        paper20200127.add(createDay("春节",
                LocalDate.of(2020, 2, 1), true));
        paper20200127.add(createDay("春节",
                LocalDate.of(2020, 2, 2), true));
        paper20200127.add(createDay("春节",
                LocalDate.of(2020, 2, 3), false));
        PRE_PARSED_PAPERS.put("http://www.gov.cn/zhengce/zhengceku/2020-01/27/content_5472352.htm", paper20200127);
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
            put("t", "zhengcelibrary_gw");
            put("n", 5);
            put("q", "假期 " + year);
            put("pcodeJiguan", "国办发明电");
            put("puborg", "国务院办公厅");
            put("filetype", "通知");
            put("sort", "pubtime");
        }};
        int pageIndex = 0;
        boolean hasNextPage = true;
        List<String> ret = new ArrayList<>();
        while (hasNextPage) {
            params.put("p", pageIndex++);
            String bodyStr = HttpServiceFactory.createHttpService().getBody(SEARCH_URL, params);
            JSONObject body = JSON.parseObject(bodyStr);
            if(1001 == body.getInteger("code")) {
                return Collections.emptyList();
            }
            assert 200 == body.getInteger("code") :
                    String.format("%s: %s: %s", SEARCH_URL, body.getInteger("code"), body.getString("msg"));
            JSONObject searchVO = body.getJSONObject("searchVO");
            JSONArray listVO = searchVO.getJSONArray("listVO");
            for (int i = 0; i < listVO.size(); i++) {
                JSONObject obj = listVO.getJSONObject(i);
                String title = obj.getString("title");
                if(title.contains(String.valueOf(year))) {
                    String url = obj.getString("url");
                    if(!PAPER_EXCLUDE.contains(url)) {
                        ret.add(url);
                    }
                }
            }
            hasNextPage = pageIndex < searchVO.getLong("totalpage");
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
        Pattern pattern = Pattern.compile(
                "https://www.gov.cn/zhengce/(zhengceku|content)/\\d{4}-\\d{2}/\\d{2}/content_\\d+.htm");
        Matcher matcher = pattern.matcher(url);
        boolean conform = matcher.find();
        assert conform : "网站变化,需要人工验证";
        String body = HttpServiceFactory.createHttpService().getBody(url, null);
        Document doc = Jsoup.parse(body);
        Element container = doc.getElementById("UCAP-CONTENT");
        assert container != null : "无法从url获取政策文件主体： " + url;

        StringBuilder content = new StringBuilder();
        for (Element p : container.getElementsByTag("p")) {
            content.append(p.text()).append("\n");
        }
        assert content.length() > 0 : "无法从url获取政策文件内容: " + url;
        return content.toString().trim();
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

        Iterator<Tuple<String, String>> rules = concatIterator(
                getNormalRules(lines.iterator()),
                getPatchRules(lines.iterator()));

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

    public static void main(String[] args) {
        for (int i = 2023; i < 2025; i++) {
            System.out.println(fetchHoliday(i));
        }
    }

}
