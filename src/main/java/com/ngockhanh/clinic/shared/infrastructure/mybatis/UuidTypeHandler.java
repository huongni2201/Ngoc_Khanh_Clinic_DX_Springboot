package com.ngockhanh.clinic.shared.infrastructure.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.OTHER)
public final class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(
            PreparedStatement ps,
            int i,
            UUID parameter,
            JdbcType jdbcType
    ) throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public UUID getNullableResult(
            ResultSet rs,
            String columnName
    ) throws SQLException {
        return getUuid(rs.getObject(columnName));
    }

    @Override
    public UUID getNullableResult(
            ResultSet rs,
            int columnIndex
    ) throws SQLException {
        return getUuid(rs.getObject(columnIndex));
    }

    @Override
    public UUID getNullableResult(
            CallableStatement cs,
            int columnIndex
    ) throws SQLException {
        return getUuid(cs.getObject(columnIndex));
    }

    private UUID getUuid(Object value) throws SQLException {
        if (value == null) {
            return null;
        }

        if (value instanceof UUID uuid) {
            return uuid;
        }

        if (value instanceof String stringValue) {
            try {
                return UUID.fromString(stringValue);
            } catch (IllegalArgumentException ex) {
                throw new SQLException(
                        "Invalid UUID value returned from database: " + stringValue,
                        ex
                );
            }
        }

        throw new SQLException(
                "Unsupported UUID JDBC value type: " + value.getClass().getName()
        );
    }
}
