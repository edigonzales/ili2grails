package ch.interlis.generator.grails.runtime.api.persistence;

/**
 * Outcome of a pessimistic lock attempt.
 */
public enum LockStatus {
    LOCKED,
    NOT_FOUND,
    LOCK_UNSUPPORTED,
    LOCK_FAILED
}
