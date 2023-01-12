package io.eroshenkoam.xcresults.util;

import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.UUID;

public final class FormatUtil {

    private FormatUtil(){
    }

    public static String getResultFileName() {
        final String uuid = UUID.randomUUID().toString();
        return String.format("%s-result.json", uuid);
    }

    public static String getAttachmentFileName(final String fileExtension) {
        final String uuid = UUID.randomUUID().toString();
        return String.format("%s-attachment.%s", uuid, fileExtension);
    }

    public static Path getResultFilePath(final Path outputDir) {
        return outputDir.resolve(getResultFileName());
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
