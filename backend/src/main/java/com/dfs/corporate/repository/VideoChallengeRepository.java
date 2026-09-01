package com.dfs.corporate.repository;

import com.dfs.corporate.domain.VideoChallenge;
import com.dfs.corporate.domain.VideoChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoChallengeRepository extends JpaRepository<VideoChallenge, Long> {

    Optional<VideoChallenge> findByChallengeId(String challengeId);

    List<VideoChallenge> findByAppUserIdAndStatus(Long appUserId, VideoChallengeStatus status);
}
