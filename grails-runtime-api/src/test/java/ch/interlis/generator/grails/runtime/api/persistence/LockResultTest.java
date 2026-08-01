package ch.interlis.generator.grails.runtime.api.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LockResultTest {

    @Test
    void lockedCarriesRecord() {
        Object record = new Object();
        LockResult result = LockResult.locked(record);
        assertThat(result.status()).isEqualTo(LockStatus.LOCKED);
        assertThat(result.record()).isSameAs(record);
        assertThat(result.failure()).isNull();
    }

    @Test
    void failedCarriesFailureWithoutRecord() {
        RuntimeException failure = new RuntimeException("boom");
        LockResult result = LockResult.failed(failure);
        assertThat(result.status()).isEqualTo(LockStatus.LOCK_FAILED);
        assertThat(result.record()).isNull();
        assertThat(result.failure()).isSameAs(failure);
    }

    @Test
    void unsupportedIsExplicit() {
        assertThat(LockResult.unsupported().status()).isEqualTo(LockStatus.LOCK_UNSUPPORTED);
    }
}
