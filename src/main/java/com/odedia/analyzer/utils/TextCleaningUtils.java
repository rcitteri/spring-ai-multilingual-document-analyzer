package com.odedia.analyzer.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Utility class for cleaning extracted text before embedding.
 * 
 * Removes problematic content that can cause embedding API failures:
 * - Binary/control characters
 * - Base64-encoded data
 * - Zero-width Unicode characters
 * - Excessive whitespace
 */
public class TextCleaningUtils {

    private static final Logger logger = LoggerFactory.getLogger(TextCleaningUtils.class);

    // Pattern for base64-like strings (long sequences of alphanumeric + /+=)
    private static final Pattern BASE64_PATTERN = Pattern.compile(
            "[A-Za-z0-9+/]{50,}={0,2}");

    // Pattern for multiple whitespace
    private static final Pattern MULTI_SPACE = Pattern.compile(" {2,}");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\n{3,}");

    /**
     * Clean extracted text to prevent embedding API errors.
     * 
     * @param text Raw extracted text
     * @return Cleaned text safe for embedding
     */
    public static String cleanExtractedText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String original = text;

        // 1. Remove base64-encoded data (often from embedded images)
        text = BASE64_PATTERN.matcher(text).replaceAll(" ");

        // 2. Remove control characters (except newline, tab, carriage return)
        StringBuilder cleaned = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            // Keep: printable chars, newline, tab, carriage return
            // HEBREW: U+0590 to U+05FF
            // ENGLISH: ASCII printable
            // ARABIC: U+0600 to U+06FF (bonus support)
            if (c == '\n' || c == '\t' || c == '\r' ||
                    (c >= 0x0020 && c <= 0x007E) || // ASCII printable
                    (c >= 0x0590 && c <= 0x05FF) || // Hebrew
                    (c >= 0x0600 && c <= 0x06FF) || // Arabic
                    (c >= 0x00A0 && c <= 0x00FF) || // Latin-1 supplement
                    Character.isWhitespace(c)) {
                cleaned.append(c);
            }
            // Skip: control chars, zero-width chars, other problematic Unicode
        }
        text = cleaned.toString();

        // 3. Normalize whitespace
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");

        // 4. Trim and remove leading/trailing whitespace on each line
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.append(trimmed).append("\n");
            }
        }
        text = result.toString().trim();

        int removed = original.length() - text.length();
        if (removed > 100) {
            logger.info("Cleaned text: removed {} characters ({} -> {})",
                    removed, original.length(), text.length());
        }

        return text;
    }
}
