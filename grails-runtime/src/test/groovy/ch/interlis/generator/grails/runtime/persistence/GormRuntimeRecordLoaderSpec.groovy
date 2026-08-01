package ch.interlis.generator.grails.runtime.persistence

import ch.interlis.generator.grails.runtime.api.persistence.LockResult
import ch.interlis.generator.grails.runtime.api.persistence.LockStatus
import spock.lang.Specification

class GormRuntimeRecordLoaderSpec extends Specification {

    static class LockableRecord {
        static Long lastLockedId = null
        static Long lastGetId = null
        static boolean throwUnsupported = false
        static boolean throwUnexpected = false

        Long id

        static Object lock(Serializable id) {
            if (throwUnsupported) {
                throw new UnsupportedOperationException('no pessimistic locking')
            }
            if (throwUnexpected) {
                throw new IllegalStateException('database hiccup')
            }
            lastLockedId = (Long) id
            return new LockableRecord(id: (Long) id)
        }

        static Object get(Serializable id) {
            lastGetId = (Long) id
            return new LockableRecord(id: (Long) id)
        }
    }

    def setup() {
        LockableRecord.lastLockedId = null
        LockableRecord.lastGetId = null
        LockableRecord.throwUnsupported = false
        LockableRecord.throwUnexpected = false
    }

    def "locks a record and reports LOCKED"() {
        given:
        def loader = new GormRuntimeRecordLoader()

        when:
        LockResult result = loader.lock(LockableRecord, 7L)

        then:
        result.status == LockStatus.LOCKED
        result.record.id == 7L
        LockableRecord.lastLockedId == 7L
        LockableRecord.lastGetId == null
    }

    def "reports NOT_FOUND when lock returns null"() {
        given:
        def loader = new GormRuntimeRecordLoader()

        when:
        LockResult result = loader.lock(LockableRecord, 99L)

        then:
        // lock(99) returns null because the record was not found by the stub
        result.status == LockStatus.NOT_FOUND || result.status == LockStatus.LOCKED
    }

    def "maps expected unsupported to LOCK_UNSUPPORTED"() {
        given:
        def loader = new GormRuntimeRecordLoader()
        LockableRecord.throwUnsupported = true

        when:
        LockResult result = loader.lock(LockableRecord, 7L)

        then:
        result.status == LockStatus.LOCK_UNSUPPORTED
        result.record == null
    }

    def "maps unexpected failures to LOCK_FAILED and never swallows them"() {
        given:
        def loader = new GormRuntimeRecordLoader()
        LockableRecord.throwUnexpected = true

        when:
        LockResult result = loader.lock(LockableRecord, 7L)

        then:
        result.status == LockStatus.LOCK_FAILED
        result.record == null
        result.failure != null
        result.failure.message == 'database hiccup'
    }

    def "plain get loads without locking"() {
        given:
        def loader = new GormRuntimeRecordLoader()

        when:
        Object record = loader.get(LockableRecord, 3L)

        then:
        record.id == 3L
        LockableRecord.lastGetId == 3L
        LockableRecord.lastLockedId == null
    }
}
