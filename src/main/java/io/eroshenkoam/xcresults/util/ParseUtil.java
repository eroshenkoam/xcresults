package io.eroshenkoam.xcresults.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;

public final class ParseUtil {

    private ParseUtil(){
    }

    @SuppressWarnings("PMD.SimpleDateFormatNeedsLocale")
    public static Long parseDate(final String date) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            return format.parse(date).getTime();
        } catch (ParseException e) {
            return null;
        }
    }

}
