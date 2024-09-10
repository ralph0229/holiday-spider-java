package holiday.util;

import holiday.Tuple;
import lombok.experimental.UtilityClass;

import java.util.Iterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 2024/9/10
 *
 * @author Href
 * @version 1.0.0
 */
@UtilityClass
public class CommonUtil {

    /**
     * 合并两个Iterator
     * @param iterator1 iterator1
     * @param iterator2 iterator2
     * @return merged iterator
     */
    public static Iterator<Tuple<String, String>> concatIterator(
            Iterator<Tuple<String, String>> iterator1,
            Iterator<Tuple<String, String>> iterator2) {
        return Stream.concat(
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator1, 0), false),
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator2, 0), false)
        ).iterator();
    }

}
