package com.sih.sif.repository;

import com.sih.sif.model.SafetyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SafetyReportRepository.java
 *
 * Spring Data MongoDB Repository interface for performing CRUD and query operations
 * on the 'safety_reports' collection.
 */
@Repository
public interface SafetyReportRepository extends MongoRepository<SafetyReport, String> {

    Page<SafetyReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SafetyReport> findByRiskLevelInOrderByCreatedAtDesc(List<String> riskLevels, Pageable pageable);

    List<SafetyReport> findByRiskLevelInOrderByCreatedAtDesc(List<String> riskLevels);

    List<SafetyReport> findAllByOrderByCreatedAtDesc();

    /**
     * Counts how many safety reports have sifPrecursorDetected == true.
     * Equivalent SQL: SELECT COUNT(*) FROM safety_reports WHERE sif_precursor_detected = true;
     */
    long countBySifPrecursorDetectedTrue();

    /**
     * Counts the total number of reports belonging to a specific risk tier (e.g., "CRITICAL").
     * Equivalent SQL: SELECT COUNT(*) FROM safety_reports WHERE risk_level = ?;
     */
    long countByRiskLevel(String riskLevel);
}
