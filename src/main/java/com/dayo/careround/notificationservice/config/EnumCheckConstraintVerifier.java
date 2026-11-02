package com.dayo.careround.notificationservice.config;

import com.dayo.careround.notificationservice.data.models.enums.AlertSeverity;
import com.dayo.careround.notificationservice.data.models.enums.AlertType;
import com.dayo.careround.notificationservice.data.models.enums.EscalationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Hibernate writes a CHECK (col IN (...)) for each enum column when it creates a column, and ddl-auto=update never
// widens it. A database created before an enum gained values then rejects the new values at insert time. This
// check runs once at startup, logs ERROR with the exact SQL to run, and never blocks startup.
@Slf4j
@Component
@RequiredArgsConstructor
public class EnumCheckConstraintVerifier {

    private static final String TABLE = "alerts";

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        Map<String, Enum<?>[]> columns = new LinkedHashMap<>();
        columns.put("type", AlertType.values());
        columns.put("escalation_state", EscalationState.values());
        columns.put("severity", AlertSeverity.values());
        columns.forEach(this::verifyColumn);
    }

    private void verifyColumn(String column, Enum<?>[] values) {
        try {
            String name = TABLE + "_" + column + "_check";
            List<String> defs = jdbcTemplate.queryForList(
                    "select pg_get_constraintdef(c.oid) from pg_constraint c join pg_class t on t.oid = c.conrelid"
                            + " where t.relname = ? and c.conname = ? and c.contype = 'c'", String.class, TABLE, name);
            if (defs.isEmpty()) return; // no constraint: nothing to widen
            List<String> missing = Arrays.stream(values).map(Enum::name)
                    .filter(v -> !defs.get(0).contains("'" + v + "'")).toList();
            if (missing.isEmpty()) return;
            String all = Arrays.stream(values).map(v -> "'" + v.name() + "'").collect(Collectors.joining(", "));
            log.error("Database constraint {} does not allow {} {}: inserting those values will fail. Run once: "
                            + "ALTER TABLE {} DROP CONSTRAINT IF EXISTS {}, ADD CONSTRAINT {} CHECK ({} IN ({}));",
                    name, column, missing, TABLE, name, name, column, all);
        } catch (RuntimeException e) {
            log.warn("Could not verify the {}.{} check constraint: {}", TABLE, column, e.getMessage());
        }
    }
}
