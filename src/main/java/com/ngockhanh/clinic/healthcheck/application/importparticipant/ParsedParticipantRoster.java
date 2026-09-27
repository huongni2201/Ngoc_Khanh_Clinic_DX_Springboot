package com.ngockhanh.clinic.healthcheck.application.importparticipant;

import java.util.List;

public record ParsedParticipantRoster(List<RawParticipantImportRow> rows) {
    public ParsedParticipantRoster {
        rows = List.copyOf(rows);
    }
}
