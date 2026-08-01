package ch.interlis.generator.grails.runtime.api.persistence;

import java.io.Serializable;

/**
 * Loads and optionally locks domain records for write commands.
 *
 * <p>Implementations live in the runtime plugin and use GORM. Unexpected lock
 * failures are reported as {@link LockResult#failed(Throwable)} so callers can
 * decide; they must not silently downgrade to an unsafe read.</p>
 */
public interface RuntimeRecordLoader {

    Object get(Class<?> type, Serializable id);

    LockResult lock(Class<?> type, Serializable id);
}
