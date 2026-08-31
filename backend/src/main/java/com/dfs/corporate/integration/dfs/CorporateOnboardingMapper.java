package com.dfs.corporate.integration.dfs;

import com.dfs.corporate.domain.PartnerAppUser;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.util.IdentityFormats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Builds DFS corporateonboarding payload.
 * CNIC = digits only; pin / partner passwords = plain text (never bcrypt hash).
 */
@Component
public class CorporateOnboardingMapper {

    private final String channel;
    private final String defaultLevelCode;
    private final String defaultCityId;
    private final String defaultBusinessTypeId;
    private final String defaultMonthlyVolumeId;

    public CorporateOnboardingMapper(
            @Value("${dfs.account-api.channel:AGNT}") String channel,
            @Value("${dfs.account-api.level-code:L4}") String defaultLevelCode,
            @Value("${dfs.account-api.default-city-id:1}") String defaultCityId,
            @Value("${dfs.account-api.default-business-type-id:1}") String defaultBusinessTypeId,
            @Value("${dfs.account-api.default-monthly-volume-id:1}") String defaultMonthlyVolumeId) {
        this.channel = channel;
        this.defaultLevelCode = defaultLevelCode;
        this.defaultCityId = defaultCityId;
        this.defaultBusinessTypeId = defaultBusinessTypeId;
        this.defaultMonthlyVolumeId = defaultMonthlyVolumeId;
    }

    public CorporateOnboardingRequest map(Party party, List<PartnerAppUser> appUsers) {
        PartnerAppUser primary = pickPrimary(party, appUsers);

        String imei = firstNonBlank(
                primary != null ? primary.getImeiNo() : null,
                "000000000000000");
        // Wallet / mPIN — plain digits only (never hashed)
        String pin = firstNonBlank(
                IdentityFormats.pinPlain(primary != null ? primary.getWalletPin() : null),
                IdentityFormats.pinPlain(party.getWalletPin()),
                "1234");

        CorporateOnboardingRequest req = new CorporateOnboardingRequest();
        req.setChannel(channel);
        req.setImieNo(imei);

        CorporateOnboardingRequest.Payload p = new CorporateOnboardingRequest.Payload();
        p.setAppVersion(firstNonBlank(primary != null ? primary.getAppVersion() : null, "1.0.0"));
        p.setDeviceModel(firstNonBlank(primary != null ? primary.getDeviceModel() : null, "Android"));
        p.setImeiNo(imei);
        p.setLevelCode(firstNonBlank(party.getLevelCode(), defaultLevelCode));
        p.setFullName(firstNonBlank(
                primary != null ? primary.getCnicFullName() : null,
                primary != null ? primary.getFullName() : null,
                party.getFullName()));
        p.setFatherName(firstNonBlank(
                primary != null ? primary.getFatherName() : null,
                party.getFatherOrSpouseName(),
                "N/A"));
        p.setMobileNumber(firstNonBlank(
                IdentityFormats.phoneDigits(primary != null ? primary.getPhone() : null),
                IdentityFormats.phoneDigits(party.getPhone())));
        p.setPermanentAddress(firstNonBlank(
                primary != null ? primary.getPermanentAddress() : null,
                party.getPermanentAddress(),
                party.getRegisteredAddress(),
                party.getAddressLine(),
                "N/A"));
        p.setPresentAddress(firstNonBlank(
                primary != null ? primary.getPresentAddress() : null,
                party.getPresentAddress(),
                party.getMailingAddress(),
                party.getPlaceOfBusiness(),
                p.getPermanentAddress()));
        p.setGender(normalizeGender(firstNonBlank(
                primary != null ? primary.getGender() : null,
                party.getGender(),
                "M")));
        // nidNo — always without dashes
        p.setNidNo(firstNonBlank(
                IdentityFormats.cnicDigits(primary != null ? primary.getCnicNumber() : null),
                IdentityFormats.cnicDigits(party.getCnicNumber()),
                "0000000000000"));
        p.setDob(formatDate(firstDate(
                primary != null ? primary.getDateOfBirth() : null,
                party.getDateOfBirth(),
                LocalDate.of(1990, 1, 1))));
        p.setNidIssuanceDate(formatDate(firstDate(
                primary != null ? primary.getNidIssuanceDate() : null,
                party.getNidIssuanceDate(),
                LocalDate.of(2020, 1, 1))));
        p.setCityId(firstNonBlank(
                primary != null ? primary.getCityId() : null,
                party.getCityId(),
                defaultCityId));
        p.setPin(pin);
        p.setConfirmMpin(pin);
        p.setParentAgentId(party.getParentAgentId() != null ? party.getParentAgentId() : "");
        p.setBusinessName(firstNonBlank(party.getBusinessName(), party.getFullName()));
        p.setBusinessTypeId(firstNonBlank(party.getBusinessTypeId(), defaultBusinessTypeId));
        p.setBusinessAddress(firstNonBlank(
                party.getBusinessAddress(),
                party.getRegisteredAddress(),
                party.getPlaceOfBusiness(),
                p.getPermanentAddress()));
        p.setExpectedMonthlyVolumeId(firstNonBlank(party.getExpectedMonthlyVolumeId(), defaultMonthlyVolumeId));

        if (appUsers != null) {
            for (PartnerAppUser u : appUsers) {
                String email = firstNonBlank(u.getEmail(), party.getEmail());
                if (email == null) continue;
                // Plain password only — never passwordHash / bcrypt
                String plain = firstNonBlank(
                        IdentityFormats.passwordPlain(u.getPasswordPlain()),
                        IdentityFormats.passwordPlain(u.getTempPin()),
                        pin,
                        "1234");
                p.getPartners().add(new CorporateOnboardingRequest.PartnerCredential(email, plain));
            }
        }
        if (p.getPartners().isEmpty() && party.getEmail() != null) {
            p.getPartners().add(new CorporateOnboardingRequest.PartnerCredential(party.getEmail(), pin));
        }

        req.setPayload(p);
        return req;
    }

    private PartnerAppUser pickPrimary(Party party, List<PartnerAppUser> appUsers) {
        if (appUsers == null || appUsers.isEmpty()) return null;
        String partyPhone = normalizePhone(party.getPhone());
        return appUsers.stream()
                .filter(u -> partyPhone.equals(normalizePhone(u.getPhone())))
                .findFirst()
                .or(() -> appUsers.stream()
                        .filter(u -> u.getCompletedAt() != null)
                        .min(Comparator.comparing(PartnerAppUser::getCompletedAt)))
                .orElse(appUsers.get(0));
    }

    private static String normalizeGender(String g) {
        if (g == null) return "M";
        String t = g.trim().toUpperCase();
        if (t.startsWith("F")) return "F";
        return "M";
    }

    private static String formatDate(LocalDate d) {
        return d != null ? d.toString() : "1990-01-01";
    }

    private static LocalDate firstDate(LocalDate... dates) {
        if (dates == null) return null;
        for (LocalDate d : dates) {
            if (d != null) return d;
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static String normalizePhone(String phone) {
        String n = IdentityFormats.phoneDigits(phone);
        return n == null ? "" : n;
    }
}
