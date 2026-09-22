package com.sih.sif.repository;

import com.sih.sif.model.SafetyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SafetyReportRepository.java
 *
 * Spring Data JPA Repository interface for performing CRUD and query operations
 * on the 'safety_reports' table without writing raw SQL.
 *
 * Spring Data automatically generates the implementation at runtime by inspecting
 * the method names and deriving queries from them (Method Name Query Derivation).
 */
@Repository
public interface SafetyReportRepository extends JpaRepository<SafetyReport, Long> {

    /**
     * Finds reports matching any of the specified risk levels (e.g., ["CRITICAL", "HIGH"])
     * ordered by newest first.
     * Equivalent SQL: SELECT * FROM safety_reports WHERE risk_level IN (...) ORDER BY created_at DESC;
     */
    List<SafetyReport> findByRiskLevelInOrderByCreatedAtDesc(List<String> riskLevels);

    /**
     * Retrieves all saved safety reports sorted by creation timestamp descending.
     * Equivalent SQL: SELECT * FROM safety_reports ORDER BY created_at DESC;
     */
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
