package com.rinoimob.service.email;

import com.rinoimob.domain.entity.EmailSenderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@dev.krgma.com.br}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sends an email using a tenant-specific SMTP configuration.
     * Creates a transient JavaMailSender on demand — does not affect the shared default sender.
     */
    public void sendEmailWithConfig(EmailSenderConfig config, String to, String subject, String text) {
        JavaMailSenderImpl sender = buildSender(config);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String from = (config.getFromName() != null && !config.getFromName().isBlank())
                    ? config.getFromName() + " <" + config.getFromEmail() + ">"
                    : config.getFromEmail();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
            log.info("Email sent via config '{}' to {}", config.getDisplayName(), to);
        } catch (Exception e) {
            log.error("Failed to send email via config '{}' to {}: {}", config.getDisplayName(), to, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    private JavaMailSenderImpl buildSender(EmailSenderConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getSmtpHost());
        sender.setPort(config.getSmtpPort() != null ? config.getSmtpPort() : 587);
        if (config.getSmtpUsername() != null) {
            sender.setUsername(config.getSmtpUsername());
        }
        if (config.getSmtpPassword() != null) {
            sender.setPassword(config.getSmtpPassword());
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        boolean tls = Boolean.TRUE.equals(config.getSmtpTls());
        props.put("mail.smtp.auth", config.getSmtpUsername() != null ? "true" : "false");
        if (tls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }

    @Async
    public void sendVerificationEmail(String email, String token) {
        try {
            String verificationUrl = frontendUrl + "/verify-email?token=" + token;
            String subject = "Verify your Rinoimob account";
            String text = String.format(
                    "Please verify your email by clicking the link below:\n\n%s\n\nThis link will expire in 24 hours.",
                    verificationUrl
            );

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Verification email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", email, e);
        }
    }

    @Async
    public void sendInvitationEmail(String email, String token, String firstName) {
        try {
            String verificationUrl = frontendUrl + "/verify-email?token=" + token;
            String subject = "Você foi convidado para o Rinoimob";
            String greeting = (firstName != null && !firstName.isBlank()) ? "Olá, " + firstName + "," : "Olá,";
            String text = String.format(
                    "%s\n\nVocê foi convidado para acessar o Rinoimob.\n\n%s\n\nEste link expira em 7 dias.",
                    greeting,
                    verificationUrl
            );

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Invitation email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send invitation email to {}", email, e);
        }
    }

    @Async
    public void sendPasswordResetEmail(String email, String token) {
        try {
            String resetUrl = frontendUrl + "/reset-password?token=" + token;
            String subject = "Password Reset Request";
            String text = String.format(
                    "We received a password reset request for your account.\n\n" +
                            "Click the link below to reset your password:\n\n%s\n\n" +
                            "This link will expire in 1 hour.\n\n" +
                            "If you didn't request this, please ignore this email.",
                    resetUrl
            );

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Password reset email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", email, e);
        }
    }
}
