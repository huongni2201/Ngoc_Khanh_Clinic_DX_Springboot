package com.ngockhanh.clinic.healthcheck.application.port;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParsedParticipantRoster;

public interface ParticipantRosterWorkbookReader {
    ParsedParticipantRoster read(byte[] content);
}
