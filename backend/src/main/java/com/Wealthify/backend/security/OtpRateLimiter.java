package com.Wealthify.backend.security;

import com.Wealthify.backend.exception.RateLimitExceededException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Memory Rate Limiter and Brute-Force Protection for OTP flows.
 *
 * Provides:
 * 1. Sliding-window rate limiting on OTP generation (forgot-password) by client IP and email.
 * 2. Failed-attempt threshold and temporary lockout on OTP verification (reset-password) by email.
 * 3. Thread-safe atomic updates to prevent check-then-increment concurrency bypass.
 * 4. Zero sensitive data leakage (no OTP values logged, uniform error messages preventing account enumeration).
 *
 * NOTE ON ARCHITECTURE:
 * This implementation uses thread-safe in-memory data structures suitable for single-node
 * deployments without requiring external infrastructure (Redis/Kafka). State resets on
 * application restart and is scoped to the local JVM process.
 */
@Component
@Slf4j
public class OtpRateLimiter {

    @Getter
    private final int maxSendPerIp;
    @Getter
    private final int maxSendPerEmail;
    @Getter
    private final long sendWindowSeconds;
    @Getter
    private final int maxVerifyAttempts;
    @Getter
    private final long verifyLockoutSeconds;

    private final Map<String, Deque<Long>> sendAttemptsByIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> sendAttemptsByEmail = new ConcurrentHashMap<>();
    private final Map<String, VerificationTracker> verificationTrackers = new ConcurrentHashMap<>();

    private final Object sendLock = new Object();
    private final Object verifyLock = new Object();

    public OtpRateLimiter(
            @Value("${app.security.otp.max-send-per-ip:3}") int maxSendPerIp,
            @Value("${app.security.otp.max-send-per-email:3}") int maxSendPerEmail,
            @Value("${app.security.otp.send-window-seconds:600}") long sendWindowSeconds,
            @Value("${app.security.otp.max-verify-attempts:5}") int maxVerifyAttempts,
            @Value("${app.security.otp.verify-lockout-seconds:900}") long verifyLockoutSeconds) {
        this.maxSendPerIp = maxSendPerIp;
        this.maxSendPerEmail = maxSendPerEmail;
        this.sendWindowSeconds = sendWindowSeconds;
        this.maxVerifyAttempts = maxVerifyAttempts;
        this.verifyLockoutSeconds = verifyLockoutSeconds;
    }

    /**
     * Atomically validates and records an OTP send request against IP and email rate limits.
     * Prevents race conditions by synchronizing the combined evaluation and recording.
     *
     * @param clientIp Client IP address
     * @param email Target user email
     * @throws RateLimitExceededException if either IP or email threshold is reached
     */
    public void checkAndRecordSendAttempt(String clientIp, String email) {
        long now = System.currentTimeMillis();
        String normalizedIp = normalizeKey(clientIp, "unknown-ip");
        String normalizedEmail = normalizeEmail(email);

        synchronized (sendLock) {
            cleanupStaleSenders(now);

            // 1. Evaluate IP Limit
            Deque<Long> ipTimestamps = sendAttemptsByIp.computeIfAbsent(normalizedIp, k -> new ArrayDeque<>());
            evictExpiredTimestamps(ipTimestamps, now, sendWindowSeconds * 1000L);
            if (ipTimestamps.size() >= maxSendPerIp) {
                long oldest = ipTimestamps.peekFirst();
                long retryAfterSec = calculateRetryAfter(oldest, sendWindowSeconds * 1000L, now);
                log.warn("OTP send rate limit exceeded for IP: {} (Retry-After: {}s)", normalizedIp, retryAfterSec);
                throw new RateLimitExceededException(
                        "Too many password reset requests. Please try again in " + formatWaitTime(retryAfterSec) + ".",
                        retryAfterSec
                );
            }

            // 2. Evaluate Email Limit (if provided)
            Deque<Long> emailTimestamps = null;
            if (normalizedEmail != null) {
                emailTimestamps = sendAttemptsByEmail.computeIfAbsent(normalizedEmail, k -> new ArrayDeque<>());
                evictExpiredTimestamps(emailTimestamps, now, sendWindowSeconds * 1000L);
                if (emailTimestamps.size() >= maxSendPerEmail) {
                    long oldest = emailTimestamps.peekFirst();
                    long retryAfterSec = calculateRetryAfter(oldest, sendWindowSeconds * 1000L, now);
                    log.warn("OTP send rate limit exceeded for target account (Retry-After: {}s)", retryAfterSec);
                    throw new RateLimitExceededException(
                            "Too many password reset requests. Please try again in " + formatWaitTime(retryAfterSec) + ".",
                            retryAfterSec
                    );
                }
            }

            // 3. Atomically record the attempt for both IP and Email
            ipTimestamps.addLast(now);
            if (emailTimestamps != null) {
                emailTimestamps.addLast(now);
            }
        }
    }

