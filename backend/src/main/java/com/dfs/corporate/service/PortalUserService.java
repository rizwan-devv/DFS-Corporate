package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AccountPortalRoleRepository;
import com.dfs.corporate.repository.AccountRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.PortalUserCreateRequest;
import com.dfs.corporate.web.dto.PortalUserResponse;
import com.dfs.corporate.web.dto.PortalUserRolesUpdateRequest;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PortalUserService {

    private final AccountRepository accountRepository;
    private final AccountPortalRoleRepository portalRoleRepository;
    private final PartyRepository partyRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    public PortalUserService(AccountRepository accountRepository,
                             AccountPortalRoleRepository portalRoleRepository,
                             PartyRepository partyRepository,
                             PasswordEncoder passwordEncoder,
                             MailService mailService) {
        this.accountRepository = accountRepository;
        this.portalRoleRepository = portalRoleRepository;
        this.partyRepository = partyRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    public List<PortalRole> rolesOf(Long accountId) {
        return portalRoleRepository.findByAccountId(accountId).stream()
                .map(AccountPortalRole::getPortalRole)
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }

    public boolean hasRole(Long accountId, PortalRole role) {
        return portalRoleRepository.existsByAccountIdAndPortalRole(accountId, role);
    }

    public boolean hasAnyRole(Long accountId, PortalRole... roles) {
        for (PortalRole r : roles) {
            if (hasRole(accountId, r)) return true;
        }
        return false;
    }

    /** Ensure ACTIVE master owner accounts have admin + workflow roles (idempotent). */
    @Transactional
    public void ensureOwnerRoles(Account account, Party party) {
        if (account.getRole() != Role.PARTY_USER) return;
        if (party.getPartyType() != PartyType.MERCHANT) return;
        if (portalRoleRepository.findByAccountId(account.getId()).isEmpty()) {
            assignRoles(account.getId(), EnumSet.of(
                    PortalRole.PARTY_ADMIN, PortalRole.MAKER, PortalRole.CHECKER,
                    PortalRole.APPROVER, PortalRole.RELEASER));
        }
    }

    @Transactional
    public void assignRoles(Long accountId, Collection<PortalRole> roles) {
        portalRoleRepository.deleteByAccountId(accountId);
        for (PortalRole role : new LinkedHashSet<>(roles)) {
            AccountPortalRole row = new AccountPortalRole();
            row.setAccountId(accountId);
            row.setPortalRole(role);
            portalRoleRepository.save(row);
        }
    }

    public List<PortalUserResponse> listUsers(AccountPrincipal principal) {
        Party party = requireActiveMaster(principal);
        requirePartyAdmin(principal);
        return accountRepository.findAllByPartyIdOrderByCreatedAtAsc(party.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PortalUserResponse createUser(AccountPrincipal principal, PortalUserCreateRequest req) {
        Party party = requireActiveMaster(principal);
        requirePartyAdmin(principal);
        String email = req.getEmail().trim().toLowerCase();
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        Set<PortalRole> roles = parseRoles(req.getRoles());
        if (roles.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one portal role is required");
        }

        String tempPassword = req.getTemporaryPassword() != null && !req.getTemporaryPassword().isBlank()
                ? req.getTemporaryPassword().trim()
                : generatePassword();

        Account account = new Account();
        account.setPublicId(UUID.randomUUID().toString());
        account.setPartyId(party.getId());
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(tempPassword));
        account.setRole(Role.PARTY_USER);
        account.setStatus(AccountStatus.ACTIVE);
        account.setFirstLogin(true);
        accountRepository.save(account);
        assignRoles(account.getId(), roles);

        mailService.send(email, "DFS Corporate — portal user credentials",
                "Hello,\n\nYou have been added to " + party.getBusinessName()
                        + " with roles: " + roles.stream().map(Enum::name).collect(Collectors.joining(", "))
                        + "\n\nLogin email: " + email
                        + "\nTemporary password: " + tempPassword
                        + "\n\n— DFS Corporate");

        PortalUserResponse res = toResponse(account);
        res.setTemporaryPassword(tempPassword);
        return res;
    }

    @Transactional
    public PortalUserResponse updateRoles(AccountPrincipal principal, Long accountId, PortalUserRolesUpdateRequest req) {
        Party party = requireActiveMaster(principal);
        requirePartyAdmin(principal);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!Objects.equals(account.getPartyId(), party.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "User does not belong to your corporate");
        }
        if (account.getRole() == Role.PLATFORM_ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot change platform admin");
        }
        Set<PortalRole> roles = parseRoles(req.getRoles());
        if (roles.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least one portal role is required");
        }
        assignRoles(account.getId(), roles);
        return toResponse(account);
    }

    private Set<PortalRole> parseRoles(List<String> raw) {
        if (raw == null) return Set.of();
        Set<PortalRole> out = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            try {
                out.add(PortalRole.valueOf(s.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown portal role: " + s);
            }
        }
        return out;
    }

    private Party requireActiveMaster(AccountPrincipal principal) {
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getPartyType() != PartyType.MERCHANT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only corporate (master) can manage portal users");
        }
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Corporate must be ACTIVE");
        }
        return party;
    }

    private void requirePartyAdmin(AccountPrincipal principal) {
        if (principal.getRole() == Role.PLATFORM_ADMIN) return;
        if (!hasRole(principal.getAccountId(), PortalRole.PARTY_ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PARTY_ADMIN role required");
        }
    }

    private PortalUserResponse toResponse(Account account) {
        PortalUserResponse r = new PortalUserResponse();
        r.setAccountId(account.getId());
        r.setPublicId(account.getPublicId());
        r.setEmail(account.getEmail());
        r.setStatus(account.getStatus() != null ? account.getStatus().name() : null);
        r.setRoles(rolesOf(account.getId()).stream().map(Enum::name).toList());
        r.setFirstLogin(account.isFirstLogin());
        return r;
    }

    private static String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
