package holiday;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 句子解析
 * 2023/6/29
 *
 * @author Href
 * @version 1.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SentenceParser {

    private DescriptionParser parent;

    private String sentence;

    /**
     * 解析
     *
     * @return 解析days
     */
    public List<Map<String, Object>> parse() {
        List<Supplier<List<Map<String, Object>>>> parseRestMethods =
                Arrays.asList(this::parseRest1, this::parseRest2, this::parseRest3);
        return parseRestMethods.stream()
                .map(Supplier::get)
                .flatMap(List::stream)
                .peek(x -> x.put("name", parent.getName()))
                .collect(Collectors.toList());
    }

    /**
     * 解析日期
     *
     * @param dateText 日期文本
     * @return 解析后日期
     */
    private Iterator<LocalDate> extractDates(String dateText) {
        // 汇集日期解析方法
        List<Function<String, Iterator<LocalDate>>> dateExtractionMethods =
                Arrays.asList(this::extractDates1, this::extractDates2, this::extractDates3);
        dateText = dateText.replace("(", "（").replace(")", "）");
        // 执行
        String finalDateText = dateText;
        return dateExtractionMethods.stream()
                .flatMap(function -> StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(function.apply(finalDateText), 0),
                        false))
                .distinct()
                .iterator();
    }

    /**
     * 解析日期方法1
     *
     * @param dateText 日期文本
     * @return 解析后日期
     */
    private Iterator<LocalDate> extractDates1(String dateText) {
        Matcher matcher = Pattern.compile("(?:(\\d+)年)?(?:(\\d+)月)?(\\d+)日").matcher(dateText);
        List<LocalDate> dates = new ArrayList<>();
        while (matcher.find()) {
            if(matcher.groupCount() % 3 != 0) {
                continue;
            }
            LocalDate date = parent.getDate(matcher.group(1), matcher.group(2), matcher.group(3));
            if(!parent.dateHistory.contains(date)) {
                parent.dateHistory.add(date);
                dates.add(date);
            }
        }
        return dates.iterator();
    }

    /**
     * 解析日期方法2
     *
     * @param dateText 日期文本
     * @return 解析后日期
     */
    public Iterator<LocalDate> extractDates2(String dateText) {
        dateText = dateText.replaceAll("（.+?）", "");
        Matcher matcher = Pattern.compile(
                "(?:(\\d+)年)?(?:(\\d+)月)?(\\d+)日[至\\-—](?:(\\d+)年)?(?:(\\d+)月)?(\\d+)日"
        ).matcher(dateText);
        List<LocalDate> dates = new ArrayList<>();
        while (matcher.find()) {
            if(matcher.groupCount() %6 != 0) {
                continue;
            }
            LocalDate start = parent.getDate(matcher.group(1), matcher.group(2), matcher.group(3));
            LocalDate end =  parent.getDate(matcher.group(4), matcher.group(5), matcher.group(6));
            dates.add(start);
            while (start.isBefore(end)) {
                if(!parent.dateHistory.contains(start = start.plusDays(1))) {
                    parent.dateHistory.add(start);
                    dates.add(start);
                }
            }
        }
        return dates.iterator();
    }

    /**
     * 解析日期方法3
     *
     * @param dateText 日期文本
     * @return 解析后日期
     */
    public Iterator<LocalDate> extractDates3(String dateText) {
        dateText = dateText.replaceAll("（.+?）", "");
        Matcher matcher = Pattern.compile(
                "(?:(\\d+)年)?(?:(\\d+)月)?(\\d+)日(?:（[^）]+）)?(?:、(?:(\\d+)年)?(?:(\\d+)月)?(\\d+)日(?:（[^）]+）)?)+")
                .matcher(dateText);
        List<LocalDate> dates = new ArrayList<>();
        while (matcher.find()) {
            if(matcher.groupCount() % 3 != 0) {
                continue;
            }
            for (int i = 0; i < matcher.groupCount(); i += 3) {
                LocalDate date = parent.getDate(matcher.group(i + 1), matcher.group(i + 2), matcher.group(i + 3));
                if(!parent.dateHistory.contains(date)) {
                    dates.add(date);
                    parent.dateHistory.add(date);
                }
            }
        }
        return dates.iterator();
    }

    /**
     * 解析放假安排规则1
     *
     * @return 放假安排
     */
    private List<Map<String, Object>> parseRest1() {
        Matcher matcher = Pattern.compile("(.+)(放假|补休|调休|公休)+(?:\\d+天)?$").matcher(sentence);
        List<Map<String, Object>> result = new ArrayList<>();
        while (matcher.find()) {
            extractDates(matcher.group(1)).forEachRemaining(date ->result.add(new HashMap<String, Object>(){{
                put("date", date);put("isOffDay", true);
            }}));
        }
        return result;
    }

    /**
     * 解析放假安排规则2
     *
     * @return 放假安排
     */
    private List<Map<String, Object>> parseRest2() {
        Pattern pattern = Pattern.compile("(.+)上班$");
        Matcher matcher = pattern.matcher(sentence);
        List<Map<String, Object>> result = new ArrayList<>();
        while (matcher.find()) {
            extractDates(matcher.group(1)).forEachRemaining(date ->result.add(new HashMap<String, Object>(){{
                put("date", date);put("isOffDay", false);
            }}));
        }
        return result;
    }

    /**
     * 解析放假安排规则3
     *
     * @return 放假安排
     */
    private List<Map<String, Object>> parseRest3() {
        Pattern pattern = Pattern.compile("(.+)调至(.+)");
        Matcher matcher = pattern.matcher(sentence);
        List<Map<String, Object>> result = new ArrayList<>();
        while (matcher.find()) {
            extractDates(matcher.group(1)).forEachRemaining(date ->result.add(new HashMap<String, Object>(){{
                put("date", date);put("isOffDay", false);
            }}));
            extractDates(matcher.group(2)).forEachRemaining(date ->result.add(new HashMap<String, Object>(){{
                put("date", date);put("isOffDay", true);
            }}));
        }
        return result;
    }

}
