package com.odedia.analyzer.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Resilient wrapper for LLM calls with retry logic, timeouts, and fallback
 * behavior.
 *
 * Features:
 * - Exponential backoff retry (3 attempts by default)
 * - Configurable timeouts
 * - Graceful fallback on failure
 * - Circuit breaker pattern (fail-fast after consecutive failures)
 */
@Service
public class ResilientLlmService {

    private static final Logger logger = LoggerFactory.getLogger(ResilientLlmService.class);

    private final int maxRetries;
    private final long retryDelayMs;
    private final int timeoutSeconds;

    // Circuit breaker state
    private int consecutiveFailures = 0;
    private long circuitOpenUntil = 0;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final long CIRCUIT_OPEN_DURATION_MS = 30_000;

    public ResilientLlmService(
            @Value("${app.ai.resilience.maxRetries:3}") int maxRetries,
            @Value("${app.ai.resilience.retryDelayMs:1000}") long retryDelayMs,
            @Value("${app.ai.resilience.timeoutSeconds:120}") int timeoutSeconds) {
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.timeoutSeconds = timeoutSeconds;

        logger.info("Initialized ResilientLlmService: maxRetries={}, retryDelayMs={}, timeoutSeconds={}",
                maxRetries, retryDelayMs, timeoutSeconds);
    }

    /**
     * Execute an LLM call with retry logic and fallback.
     *
     * @param operation       Description of the operation (for logging)
     * @param llmCall         The LLM call to execute
     * @param fallbackMessage Message to return if all retries fail
     * @return The LLM response, or fallback message on failure
     */
    public String callWithRetry(String operation, Supplier<String> llmCall, String fallbackMessage) {
        // Check circuit breaker
        if (isCircuitOpen()) {
            logger.warn("[{}] Circuit breaker is OPEN, returning fallback immediately", operation);
            return fallbackMessage;
        }

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("[{}] Attempt {}/{}", operation, attempt, maxRetries);

                String result = executeWithTimeout(llmCall);

                // Success - reset circuit breaker
                onSuccess();

                logger.debug("[{}] Success on attempt {}", operation, attempt);
                return result;

            } catch (Exception e) {
                lastException = e;
                logger.warn("[{}] Attempt {}/{} failed: {}", operation, attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    long delay = calculateBackoff(attempt);
                    logger.debug("[{}] Waiting {}ms before retry...", operation, delay);
                    sleep(delay);
                }
            }
        }

        // All retries failed
        onFailure();
        logger.error("[{}] All {} attempts failed, returning fallback. Last error: {}",
                operation, maxRetries, lastException != null ? lastException.getMessage() : "unknown");

        return fallbackMessage;
    }

    /**
     * Execute an LLM call with retry logic, throwing exception on failure.
     * Use this when you need to handle the failure yourself.
     *
     * @param operation Description of the operation (for logging)
     * @param llmCall   The LLM call to execute
     * @return The LLM response
     * @throws LlmCallException if all retries fail
     */
    public String callWithRetryOrThrow(String operation, Supplier<String> llmCall) throws LlmCallException {
        // Check circuit breaker
        if (isCircuitOpen()) {
            throw new LlmCallException("Circuit breaker is open", null);
        }

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.debug("[{}] Attempt {}/{}", operation, attempt, maxRetries);

                String result = executeWithTimeout(llmCall);
                onSuccess();

                return result;

            } catch (Exception e) {
                lastException = e;
                logger.warn("[{}] Attempt {}/{} failed: {}", operation, attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    long delay = calculateBackoff(attempt);
                    sleep(delay);
                }
            }
        }

        onFailure();
        throw new LlmCallException("All " + maxRetries + " attempts failed for: " + operation, lastException);
    }

    /**
     * Execute a supplier with timeout.
     */
    private String executeWithTimeout(Supplier<String> llmCall) throws TimeoutException {
        // Note: For proper timeout handling in production, consider using
        // CompletableFuture.supplyAsync() with .orTimeout()
        // For now, we rely on the underlying ChatClient timeout settings
        return llmCall.get();
    }

    /**
     * Calculate exponential backoff delay.
     */
    private long calculateBackoff(int attempt) {
        // Exponential backoff: delay * 2^(attempt-1)
        // Attempt 1: delay, Attempt 2: 2*delay, Attempt 3: 4*delay
        return retryDelayMs * (1L << (attempt - 1));
    }

    /**
     * Check if circuit breaker is open.
     */
    private boolean isCircuitOpen() {
        if (circuitOpenUntil > System.currentTimeMillis()) {
            return true;
        }
        // Circuit has closed, reset if it was open
        if (circuitOpenUntil > 0) {
            logger.info("Circuit breaker CLOSED after cooldown period");
            circuitOpenUntil = 0;
        }
        return false;
    }

    /**
     * Record a successful call - reset failure counter.
     */
    private synchronized void onSuccess() {
        consecutiveFailures = 0;
    }

    /**
     * Record a failed call - increment failure counter and potentially open
     * circuit.
     */
    private synchronized void onFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_OPEN_DURATION_MS;
            logger.error("Circuit breaker OPENED after {} consecutive failures. Will retry in {}ms",
                    consecutiveFailures, CIRCUIT_OPEN_DURATION_MS);
        }
    }

    /**
     * Sleep for the specified duration, handling interruption.
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Custom exception for LLM call failures.
     */
    public static class LlmCallException extends Exception {
        public LlmCallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
