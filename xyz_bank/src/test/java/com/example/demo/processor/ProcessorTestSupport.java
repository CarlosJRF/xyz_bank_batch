package com.example.demo.processor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import com.example.demo.config.XyzBatchProperties;
import com.example.demo.validation.ErrorCode;
import com.example.demo.validation.InvalidRecordException;

final class ProcessorTestSupport {

    private ProcessorTestSupport() {
    }

    static XyzBatchProperties properties() {
        return new XyzBatchProperties(null, 100, 2, 1000, 3, true, 18, 99);
    }

    static void assertRejected(ThrowingCallable call, ErrorCode... expected) {
        assertThatThrownBy(call)
                .isInstanceOf(InvalidRecordException.class)
                .extracting(e -> ((InvalidRecordException) e).getErrorCodes())
                .asList()
                .containsExactly((Object[]) expected);
    }
}
