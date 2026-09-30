package com.ngockhanh.clinic.healthexamination.domain.valueobject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;

public final class ParticipantImportColumnMapping {
    private final Map<ParticipantImportField, Integer> columns;

    private ParticipantImportColumnMapping(Map<ParticipantImportField, Integer> columns) {
        this.columns = Collections.unmodifiableMap(columns);
    }

    public static ParticipantImportColumnMapping of(Map<ParticipantImportField, Integer> columns) {
        if (columns == null) throw new IllegalArgumentException("Column mapping is required");

        EnumMap<ParticipantImportField, Integer> copy = new EnumMap<>(ParticipantImportField.class);
        Set<Integer> sourceColumns = new HashSet<>();
        for (Map.Entry<ParticipantImportField, Integer> entry : columns.entrySet()) {
            ParticipantImportField field = entry.getKey();
            Integer sourceColumn = entry.getValue();
            if (field == null || sourceColumn == null || sourceColumn < 0 || !sourceColumns.add(sourceColumn)) {
                throw new IllegalArgumentException("Invalid column mapping");
            }
            copy.put(field, sourceColumn);
        }

        for (ParticipantImportField field : ParticipantImportField.values()) {
            if (field.required() && !copy.containsKey(field)) {
                throw new IllegalArgumentException("Required import column is not mapped");
            }
        }
        return new ParticipantImportColumnMapping(copy);
    }

    public Integer sourceColumn(ParticipantImportField field) {
        return columns.get(field);
    }

    public Map<ParticipantImportField, Integer> columns() {
        return columns;
    }
}
