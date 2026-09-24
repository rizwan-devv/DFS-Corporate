package com.dfs.corporate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public MailService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("[MAIL-DEV] to={} subject={} body=\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Mail sent from={} to={} subject={}", from, to, subject);
            log.info("[MAIL-DEV] to={} subject={} body=\n{}", to, subject, body);
        } catch (Exception e) {
            // Demo-safe: SMTP failure must not fail signup / submit / approve
            log.error("Mail send failed from={} to={} subject={}: {}", from, to, subject, e.getMessage());
            log.info("[MAIL-FALLBACK] to={} subject={} body=\n{}", to, subject, body);
        }
    }
}
