package ch.interlis.generator.grails.runtime.persistence

import ch.interlis.generator.grails.runtime.api.persistence.LockResult
import ch.interlis.generator.grails.runtime.api.persistence.LockStatus
import ch.interlis.generator.grails.runtime.api.persistence.RuntimeRecordLoader

/**
 * GORM-based record loader with explicit lock semantics.
 *
 * <p>Only expected {@code LOCK_UNSUPPORTED} outcomes may fall back to a plain
 * read; unexpected lock failures are reported as {@link LockStatus#LOCK_FAILED}
 * and are never swallowed silently.</p>
 */
final class GormRuntimeRecordLoader implements RuntimeRecordLoader {

    @Override
    Object get(Class<?> type, Serializable id) {
        if (type == null || id == null) {
            return null
        }
        return type.get(id)
    }

    @Override
    LockResult lock(Class<?> type, Serializable id) {
        if (type == null || id == null) {
            return LockResult.notFound()
        }
        try {
            Object locked = type.lock(id)
            return locked == null ? LockResult.notFound() : LockResult.locked(locked)
        } catch (MissingMethodException unsupported) {
            return LockResult.unsupported()
        } catch (UnsupportedOperationException unsupported) {
            return LockResult.unsupported()
        } catch (Exception failure) {
            return LockResult.failed(failure)
        }
    }
}
