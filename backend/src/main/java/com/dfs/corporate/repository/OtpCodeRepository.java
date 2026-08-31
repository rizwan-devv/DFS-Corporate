package com.dfs.corporate.repository;

import com.dfs.corporate.domain.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
            String email, String purpose);
}
