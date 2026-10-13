package com.mebelsex.license;

import com.mebelsex.settings.Setting;
import jakarta.annotation.PostConstruct;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PreDeleteEvent;
import org.hibernate.event.spi.PreDeleteEventListener;
import org.hibernate.event.spi.PreInsertEvent;
import org.hibernate.event.spi.PreInsertEventListener;
import org.hibernate.event.spi.PreUpdateEvent;
import org.hibernate.event.spi.PreUpdateEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;

/**
 * Blocks every business write once the license is READ_ONLY or BLOCKED by asking
 * the {@link LicenseGate} from Hibernate's PreInsert/PreUpdate/PreDelete listeners.
 * {@link Setting} rows stay writable in every state (theme, window geometry,
 * backup status, license last-seen), so the app can still report and renew.
 */
@Component
public class LicenseWriteGuard {

    private static final Logger log = LoggerFactory.getLogger(LicenseWriteGuard.class);

    private final EntityManagerFactory entityManagerFactory;
    private final LicenseGate gate;

    public LicenseWriteGuard(EntityManagerFactory entityManagerFactory, LicenseGate gate) {
        this.entityManagerFactory = entityManagerFactory;
        this.gate = gate;
    }

    @PostConstruct
    public void register() {
        try {
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            SessionFactoryImplementor implementor = sessionFactory.unwrap(SessionFactoryImplementor.class);
            EventListenerRegistry registry = implementor.getServiceRegistry()
                    .getService(EventListenerRegistry.class);
            if (registry == null) {
                log.warn("License write guard: no EventListenerRegistry available");
                return;
            }
            registry.appendListeners(EventType.PRE_INSERT, (PreInsertEventListener) this::onPreInsert);
            registry.appendListeners(EventType.PRE_UPDATE, (PreUpdateEventListener) this::onPreUpdate);
            registry.appendListeners(EventType.PRE_DELETE, (PreDeleteEventListener) this::onPreDelete);
        } catch (RuntimeException e) {
            log.warn("License write guard not registered: {}", e.getMessage());
        }
    }

    private boolean onPreInsert(PreInsertEvent event) {
        return guard(event.getEntity());
    }

    private boolean onPreUpdate(PreUpdateEvent event) {
        return guard(event.getEntity());
    }

    private boolean onPreDelete(PreDeleteEvent event) {
        return guard(event.getEntity());
    }

    private boolean guard(Object entity) {
        if (entity instanceof Setting) {
            return false;
        }
        gate.requireWritable();
        return false;
    }
}
