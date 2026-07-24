package com.finscope.rpc.marketintel;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 将 Provider 截止时间传递到底层网络客户端和显式包装的异步任务。 */
public final class ProviderCallDeadline {
    private static final ThreadLocal<Deque<Long>> DEADLINES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private ProviderCallDeadline() { }

    public static Scope open(Duration timeout) {
        long now = System.nanoTime();
        long timeoutNanos = toPositiveNanos(timeout);
        long candidate = timeoutNanos >= Long.MAX_VALUE - now
                ? Long.MAX_VALUE : now + timeoutNanos;
        return openAt(candidate);
    }

    /** 捕获当前绝对截止时间，并显式传播到线程池任务。 */
    public static <T> Supplier<T> propagate(Supplier<T> operation) {
        Deque<Long> deadlines = DEADLINES.get();
        Long captured = deadlines.isEmpty() ? null : deadlines.peek();
        if (captured == null) return operation;
        return () -> {
            try (Scope ignored = openAt(captured)) {
                return operation.get();
            }
        };
    }

    private static Scope openAt(long candidate) {
        Deque<Long> deadlines = DEADLINES.get();
        long effective = deadlines.isEmpty() ? candidate
                : Math.min(candidate, deadlines.peek());
        deadlines.push(effective);
        return new Scope(deadlines);
    }

    /** 无截止时间时返回 Long.MAX_VALUE；有截止时间时向上取整到毫秒。 */
    public static long remainingMillis() {
        Deque<Long> deadlines = DEADLINES.get();
        if (deadlines.isEmpty()) return Long.MAX_VALUE;
        long remainingNanos = deadlines.peek() - System.nanoTime();
        if (remainingNanos <= 0L) return 0L;
        long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
        return millis > 0L ? millis : 1L;
    }

    private static long toPositiveNanos(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) return Long.MAX_VALUE;
        try {
            return Math.max(1L, timeout.toNanos());
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Deque<Long> deadlines;
        private boolean closed;

        private Scope(Deque<Long> deadlines) { this.deadlines = deadlines; }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            deadlines.pop();
            if (deadlines.isEmpty()) DEADLINES.remove();
        }
    }
}
