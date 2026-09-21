package com.dfs.corporate.repository;

import com.dfs.corporate.domain.AmlWatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AmlWatchlistRepository extends JpaRepository<AmlWatchlistEntry, Long> {
    List<AmlWatchlistEntry> findByActiveTrue();
    List<AmlWatchlistEntry> findByActiveTrueAndCnic(String cnic);
    long countByActiveTrue();
}
