package ch.interlis.generator.grails.runtime.api.persistence;

/**
 * Typed result of a lock attempt. Only {@link LockStatus#LOCK_UNSUPPORTED}
 * may fall back to a plain read; unexpected lock failures must never be
 * swallowed silently.
 */
public record LockResult(
    Object record,
    LockStatus status,
    Throwable failure
) {

    public static LockResult locked(Object record) {
        return new LockResult(record, LockStatus.LOCKED, null);
    }

    public static LockResult notFound() {
        return new LockResult(null, LockStatus.NOT_FOUND, null);
    }

    public static LockResult unsupported() {
        return new LockResult(null, LockStatus.LOCK_UNSUPPORTED, null);
    }

    public static LockResult failed(Throwable failure) {
        return new LockResult(null, LockStatus.LOCK_FAILED, failure);
    }
}
