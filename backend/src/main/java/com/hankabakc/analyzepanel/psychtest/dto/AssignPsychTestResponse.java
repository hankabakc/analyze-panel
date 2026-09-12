package com.hankabakc.analyzepanel.psychtest.dto;

import java.util.List;
import java.util.UUID;

/**
 * AssignPsychTestResponse: Test atama sonucunu, atanan ve atlanan öğrencileri bildirir.
 */
public record AssignPsychTestResponse(
        List<UUID> assignedStudentIds,
        List<UUID> skippedStudentIds,
        String message
) {
}
