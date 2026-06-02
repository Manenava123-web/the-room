package com.theroom.backend.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time migration: copies the legacy `instructores.especialidad` VARCHAR column
 * to the new `instructor_especialidades` join table introduced when the field
 * became a @ElementCollection Set<TipoClase>.
 *
 * Idempotent — uses INSERT IGNORE so it is safe to run on every startup.
 * Will log 0 rows once all instructors have been migrated.
 */
@Component
@Slf4j
public class InstructorMigration {

    @PersistenceContext
    private EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrarEspecialidades() {
        try {
            // Only proceeds if the legacy column still exists (safe on fresh DBs too).
            int migrated = em.createNativeQuery(
                    "INSERT IGNORE INTO instructor_especialidades (instructor_id, especialidad) " +
                    "SELECT id, especialidad FROM instructores " +
                    "WHERE especialidad IS NOT NULL " +
                    "AND id NOT IN (SELECT DISTINCT instructor_id FROM instructor_especialidades)"
            ).executeUpdate();

            if (migrated > 0) {
                log.info("InstructorMigration: {} instructor(es) migrados de especialidad a instructor_especialidades", migrated);
            }
        } catch (Exception e) {
            // Column may not exist on a fresh DB — not an error.
            log.debug("InstructorMigration: sin datos que migrar ({})", e.getMessage());
        }
    }
}
