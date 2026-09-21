package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.Beneficiary;

import java.time.Instant;

public record BeneficiaryResponse(
        Long id,
        String publicId,
        String aliasName,
        String fullName,
        String accountNumber,
        String bankName,
        String raastId,
        String mobile,
        String cnic,
        String railScope,
        boolean active,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static BeneficiaryResponse from(Beneficiary b) {
        return new BeneficiaryResponse(
                b.getId(),
                b.getPublicId(),
                b.getAliasName(),
                b.getFullName(),
                b.getAccountNumber(),
                b.getBankName(),
                b.getRaastId(),
                b.getMobile(),
                b.getCnic(),
                b.getRailScope().name(),
                b.isActive(),
                b.getNotes(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
