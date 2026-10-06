package com.ballastlane.assessment.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class MailPasswordResetNotifierTest {

    JavaMailSender sender = mock(JavaMailSender.class);
    final List<MailPasswordResetNotifier> created = new ArrayList<>();

    private MailPasswordResetNotifier notifier(String publicUrl) {
        return notifier(publicUrl, Executors.newFixedThreadPool(1));
    }

    private MailPasswordResetNotifier notifier(String publicUrl, ExecutorService executor) {
        MailPasswordResetNotifier notifier = new MailPasswordResetNotifier(
                sender, "no-reply@pokedex.local", publicUrl, Duration.ofMinutes(30), executor);
        created.add(notifier);
        return notifier;
    }

    /** close() drains the queue, so after it every send that was going to happen has happened: no sleeping needed. */
    private void sendResetLinkAndWait(MailPasswordResetNotifier notifier, String email, String token) {
        notifier.sendResetLink(email, token);
        notifier.close();
    }

    @AfterEach
    void stopExecutors() {
        created.forEach(MailPasswordResetNotifier::close);
    }

    @Test
    void sendsAPlainTextMailWithTheResetLink() {
        sendResetLinkAndWait(notifier("http://localhost:8081"), "ash@pallet.town", "tok-123");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@pokedex.local");
        assertThat(message.getTo()).containsExactly("ash@pallet.town");
        assertThat(message.getSubject()).isEqualTo("Reset your Pokedex password");
        assertThat(message.getText())
                .contains("http://localhost:8081/reset-password?token=tok-123")
                .contains("expires in 30 minutes");
    }

    @Test
    void aTrailingSlashInThePublicUrlDoesNotDoubleUp() {
        sendResetLinkAndWait(notifier("https://pokedex.example/"), "ash@pallet.town", "tok-123");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getText()).contains("https://pokedex.example/reset-password?token=tok-123");
    }

    @Test
    void aDeliveryFailureIsLoggedAsAWarningWithoutTheTokenTheLinkOrTheAddressAndDoesNotPropagate() {
        Logger logger = (Logger) LoggerFactory.getLogger(MailPasswordResetNotifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        doThrow(new MailSendException("Mail server connection failed for ash@pallet.town: tok-123"))
                .when(sender).send(any(SimpleMailMessage.class));
        try {
            sendResetLinkAndWait(notifier("http://localhost:8081"), "ash@pallet.town", "tok-123");

            assertThat(appender.list).hasSize(1).allSatisfy(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).doesNotContain("tok-123", "ash@pallet.town", "reset-password");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void returnsBeforeASlowSendCompletesSoTheResponseTimeDoesNotRevealWhichAccountsExist() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch sent = new CountDownLatch(1);
        doAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            sent.countDown();
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        MailPasswordResetNotifier notifier = notifier("http://localhost:8081");

        notifier.sendResetLink("ash@pallet.town", "tok-123");

        assertThat(sent.getCount()).as("the send is still blocked when sendResetLink returns").isEqualTo(1);
        release.countDown();
        assertThat(sent.await(10, TimeUnit.SECONDS)).isTrue();
        notifier.close();
        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void anUnexpectedFailureInTheBackgroundSendIsAlsoSwallowedAndLoggedWithoutDetails() {
        Logger logger = (Logger) LoggerFactory.getLogger(MailPasswordResetNotifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        doThrow(new IllegalStateException("boom for ash@pallet.town tok-123")).when(sender).send(any(SimpleMailMessage.class));
        try {
            sendResetLinkAndWait(notifier("http://localhost:8081"), "ash@pallet.town", "tok-123");

            assertThat(appender.list).hasSize(1).allSatisfy(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).doesNotContain("tok-123", "ash@pallet.town");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void aFullQueueDropsTheMailWithAWarningInsteadOfFailingTheRequestOrBlockingIt() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            release.await(10, TimeUnit.SECONDS);
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        ExecutorService tiny = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        MailPasswordResetNotifier notifier = notifier("http://localhost:8081", tiny);
        Logger logger = (Logger) LoggerFactory.getLogger(MailPasswordResetNotifier.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            notifier.sendResetLink("a@pallet.town", "t1");
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            notifier.sendResetLink("b@pallet.town", "t2"); // waits in the queue
            notifier.sendResetLink("c@pallet.town", "t3"); // rejected

            assertThat(appender.list).hasSize(1).allSatisfy(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).doesNotContain("t3", "c@pallet.town");
            });
        } finally {
            logger.detachAppender(appender);
            release.countDown();
            notifier.close();
        }
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void closeLetsQueuedMailsFinishAndIsSafeToCallTwice() {
        MailPasswordResetNotifier notifier = notifier("http://localhost:8081");
        notifier.sendResetLink("ash@pallet.town", "tok-123");

        notifier.close();
        notifier.close();

        verify(sender).send(any(SimpleMailMessage.class));
    }
}
