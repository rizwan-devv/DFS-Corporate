package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "video_challenges")
public class VideoChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false, unique = true, length = 64)
    private String challengeId;

    @Column(name = "app_user_id", nullable = false)
    private Long appUserId;

    @Column(name = "template_id", nullable = false, length = 10)
    private String templateId;

    @Column(name = "script_text", nullable = false, length = 500)
    private String scriptText;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "video_path", length = 500)
    private String videoPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VideoChallengeStatus status = VideoChallengeStatus.ISSUED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getChallengeId() { return challengeId; }
    public void setChallengeId(String challengeId) { this.challengeId = challengeId; }
    public Long getAppUserId() { return appUserId; }
    public void setAppUserId(Long appUserId) { this.appUserId = appUserId; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getScriptText() { return scriptText; }
    public void setScriptText(String scriptText) { this.scriptText = scriptText; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }
    public VideoChallengeStatus getStatus() { return status; }
    public void setStatus(VideoChallengeStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
