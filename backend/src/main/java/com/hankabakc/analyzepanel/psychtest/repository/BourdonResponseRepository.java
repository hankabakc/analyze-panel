package com.hankabakc.analyzepanel.psychtest.repository;

import com.hankabakc.analyzepanel.psychtest.entity.BourdonResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BourdonResponseRepository extends JpaRepository<BourdonResponse, UUID> {

    Optional<BourdonResponse> findByAssignmentId(UUID assignmentId);

    java.util.List<BourdonResponse> findAllByAssignmentIdIn(java.util.Collection<UUID> assignmentIds);

    void deleteByAssignmentId(UUID assignmentId);

    boolean existsByAssignmentId(UUID assignmentId);
}
