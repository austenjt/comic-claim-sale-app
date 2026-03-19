package org.example.functions.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.util.EnvHelper;

import java.util.List;
import java.util.Properties;

@Slf4j
public class EmailService {

    private static EmailService SERVICE_INSTANCE;

    public static EmailService getServiceInstance() {
        if (SERVICE_INSTANCE == null) {
            SERVICE_INSTANCE = new EmailService();
        }
        return SERVICE_INSTANCE;
    }

    /**
     * Sends a plain-text email via STARTTLS SMTP (privateemail.com port 587).
     *
     * @param toAddresses list of recipient email addresses
     * @param replyTo     reply-to address (may be null)
     * @param bccAddress  single BCC address (may be null)
     * @param subject     email subject
     * @param body        plain-text body
     */
    public void send(List<String> toAddresses, String replyTo, String bccAddress, String subject, String body) {
        String host     = EnvHelper.getSmtpHost();
        String username = EnvHelper.getSmtpUsername();
        String password = EnvHelper.getSmtpPassword();

        if (host == null || username == null || password == null) {
            log.error("SMTP credentials not fully configured — email not sent");
            return;
        }
        if (toAddresses == null || toAddresses.isEmpty()) {
            log.warn("No recipients provided — email not sent");
            return;
        }

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new jakarta.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            MimeMessage email = new MimeMessage(session);
            email.setFrom(new InternetAddress(username));
            for (String to : toAddresses) {
                email.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }
            if (replyTo != null) {
                email.setReplyTo(new jakarta.mail.Address[]{new InternetAddress(replyTo)});
            }
            if (bccAddress != null) {
                email.addRecipient(Message.RecipientType.BCC, new InternetAddress(bccAddress));
            }
            email.setSubject("LightningComics.Rocks: " + subject);
            email.setText(body);

            Transport.send(email);
            log.info("Email sent to {} recipient(s): {}", toAddresses.size(), subject);

        } catch (MessagingException e) {
            log.error("Failed to send email via SMTP", e);
        }
    }
}
