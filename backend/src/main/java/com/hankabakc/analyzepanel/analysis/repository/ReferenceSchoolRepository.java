package com.hankabakc.analyzepanel.analysis.repository;

import com.hankabakc.analyzepanel.analysis.entity.ReferenceSchool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReferenceSchoolRepository extends JpaRepository<ReferenceSchool, UUID> {

    /**
     * searchSchools: Okul ismi veya şehir adına göre arama yapar.
     */
    @Query("SELECT r FROM ReferenceSchool r WHERE " +
           "LOWER(r.schoolName) LIKE LOWER(concat('%', :query, '%')) OR " +
           "LOWER(r.city) LIKE LOWER(concat('%', :query, '%')) " +
           "ORDER BY r.baseScore DESC")
    List<ReferenceSchool> searchSchools(@Param("query") String query);

    List<ReferenceSchool> findAllByOrderByBaseScoreDesc();
}
