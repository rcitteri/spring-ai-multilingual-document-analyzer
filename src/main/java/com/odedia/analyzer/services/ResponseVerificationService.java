package com.odedia.analyzer.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for verifying AI responses and adding confidence warnings.
 *
 * Features:
 * - Extracts confidence level from CoT responses
 * - Adds warning disclaimers for LOW/NONE confidence
 * - Detects when no sources were cited (potential hallucination)
 */
@Service
public class ResponseVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(ResponseVerificationService.class);

    // Pattern to extract [CONFIDENCE: X] from response
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile(
            "\\[CONFIDENCE:\\s*(HIGH|MEDIUM|LOW|NONE)\\]",
            Pattern.CASE_INSENSITIVE);

    // Pattern to detect source citations
    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "\\(Source:\\s*[^,]+,\\s*Page\\s*\\d+\\)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Result of response verification.
     */
    public record VerificationResult(
            String response,
            ConfidenceLevel confidence,
            boolean hasCitations,
            String warning) {
        public String getResponseWithWarning() {
            if (warning == null || warning.isEmpty()) {
                return response;
            }
            return warning + "\n\n" + response;
        }
    }

    /**
     * Confidence levels for responses.
     */
    public enum ConfidenceLevel {
        HIGH,
        MEDIUM,
        LOW,
        NONE,
        UNKNOWN // When CoT is disabled or confidence not found
    }

    /**
     * Verify an AI response and add appropriate warnings.
     *
     * @param response     The AI response to verify
     * @param language     The language for warnings ("he" or "en")
     * @param isCoTEnabled Whether Chain-of-Thought was enabled
     * @return VerificationResult with response, confidence, and any warnings
     */
    public VerificationResult verify(String response, String language, boolean isCoTEnabled) {
        if (response == null || response.isEmpty()) {
            return new VerificationResult(
                    response,
                    ConfidenceLevel.UNKNOWN,
                    false,
                    getWarning("empty", language));
        }

        // Extract confidence level if CoT was enabled
        ConfidenceLevel confidence = ConfidenceLevel.UNKNOWN;
        if (isCoTEnabled) {
            confidence = extractConfidence(response);
        }

        // Check for citations
        boolean hasCitations = hasCitations(response);

        // Build warning based on confidence and citations
        String warning = buildWarning(confidence, hasCitations, language);

        logger.info("Verification result: confidence={}, hasCitations={}, hasWarning={}",
                confidence, hasCitations, warning != null && !warning.isEmpty());

        return new VerificationResult(response, confidence, hasCitations, warning);
    }

    /**
     * Extract confidence level from response.
     */
    private ConfidenceLevel extractConfidence(String response) {
        Matcher matcher = CONFIDENCE_PATTERN.matcher(response);
        if (matcher.find()) {
            String level = matcher.group(1).toUpperCase();
            try {
                return ConfidenceLevel.valueOf(level);
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown confidence level: {}", level);
            }
        }
        return ConfidenceLevel.UNKNOWN;
    }

    /**
     * Check if response contains source citations.
     */
    private boolean hasCitations(String response) {
        return CITATION_PATTERN.matcher(response).find();
    }

    /**
     * Build warning message based on verification results.
     */
    private String buildWarning(ConfidenceLevel confidence, boolean hasCitations, String language) {
        boolean isHebrew = "he".equals(language);

        // Low/None confidence warning
        if (confidence == ConfidenceLevel.LOW) {
            return isHebrew
                    ? "⚠️ **אזהרה:** רמת הביטחון בתשובה זו נמוכה. ייתכן שהמידע אינו מלא או מדויק."
                    : "⚠️ **Warning:** This response has LOW confidence. The information may be incomplete or inaccurate.";
        }

        if (confidence == ConfidenceLevel.NONE) {
            return isHebrew
                    ? "⚠️ **אזהרה:** לא נמצא מידע רלוונטי במסמכים שהועלו. התשובה עשויה להיות לא מדויקת."
                    : "⚠️ **Warning:** No relevant information was found in the uploaded documents. This response may not be accurate.";
        }

        // No citations detected (potential hallucination)
        if (!hasCitations && confidence != ConfidenceLevel.UNKNOWN) {
            return isHebrew
                    ? "ℹ️ **הערה:** לא נמצאו ציטוטים ספציפיים למקורות בתשובה זו."
                    : "ℹ️ **Note:** No specific source citations were found in this response.";
        }

        return null; // No warning needed
    }

    /**
     * Get a predefined warning message.
     */
    private String getWarning(String type, String language) {
        boolean isHebrew = "he".equals(language);

        return switch (type) {
            case "empty" -> isHebrew
                    ? "⚠️ **שגיאה:** לא התקבלה תשובה מהמערכת."
                    : "⚠️ **Error:** No response was received from the system.";
            case "no_docs" -> isHebrew
                    ? "ℹ️ **הערה:** לא הועלו מסמכים למערכת. אנא העלה מסמכים לפני שאילת שאלות."
                    : "ℹ️ **Note:** No documents have been uploaded. Please upload documents before asking questions.";
            default -> null;
        };
    }

    /**
     * Check if a pre-flight warning should be shown (e.g., no documents uploaded).
     *
     * @param documentCount Number of documents in the vector store
     * @param language      The language for warnings
     * @return Warning message or null if no warning needed
     */
    public String getPreFlightWarning(int documentCount, String language) {
        if (documentCount == 0) {
            return getWarning("no_docs", language);
        }
        return null;
    }
}
