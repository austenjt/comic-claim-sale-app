package org.example.functions.service;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.util.EnvHelper;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
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
     * Sends a plain-text email via the Gmail REST API using OAuth2 user credentials.
     * javax.mail is used only to construct the MIME message — no SMTP connection is made.
     *
     * @param toAddresses list of recipient email addresses
     * @param replyTo     reply-to address (may be null)
     * @param subject     email subject
     * @param body        plain-text body
     */
    public void send(List<String> toAddresses, String replyTo, String subject, String body) {
        String username     = EnvHelper.getGmailUsername();
        String clientId     = EnvHelper.getGmailClientId();
        String clientSecret = EnvHelper.getGmailClientSecret();
        String refreshToken = EnvHelper.getGmailRefreshToken();

        if (username == null || clientId == null || clientSecret == null || refreshToken == null) {
            log.error("Gmail OAuth2 credentials not fully configured — email not sent");
            return;
        }
        if (toAddresses == null || toAddresses.isEmpty()) {
            log.warn("No recipients provided — email not sent");
            return;
        }

        try {
            UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

            Gmail service = new Gmail.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                .setApplicationName("Lightning Comics PDX")
                .build();

            // Build MIME message (javax.mail used for serialization only)
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);
            MimeMessage email = new MimeMessage(session);
            email.setFrom(new InternetAddress(username));
            for (String to : toAddresses) {
                email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
            }
            if (replyTo != null) {
                email.setReplyTo(new javax.mail.Address[]{new InternetAddress(replyTo)});
            }
            email.setSubject("From LightningComics.Rocks! " + subject);
            email.setText(body);

            // Encode as base64url and wrap in Gmail API message
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            email.writeTo(buffer);
            String encodedEmail = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(buffer.toByteArray());
            Message message = new Message();
            message.setRaw(encodedEmail);

            Message sent = service.users().messages().send("me", message).execute();
            log.info("Email sent to {} recipient(s): {} (id={})", toAddresses.size(), subject, sent.getId());

        } catch (MessagingException | IOException e) {
            log.error("Failed to send email via Gmail API", e);
        }
    }
}
