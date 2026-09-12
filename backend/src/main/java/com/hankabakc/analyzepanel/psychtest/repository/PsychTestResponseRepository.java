package com.hankabakc.analyzepanel.psychtest.repository;

import com.hankabakc.analyzepanel.psychtest.entity.PsychTestResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PsychTestResponseRepository extends JpaRepository<PsychTestResponse, UUID> {

    List<PsychTestResponse> findAllByAssignmentIdOrderByItemNoAsc(UUID assignmentId);

    List<PsychTestResponse> findAllByAssignmentIdIn(java.util.Collection<UUID> assignmentIds);

    void deleteAllByAssignmentId(UUID assignmentId);
}
