package com.dfs.corporate.service;

import com.dfs.corporate.domain.OtpCode;
import com.dfs.corporate.repository.OtpCodeRepository;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class OtpService {

    public static final String PURPOSE_SIGNUP = "SIGNUP";
    public static final String PURPOSE_APP_MOBILE = "APP_MOBILE";

    private final OtpCodeRepository otpCodeRepository;
    private final MailService mailService;
    private final SecureRandom random = new SecureRandom();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OtpService.class);

    public OtpService(OtpCodeRepository otpCodeRepository, MailService mailService) {
        this.otpCodeRepository = otpCodeRepository;
        this.mailService = mailService;
    }

    @Transactional
    public String issue(String email, String purpose) {
        return issueToTarget(email.trim().toLowerCase(), purpose, true);
    }

    /** Mobile OTP for KYC app — stored against phone in otp_codes.email column. */
    @Transactional
    public String issueMobile(String phone, String purpose) {
        String target = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
        return issueToTarget(target, purpose, false);
    }

    private String issueToTarget(String target, String purpose, boolean sendEmail) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpCode otp = new OtpCode();
        otp.setEmail(target);
        otp.setCode(code);
        otp.setPurpose(purpose);
        otp.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        otpCodeRepository.save(otp);

        log.info("OTP issued target={} purpose={} code={}", target, purpose, code);
        if (sendEmail && target.contains("@")) {
            mailService.send(target, "PayFast Corporate verification code",
                    "Your verification code is: " + code + "\nIt expires in 10 minutes.");
        } else {
            // SMS gateway not wired — code is logged for local/dev (same pattern as mail.enabled=false)
            mailService.send(target.contains("@") ? target : "otp+" + target + "@dfscorporate.local",
                    "PayFast Corporate mobile OTP",
                    "Mobile OTP for " + target + ": " + code + "\nExpires in 10 minutes.");
        }
        return code;
    }

    @Transactional
    public void verify(String email, String purpose, String code) {
        verifyTarget(email.trim().toLowerCase(), purpose, code);
    }

    @Transactional
    public void verifyMobile(String phone, String purpose, String code) {
        String target = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
        verifyTarget(target, purpose, code);
    }

    private void verifyTarget(String target, String purpose, String code) {
        OtpCode otp = otpCodeRepository
                .findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(target, purpose)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No OTP found. Please request a new one."));
        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP expired. Please request a new one.");
        }
        if (!otp.getCode().equals(code.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP code.");
        }
        otp.setConsumed(true);
        otpCodeRepository.save(otp);
    }
}
