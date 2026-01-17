package com.fep.db.util;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Utility methods for JDBC operations.
 */
public final class JdbcUtils {

    private JdbcUtils() {
        // Utility class
    }

    /**
     * Gets a LocalDateTime from a ResultSet, handling nulls.
     */
    public static LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts != null ? ts.toLocalDateTime() : null;
    }

    /**
     * Gets a LocalDate from a ResultSet, handling nulls.
     */
    public static LocalDate getLocalDate(ResultSet rs, String columnName) throws SQLException {
        java.sql.Date date = rs.getDate(columnName);
        return date != null ? date.toLocalDate() : null;
    }

    /**
     * Gets a BigDecimal from a ResultSet, handling nulls.
     */
    public static BigDecimal getBigDecimal(ResultSet rs, String columnName) throws SQLException {
        BigDecimal value = rs.getBigDecimal(columnName);
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Gets a Long from a ResultSet, handling nulls.
     */
    public static Long getLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    /**
     * Gets an Integer from a ResultSet, handling nulls.
     */
    public static Integer getInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    /**
     * Gets a Boolean from a ResultSet (NUMBER(1) column), handling nulls.
     */
    public static Boolean getBoolean(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : (value == 1);
    }

    /**
     * Converts a LocalDateTime to Timestamp for SQL parameters.
     */
    public static Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime != null ? Timestamp.valueOf(dateTime) : null;
    }

    /**
     * Converts a LocalDate to sql.Date for SQL parameters.
     */
    public static java.sql.Date toSqlDate(LocalDate date) {
        return date != null ? java.sql.Date.valueOf(date) : null;
    }

    /**
     * Converts a Boolean to Integer (1/0) for Oracle NUMBER(1) columns.
     */
    public static Integer toInt(Boolean value) {
        return value != null ? (value ? 1 : 0) : null;
    }
}
