package cuckoo.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.zip.GZIPInputStream;

/**
 * Parses Wikipedia pageview dump files for use as realistic hash table keys.
 *
 * File format (one record per line):
 * <pre>domain page_title view_count bytes_served</pre>
 * Example: {@code en Main_Page 12345 0}
 */
public final class WikipediaDataLoader {
    private WikipediaDataLoader() {}

    /**
     * Parse a Wikipedia pageviews file (gzip-compressed or plain text).
     * Extracts page_title from lines matching the "en" domain.
     * Returns up to maxKeys unique page titles.
     *
     * File format: "domain page_title view_count bytes_served" per line
     * Example: "en Main_Page 12345 0"
     *
     * @param path    path to the pageviews file (supports .gz)
     * @param maxKeys maximum number of unique keys to return
     * @return array of unique page title strings
     * @throws IOException if the file cannot be read
     */
    public static String[] loadPageTitles(String path, int maxKeys) throws IOException {
        LinkedHashSet<String> titles = new LinkedHashSet<>();

        try (BufferedReader reader = createReader(path)) {
            String line;
            while ((line = reader.readLine()) != null && titles.size() < maxKeys) {
                // Split on whitespace; expect at least 3 fields: domain page_title view_count
                String[] fields = line.split("\\s+");
                if (fields.length < 3) {
                    continue;
                }

                String domain = fields[0];
                if (domain.equals("en") || domain.startsWith("en.")) {
                    titles.add(fields[1]);
                }
            }
        }

        return titles.toArray(new String[0]);
    }

    private static BufferedReader createReader(String path) throws IOException {
        if (path.endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(Paths.get(path))),
                    StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8);
    }
}
