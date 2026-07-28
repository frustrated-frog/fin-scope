package com.finscope.rpc.acquisition;

import java.util.Optional;

public final class AcquisitionContext {
    private static final ThreadLocal<Value> CURRENT = new ThreadLocal<Value>();

    private AcquisitionContext() {
    }

    public static Scope open(Long fetchRunId, Long sourceId) {
        Value previous = CURRENT.get();
        CURRENT.set(new Value(fetchRunId, sourceId));
        return new Scope(previous);
    }

    public static Optional<Value> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static final class Value {
        private final Long fetchRunId;
        private final Long sourceId;

        private Value(Long fetchRunId, Long sourceId) {
            this.fetchRunId = fetchRunId;
            this.sourceId = sourceId;
        }

        public Long getFetchRunId() {
            return fetchRunId;
        }

        public Long getSourceId() {
            return sourceId;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Value previous;
        private boolean closed;

        private Scope(Value previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
