package holiday;

import lombok.Data;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 描述解析
 * 2023/6/29
 *
 * @author Href
 * @version 1.0.0
 */
@Data
public class DescriptionParser {

    private String name;

    private String description;

    private int year;

    protected List<LocalDate> dateHistory;

    /**
     * 构造函数
     *
     * @param description 假期安排描述
     * @param year 年份
     */
    public DescriptionParser(String name, String description, int year) {
        this.name = name;
        this.description = description;
        this.year = year;
        this.dateHistory = new ArrayList<>();
    }

    /**
     * 解析
     *
     * @return 节日安排days
     */
    public List<Map<String, Object>> parse() {
        return Arrays.stream(description.split("[，。；]"))
                .map(sentence -> new SentenceParser(this, sentence).parse())
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 格式化日期
     *
     * @param yearStr 年份
     * @param monthStr 月份
     * @param dayStr 日
     * @return 格式化后日期
     */
    public LocalDate getDate(String yearStr, String monthStr, String dayStr) {
        int year = Optional.ofNullable(yearStr).map(Integer::parseInt).orElse(0);
        int month = Optional.ofNullable(monthStr).map(Integer::parseInt).orElse(0);
        int day = Optional.ofNullable(dayStr).map(Integer::parseInt).orElse(0);
        assert day != 0: "没有指定的日";
        if (month == 0) {
            month = dateHistory.get(dateHistory.size() -1).getMonthValue();
        }
        if (
                year == 0
                && month == 12
                && !this.dateHistory.isEmpty()
                && LocalDate.of(this.year, 2, 1).isAfter(dateHistory.stream().max(LocalDate::compareTo).get())
        ) {
            year = this.year -1;
        }
        if (year == 0) {
            year = this.year;
        }
        return LocalDate.of(year, month, day);
    }
}
