package com.dfs.corporate.repository;

import com.dfs.corporate.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findByCodeIgnoreCase(String code);
    List<Brand> findByActiveTrueOrderByNameAsc();
}
