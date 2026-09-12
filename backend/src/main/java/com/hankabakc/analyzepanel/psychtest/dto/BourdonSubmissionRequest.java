package com.hankabakc.analyzepanel.psychtest.dto;

import java.util.List;

public record BourdonSubmissionRequest(
        List<BourdonMarkedCell> markedCells
) {
}
