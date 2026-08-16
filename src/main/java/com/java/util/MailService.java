package com.java.util;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public final class MailService {
    private MailService() {
    }
    public static boolean sendPasswordReset(String recipient, String fullName, String resetUrl) {
        String host = env("BLOG_SMTP_HOST");
        String port = envOrDefault("BLOG_SMTP_PORT", "587");
        String username = env("BLOG_SMTP_USER");
        String password = env("BLOG_SMTP_PASSWORD");
        String from = envOrDefault("BLOG_SMTP_FROM", username);

        if (host == null || username == null || password == null || from == null) {
            return false;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", envOrDefault("BLOG_SMTP_STARTTLS", "true"));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from, "Blog SE"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject("Đặt lại mật khẩu Blog SE", "UTF-8");
            String safeName = fullName == null || fullName.isBlank() ? "bạn" : fullName;
            message.setText(
                "Xin chào " + safeName + ",\n\n" +
                "Mở liên kết sau để đặt lại mật khẩu. Liên kết có hiệu lực trong 30 phút:\n" +
                resetUrl + "\n\n" +
                "Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email.",
                "UTF-8"
            );
            Transport.send(message);
            return true;
        } catch (MessagingException | UnsupportedEncodingException ex) {
            System.err.println("Không thể gửi email đặt lại mật khẩu: " + ex.getMessage());
            return false;
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String envOrDefault(String name, String fallback) {
        String value = env(name);
        return value == null ? fallback : value;
    }
}