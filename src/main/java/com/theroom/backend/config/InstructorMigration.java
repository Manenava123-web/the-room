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
        migrarEspecialidadesInstructor();
        resetearCupoTomado();
    }

    private void migrarEspecialidadesInstructor() {
        try {
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
            log.debug("InstructorMigration: sin datos de especialidad que migrar ({})", e.getMessage());
        }
    }

    /**
     * cupo_tomado era un contador global acumulativo que nunca se reseteaba entre semanas.
     * La disponibilidad ahora se calcula en tiempo real desde reservaciones confirmadas por fecha,
     * así que el campo se zeroes-out para eliminar valores inflados de prod.
     */
    private void resetearCupoTomado() {
        try {
            int updated = em.createNativeQuery("UPDATE clases SET cupo_tomado = 0").executeUpdate();
            log.info("InstructorMigration: cupo_tomado reseteado en {} clase(s)", updated);
        } catch (Exception e) {
            log.warn("InstructorMigration: no se pudo resetear cupo_tomado ({})", e.getMessage());
        }
    }
}
