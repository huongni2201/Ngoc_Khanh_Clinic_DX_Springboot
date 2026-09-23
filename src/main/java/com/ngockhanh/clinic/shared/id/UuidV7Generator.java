package com.ngockhanh.clinic.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7Generator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private long lastTimestamp = -1;
    private int sequence;

    public synchronized UUID next() {
        long timestamp = System.currentTimeMillis();
        if (timestamp <= lastTimestamp) {
            timestamp = lastTimestamp;
            sequence = (sequence + 1) & 0x0FFF;
            if (sequence == 0) timestamp++;
        } else {
            sequence = RANDOM.nextInt(1 << 12);
        }
        lastTimestamp = timestamp;
        long mostSignificantBits = (timestamp << 16) | 0x7000L | sequence;
        long leastSignificantBits = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
