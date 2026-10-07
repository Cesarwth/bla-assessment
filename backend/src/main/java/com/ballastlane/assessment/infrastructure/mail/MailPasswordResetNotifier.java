package com.ballastlane.assessment.infrastructure.mail;

import com.ballastlane.assessment.application.port.out.PasswordResetNotifier;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the reset e-mail in the background. A request for a known address would otherwise wait for the SMTP server
 * while an unknown one returns instantly, and the response time would reveal which accounts exist. (The known path
 * still does two small DB writes before this is called; that difference is negligible next to an SMTP round trip.)
 */
@Component
public class MailPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailPasswordResetNotifier.class);

    private static final int WORKERS = 2;
    // A mail server that is down must not grow memory without bound: past this many pending mails new ones are dropped.
    private static final int QUEUE_CAPACITY = 100;
    private static final long SHUTDOWN_GRACE_SECONDS = 10;

    private final JavaMailSender mailSender;
    private final String from;
    private final String publicUrl;
    private final long ttlMinutes;
    private final ExecutorService executor;

    @Autowired
    public MailPasswordResetNotifier(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from,
            @Value("${app.public-url}") String publicUrl,
            @Value("${app.password-reset.ttl}") Duration ttl) {
        this(mailSender, from, publicUrl, ttl, boundedExecutor());
    }

    MailPasswordResetNotifier(
            JavaMailSender mailSender, String from, String publicUrl, Duration ttl, ExecutorService executor) {
        this.mailSender = mailSender;
        this.from = from;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.ttlMinutes = ttl.toMinutes();
        this.executor = executor;
    }

    private static ExecutorService boundedExecutor() {
        return new ThreadPoolExecutor(WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "reset-mail");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @Override
    public void sendResetLink(String email, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your Pokedex password");
        message.setText("""
                We received a request to reset the password of your Pokedex account.

                Open this link to choose a new password (it works once and expires in %d minutes):
                %s/reset-password?token=%s

                If you did not ask for this, you can ignore this e-mail: your password has not changed.
                """.formatted(ttlMinutes, publicUrl, rawToken));
        try {
            executor.execute(() -> deliver(message));
        } catch (RejectedExecutionException exception) {
            log.warn("Dropped a password reset exception-mail: the outgoing mail queue is full or closed");
        }
    }

    private void deliver(SimpleMailMessage message) {
        try {
            mailSender.send(message);
        } catch (RuntimeException failure) {
            // The same 202 goes back either way (no enumeration). Never log the message, the address or the link:
            // the link is a credential and exception texts can quote the recipient.
            log.warn("Could not send a password reset failure-mail ({})", failure.getClass().getSimpleName());
        }
    }

    /** Lets mails already queued finish (bounded by the SMTP timeouts) before the application stops. */
    @PreDestroy
    void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
