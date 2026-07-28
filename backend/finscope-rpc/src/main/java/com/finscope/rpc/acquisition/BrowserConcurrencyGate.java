package com.finscope.rpc.acquisition;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 将昂贵的浏览器进程限制在独立且有界的并发配额内。 */
final class BrowserConcurrencyGate {
    private final Semaphore permits;

    BrowserConcurrencyGate(int maxConcurrency) {
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("浏览器最大并发必须大于零");
        }
        this.permits = new Semaphore(maxConcurrency, true);
    }

    Permit acquire(int timeoutMs) {
        try {
            if (!permits.tryAcquire(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS)) {
                throw new AcquisitionException(AcquisitionErrorType.TIMEOUT,
                        "浏览器采集容量等待超时", true, null);
            }
            return new Permit(permits);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AcquisitionException(AcquisitionErrorType.CONNECTION_ERROR,
                    "浏览器采集容量等待被中断", false, null, error);
        }
    }

    int availablePermits() {
        return permits.availablePermits();
    }

    static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                semaphore.release();
            }
        }
    }
}
