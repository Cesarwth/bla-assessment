package com.ballastlane.assessment.infrastructure.mail;

import com.ballastlane.assessment.application.port.out.PasswordResetNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class MailPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailPasswordResetNotifier.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String publicUrl;
    private final long ttlMinutes;

    public MailPasswordResetNotifier(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from,
            @Value("${app.public-url}") String publicUrl,
            @Value("${app.password-reset.ttl}") java.time.Duration ttl) {
        this.mailSender = mailSender;
        this.from = from;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.ttlMinutes = ttl.toMinutes();
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
            mailSender.send(message);
        } catch (MailException e) {
            // The same 202 goes back either way (no enumeration). Never log the message, the address or the link:
            // the link is a credential and exception texts can quote the recipient.
            log.warn("Could not send a password reset e-mail ({})", e.getClass().getSimpleName());
        }
    }
}
