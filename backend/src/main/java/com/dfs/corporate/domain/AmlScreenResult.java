package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "aml_screen_results")
public class AmlScreenResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "subject_type", nullable = false, length = 24)
    private String subjectType;

    @Column(name = "subject_name", length = 300)
    private String subjectName;

    @Column(name = "subject_cnic", length = 20)
    private String subjectCnic;

    @Column(nullable = false, length = 24)
    private String outcome;

    @Column(name = "match_type", length = 24)
    private String matchType;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "watchlist_id")
    private Long watchlistId;

    @Column(length = 1000)
    private String detail;

    @Column(name = "screened_at", nullable = false)
    private Instant screenedAt = Instant.now();

    public Long getId() { return id; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public String getSubjectCnic() { return subjectCnic; }
    public void setSubjectCnic(String subjectCnic) { this.subjectCnic = subjectCnic; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public Integer getMatchScore() { return matchScore; }
    public void setMatchScore(Integer matchScore) { this.matchScore = matchScore; }
    public Long getWatchlistId() { return watchlistId; }
    public void setWatchlistId(Long watchlistId) { this.watchlistId = watchlistId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getScreenedAt() { return screenedAt; }
    public void setScreenedAt(Instant screenedAt) { this.screenedAt = screenedAt; }
}
