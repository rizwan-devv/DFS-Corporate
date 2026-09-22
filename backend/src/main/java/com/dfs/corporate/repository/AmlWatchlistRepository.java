package com.dfs.corporate.repository;

import com.dfs.corporate.domain.AmlListSource;
import com.dfs.corporate.domain.AmlWatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AmlWatchlistRepository extends JpaRepository<AmlWatchlistEntry, Long> {
    List<AmlWatchlistEntry> findByActiveTrue();
    List<AmlWatchlistEntry> findByActiveTrueAndCnic(String cnic);
    long countByActiveTrue();
    long countByActiveTrueAndListSource(AmlListSource listSource);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AmlWatchlistEntry e set e.active = false where e.listSource in :sources and e.active = true")
    int deactivateByListSourceIn(@Param("sources") Collection<AmlListSource> sources);
}