    /**
     * Checks if verification attempts are currently allowed for the email.
     * If the account has exceeded max failed attempts, a temporary lockout is enforced.
     *
     * @param email Account email
     * @throws RateLimitExceededException if verification is currently locked out
     */
    public void checkVerificationAllowed(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (verifyLock) {
            VerificationTracker tracker = verificationTrackers.get(normalizedEmail);
            if (tracker != null) {
                if (now < tracker.lockoutExpiryMs) {
                    long retryAfterSec = calculateRemainingSeconds(tracker.lockoutExpiryMs, now);
                    log.warn("OTP verification attempt blocked: account temporarily locked (Retry-After: {}s)", retryAfterSec);
                    throw new RateLimitExceededException(
                            "Too many failed verification attempts. Account is temporarily locked. Please try again in "
                                    + formatWaitTime(retryAfterSec) + ".",
                            retryAfterSec
                    );
                } else if (tracker.lockoutExpiryMs > 0) {
                    // Lockout expired: reset tracker for fresh attempts
                    tracker.lockoutExpiryMs = 0L;
                    tracker.failedAttempts = 0;
                }
            }
        }
    }

    /**
     * Records a failed OTP verification attempt.
     * If failed attempts reach the threshold, engages a temporary lockout.
     *
     * @param email Account email
     */
    public void recordVerificationFailure(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (verifyLock) {
            VerificationTracker tracker = verificationTrackers.computeIfAbsent(normalizedEmail, k -> new VerificationTracker());

            // If existing lockout had already expired, reset before counting new failure
            if (tracker.lockoutExpiryMs > 0 && now >= tracker.lockoutExpiryMs) {
                tracker.lockoutExpiryMs = 0L;
                tracker.failedAttempts = 0;
            }

            tracker.failedAttempts++;

            if (tracker.failedAttempts >= maxVerifyAttempts) {
                tracker.lockoutExpiryMs = now + (verifyLockoutSeconds * 1000L);
                tracker.failedAttempts = 0; // Reset counter so fresh attempts are granted after lockout ends
                log.warn("Max OTP verification failures reached for account. Account locked for {} seconds.", verifyLockoutSeconds);
            }
        }
    }

    /**
     * Resets the failed attempts counter and removes lockout upon successful verification.
     *
     * @param email Account email
     */
    public void recordVerificationSuccess(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return;
        }

        synchronized (verifyLock) {
            verificationTrackers.remove(normalizedEmail);
        }
    }

    /**
     * Clears all state across IP senders, email senders, and verification trackers.
     * Useful for automated test isolation.
     */
    public void resetAll() {
        synchronized (sendLock) {
            sendAttemptsByIp.clear();
            sendAttemptsByEmail.clear();
        }
        synchronized (verifyLock) {
            verificationTrackers.clear();
        }
    }

    private void evictExpiredTimestamps(Deque<Long> deque, long now, long windowMs) {
        long windowStart = now - windowMs;
        while (!deque.isEmpty() && deque.peekFirst() <= windowStart) {
            deque.pollFirst();
        }
    }

    private long calculateRetryAfter(long oldestTimestamp, long windowMs, long now) {
        long expireTime = oldestTimestamp + windowMs;
        return calculateRemainingSeconds(expireTime, now);
    }

    private long calculateRemainingSeconds(long expiryMs, long now) {
        long diff = expiryMs - now;
        return Math.max(1L, (diff + 999L) / 1000L);
    }

    private String formatWaitTime(long seconds) {
        if (seconds >= 60) {
            long minutes = (seconds + 59) / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }

    private void cleanupStaleSenders(long now) {
        // Only run periodic prune if map size grows large
        if (sendAttemptsByIp.size() > 1000) {
            sendAttemptsByIp.entrySet().removeIf(entry -> {
                evictExpiredTimestamps(entry.getValue(), now, sendWindowSeconds * 1000L);
                return entry.getValue().isEmpty();
            });
        }
        if (sendAttemptsByEmail.size() > 1000) {
            sendAttemptsByEmail.entrySet().removeIf(entry -> {
                evictExpiredTimestamps(entry.getValue(), now, sendWindowSeconds * 1000L);
                return entry.getValue().isEmpty();
            });
        }
    }

    private String normalizeKey(String key, String fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        return key.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static class VerificationTracker {
        int failedAttempts = 0;
        long lockoutExpiryMs = 0L;
    }
}
