package com.hankabakc.analyzepanel.psychtest.dto;

import java.util.List;
import java.util.UUID;

/**
 * AssignPsychTestRequest: Yöneticinin öğrencilere test atama isteği.
 */
public record AssignPsychTestRequest(
        String testCode,
        List<UUID> studentIds
) {
}
