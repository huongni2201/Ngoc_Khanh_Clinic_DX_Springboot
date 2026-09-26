package com.ngockhanh.clinic.shared.infrastructure.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Instant.class)
public final class InstantTimestampTypeHandler extends BaseTypeHandler<Instant> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Instant value, JdbcType jdbcType)
            throws SQLException {
        statement.setObject(index, value.atOffset(ZoneOffset.UTC));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return toInstant(resultSet.getObject(columnName, OffsetDateTime.class));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return toInstant(resultSet.getObject(columnIndex, OffsetDateTime.class));
    }

    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return toInstant(statement.getObject(columnIndex, OffsetDateTime.class));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
