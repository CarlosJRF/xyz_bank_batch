package com.example.demo.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.demo.config.XyzBatchProperties;
import com.example.demo.validation.ErrorCode;
import com.example.demo.validation.InvalidRecordException;

class CustomSkipPoliciesTest {

    private final CustomSkipPolicies policy =
            new CustomSkipPolicies(new XyzBatchProperties(null, 100, 2, 10, 3, true, 18, 99));

    @Test
    void skipsDataErrors() {
        assertThat(policy.shouldSkip(new InvalidRecordException(List.of(ErrorCode.ZERO_AMOUNT)), 0)).isTrue();
        assertThat(policy.shouldSkip(new FlatFileParseException("bad", "a,b", 3), 0)).isTrue();
        assertThat(policy.shouldSkip(new DataIntegrityViolationException("dup"), 0)).isTrue();
    }

    @Test
    void doesNotSkipInfrastructureOrProgrammingErrors() {
        assertThat(policy.shouldSkip(new CannotAcquireLockException("lock"), 0)).isFalse();
        assertThat(policy.shouldSkip(new NullPointerException(), 0)).isFalse();
        // un error no omitible nunca se reporta como "límite excedido"
        assertThat(policy.shouldSkip(new IllegalStateException(), 50)).isFalse();
    }

    @Test
    void abortsWhenSkipLimitIsReached() {
        assertThatThrownBy(() -> policy.shouldSkip(new InvalidRecordException(List.of(ErrorCode.ZERO_AMOUNT)), 10))
                .isInstanceOf(SkipLimitExceededException.class);
    }
}
