package com.ballastlane.assessment.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class MailPasswordResetNotifierTest {

    JavaMailSender sender = mock(JavaMailSender.class);

    private MailPasswordResetNotifier notifier(String publicUrl) {
        return new MailPasswordResetNotifier(sender, "no-reply@pokedex.local", publicUrl, Duration.ofMinutes(30));
    }

    @Test
    void sendsAPlainTextMailWithTheResetLink() {
        notifier("http://localhost:8081").sendResetLink("ash@pallet.town", "tok-123");

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
        notifier("https://pokedex.example/").sendResetLink("ash@pallet.town", "tok-123");

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
            notifier("http://localhost:8081").sendResetLink("ash@pallet.town", "tok-123");

            assertThat(appender.list).hasSize(1).allSatisfy(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).doesNotContain("tok-123", "ash@pallet.town", "reset-password");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
