package com.Wealthify.backend.security;

import com.Wealthify.backend.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpRateLimiterTest {

    private OtpRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // maxSendPerIp=3, maxSendPerEmail=3, sendWindow=600s, maxVerifyAttempts=5, verifyLockout=900s
        rateLimiter = new OtpRateLimiter(3, 3, 600, 5, 900);
    }

    @Test
    @DisplayName("OTP Send: First request and requests within limit succeed")
    void testSendWithinLimitSucceeds() {
        String ip = "192.168.1.100";
        String email = "student@example.com";

        rateLimiter.checkAndRecordSendAttempt(ip, email);
        rateLimiter.checkAndRecordSendAttempt(ip, email);
        rateLimiter.checkAndRecordSendAttempt(ip, email);

        // 3 requests recorded successfully without exception
    }

    @Test
    @DisplayName("OTP Send: Request exceeding IP threshold is blocked with Retry-After")
    void testSendExceedingIpLimitBlocked() {
        String ip = "10.0.0.1";

        // Consume 3 allowed attempts with different emails
        rateLimiter.checkAndRecordSendAttempt(ip, "user1@example.com");
        rateLimiter.checkAndRecordSendAttempt(ip, "user2@example.com");
        rateLimiter.checkAndRecordSendAttempt(ip, "user3@example.com");

        // 4th request from same IP must be blocked even with another email
        assertThatThrownBy(() -> rateLimiter.checkAndRecordSendAttempt(ip, "user4@example.com"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
                    assertThat(rle.getMessage()).contains("Too many password reset requests");
                });
    }

    @Test
    @DisplayName("OTP Send: Request exceeding Email threshold is blocked regardless of changing IP")
    void testSendExceedingEmailLimitBlocked() {
        String email = "target@example.com";

        // Consume 3 allowed attempts from 3 different IPs
        rateLimiter.checkAndRecordSendAttempt("1.1.1.1", email);
        rateLimiter.checkAndRecordSendAttempt("2.2.2.2", email);
        rateLimiter.checkAndRecordSendAttempt("3.3.3.3", email);

        // 4th attempt from a 4th new IP must be blocked because the target email hit limit
        assertThatThrownBy(() -> rateLimiter.checkAndRecordSendAttempt("4.4.4.4", email))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
                    assertThat(rle.getMessage()).contains("Too many password reset requests");
                });
    }

    @Test
    @DisplayName("OTP Send: Null/blank email still consumes and enforces IP limit")
    void testSendWithNullOrBlankEmailEnforcesIpLimit() {
        String ip = "172.16.0.5";

        rateLimiter.checkAndRecordSendAttempt(ip, null);
        rateLimiter.checkAndRecordSendAttempt(ip, "");
        rateLimiter.checkAndRecordSendAttempt(ip, "   ");

        assertThatThrownBy(() -> rateLimiter.checkAndRecordSendAttempt(ip, null))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("OTP Send: Rate limit messages do not reveal whether account exists or which limit was hit")
    void testSendRateLimitMessagePrivacy() {
        String ip = "192.168.1.50";
        String email = "victim@example.com";

        rateLimiter.checkAndRecordSendAttempt(ip, email);
        rateLimiter.checkAndRecordSendAttempt(ip, email);
        rateLimiter.checkAndRecordSendAttempt(ip, email);

        try {
            rateLimiter.checkAndRecordSendAttempt(ip, email);
        } catch (RateLimitExceededException ex) {
            // Must be generic, not mentioning specific email or IP
            assertThat(ex.getMessage()).doesNotContain(ip);
            assertThat(ex.getMessage()).doesNotContain(email);
            assertThat(ex.getMessage()).contains("Too many password reset requests");
        }
    }

    @Test
    @DisplayName("OTP Verification: Failed attempts trigger lockout after max failed threshold")
    void testVerificationLockoutEngagesAfterMaxFailures() {
        String email = "user@example.com";

        // First 4 failed attempts: not yet locked out
        for (int i = 0; i < 4; i++) {
            rateLimiter.checkVerificationAllowed(email);
            rateLimiter.recordVerificationFailure(email);
        }

        // 5th failure reaches threshold (maxVerifyAttempts = 5)
        rateLimiter.checkVerificationAllowed(email);
        rateLimiter.recordVerificationFailure(email);

        // 6th attempt must be blocked by checkVerificationAllowed with 429 Retry-After
        assertThatThrownBy(() -> rateLimiter.checkVerificationAllowed(email))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isGreaterThan(0);
                    assertThat(rle.getRetryAfterSeconds()).isLessThanOrEqualTo(900);
                    assertThat(rle.getMessage()).contains("Account is temporarily locked");
                });
    }

    @Test
    @DisplayName("OTP Verification: Successful reset clears failure counter and unlocks account")
    void testVerificationSuccessClearsFailureState() {
        String email = "user@example.com";

        // 4 failed attempts
        for (int i = 0; i < 4; i++) {
            rateLimiter.recordVerificationFailure(email);
        }

        // Successful verification clears tracker
        rateLimiter.recordVerificationSuccess(email);

        // Verification is allowed and user gets fresh 5 attempts again
        rateLimiter.checkVerificationAllowed(email);

        for (int i = 0; i < 4; i++) {
            rateLimiter.recordVerificationFailure(email);
            rateLimiter.checkVerificationAllowed(email);
        }
    }

    @Test
    @DisplayName("OTP Verification: Verification trackers are strictly isolated across different accounts")
    void testVerificationAccountsIsolated() {
        String emailA = "accountA@example.com";
        String emailB = "accountB@example.com";

        // Lock out account A
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordVerificationFailure(emailA);
        }

        // Account A is locked
        assertThatThrownBy(() -> rateLimiter.checkVerificationAllowed(emailA))
                .isInstanceOf(RateLimitExceededException.class);

        // Account B is completely unaffected
        rateLimiter.checkVerificationAllowed(emailB);
    }

    @Test
    @DisplayName("Concurrency: Simultaneous OTP send requests cannot bypass the limit (no check-then-increment race)")
    void testSendConcurrencyThreadSafety() throws InterruptedException {
        int threads = 20;
        int maxAllowed = 3;
        OtpRateLimiter limiter = new OtpRateLimiter(maxAllowed, maxAllowed, 600, 5, 900);

        String ip = "192.168.10.50";
        String email = "concurrent@example.com";

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rateLimitCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // wait for all threads to be ready
                    limiter.checkAndRecordSendAttempt(ip, email);
                    successCount.incrementAndGet();
                } catch (RateLimitExceededException e) {
                    rateLimitCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // fire all threads at the exact same instant
        boolean completed = endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // EXACTLY maxAllowed requests succeed, no race condition bypass
        assertThat(successCount.get()).isEqualTo(maxAllowed);
        assertThat(rateLimitCount.get()).isEqualTo(threads - maxAllowed);
    }
}
