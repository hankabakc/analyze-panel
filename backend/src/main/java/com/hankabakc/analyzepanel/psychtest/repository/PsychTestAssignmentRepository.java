package com.hankabakc.analyzepanel.psychtest.repository;

import com.hankabakc.analyzepanel.psychtest.entity.PsychTestAssignment;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PsychTestAssignmentRepository extends JpaRepository<PsychTestAssignment, UUID> {

    List<PsychTestAssignment> findAllByStudentIdOrderByAssignedAtDesc(UUID studentId);

    boolean existsByStudentIdAndTestCodeAndStatus(UUID studentId, String testCode, PsychTestStatus status);

    Optional<PsychTestAssignment> findByStudentIdAndTestCodeAndStatus(UUID studentId, String testCode, PsychTestStatus status);

    List<PsychTestAssignment> findAllByOrderByAssignedAtDesc();

    List<PsychTestAssignment> findAllByStudentIdInOrderByAssignedAtDesc(List<UUID> studentIds);

    List<PsychTestAssignment> findAllByStudentIdAndStatusOrderByAssignedAtDesc(UUID studentId, PsychTestStatus status);
}
