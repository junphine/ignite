/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.ignite.internal.processors.cache.persistence.pagemem;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.processors.cache.persistence.CheckpointLockStateChecker;
import org.apache.ignite.internal.processors.cache.persistence.checkpoint.CheckpointProgress;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteOutClosure;

/**
 * Throttles threads that generate dirty pages during ongoing checkpoint.
 * Designed to avoid zero dropdowns that can happen if checkpoint buffer is overflowed.
 */
public class PagesWriteThrottle extends AbstractPagesWriteThrottle {
    /** If true, throttle will only protect from checkpoint buffer overflow, not from dirty pages ratio cap excess. */
    private final boolean throttleOnlyPagesInCheckpoint;

    /** Not-in-checkpoint protection logic. */
    private final ExponentialBackoffThrottlingStrategy notInCheckpointProtection
        = new ExponentialBackoffThrottlingStrategy();

    /** Threads that are throttled due to checkpoint buffer overflow. */
    private final ConcurrentHashMap<Long, Thread> cpBufThrottledThreads = new ConcurrentHashMap<>();

    /**
     * @param pageMemory Page memory.
     * @param cpProgress Database manager.
     * @param cpLockStateChecker Checkpoint lock state checker.
     * @param throttleOnlyPagesInCheckpoint If true, throttle will only protect from checkpoint buffer overflow.
     * @param fillRateBasedCpBufProtection If true, fill rate based throttling will be used to protect from
     *        checkpoint buffer overflow.
     * @param log Logger.
     */
    public PagesWriteThrottle(PageMemoryImpl pageMemory,
        IgniteOutClosure<CheckpointProgress> cpProgress,
        CheckpointLockStateChecker cpLockStateChecker,
        boolean throttleOnlyPagesInCheckpoint,
        boolean fillRateBasedCpBufProtection,
        IgniteLogger log
    ) {
        super(pageMemory, cpProgress, cpLockStateChecker, fillRateBasedCpBufProtection, log);
        this.throttleOnlyPagesInCheckpoint = throttleOnlyPagesInCheckpoint;

        assert (throttleOnlyPagesInCheckpoint && !fillRateBasedCpBufProtection) || cpProgress != null
                : "cpProgress must be not null if ratio based throttling mode is used";
    }

    /** {@inheritDoc} */
    @Override public void onMarkDirty(boolean isPageInCheckpoint) {
        assert cpLockStateChecker.checkpointLockIsHeldByThread();

        boolean shouldThrottle = false;

        if (isPageInCheckpoint)
            shouldThrottle = cpBufWatchdog.isInThrottlingZone();

        if (!shouldThrottle && !throttleOnlyPagesInCheckpoint) {
            CheckpointProgress progress = cpProgress.apply();

            AtomicInteger writtenPagesCntr = progress == null ? null : progress.writtenPagesCounter();
            AtomicInteger writtenRecoveryPagesCntr = progress == null ? null : progress.writtenRecoveryPagesCounter();

            if (progress == null || writtenPagesCntr == null || writtenRecoveryPagesCntr == null)
                return; // Don't throttle if checkpoint is not running.

            int cpWrittenRecoveryPages = writtenRecoveryPagesCntr.get();
            int cpWrittenPages = writtenPagesCntr.get();

            int cpTotalPages = progress.currentCheckpointPagesCount();

            if (cpWrittenPages == cpTotalPages) {
                // Checkpoint is already in fsync stage, increasing maximum ratio of dirty pages to 3/4
                shouldThrottle = pageMemory.shouldThrottle(3.0 / 4);
            }
            else {
                double dirtyRatioThreshold = cpWrittenRecoveryPages == 0 ? ((double)cpWrittenPages) / cpTotalPages :
                    (cpWrittenRecoveryPages + cpWrittenPages) / 2d / cpTotalPages;

                // Starting with 0.05 to avoid throttle right after checkpoint start
                // 7/12 is maximum ratio of dirty pages
                dirtyRatioThreshold = (dirtyRatioThreshold * 0.95 + 0.05) * 7 / 12;

                shouldThrottle = pageMemory.shouldThrottle(dirtyRatioThreshold);
            }
        }

        ThrottlingStrategy exponentialThrottle = isPageInCheckpoint ? cpBufProtector : notInCheckpointProtection;

        if (shouldThrottle) {
            long throttleParkTimeNs = exponentialThrottle.protectionParkTime();

            if (throttleParkTimeNs == 0)
                return;

            Thread curThread = Thread.currentThread();

            if (throttleParkTimeNs > LOGGING_THRESHOLD) {
                U.warn(log, "Parking thread=" + curThread.getName()
                    + " for timeout(ms)=" + (throttleParkTimeNs / 1_000_000));
            }

            long startTime = U.currentTimeMillis();

            if (isPageInCheckpoint) {
                cpBufThrottledThreads.put(curThread.getId(), curThread);

                try {
                    LockSupport.parkNanos(throttleParkTimeNs);
                }
                finally {
                    cpBufThrottledThreads.remove(curThread.getId());

                    if (throttleParkTimeNs > LOGGING_THRESHOLD) {
                        U.warn(log, "Unparking thread=" + curThread.getName()
                            + " with park timeout(ms)=" + (throttleParkTimeNs / 1_000_000));
                    }
                }
            }
            else
                LockSupport.parkNanos(throttleParkTimeNs);

            pageMemory.metrics().addThrottlingTime(U.currentTimeMillis() - startTime);
        }
        else {
            boolean backoffWasAlreadyStarted = exponentialThrottle.reset();

            if (isPageInCheckpoint && backoffWasAlreadyStarted)
                unparkParkedThreads();
        }
    }

    /** {@inheritDoc} */
    @Override public void wakeupThrottledThreads() {
        if (!cpBufWatchdog.isInThrottlingZone()) {
            cpBufProtector.reset();

            unparkParkedThreads();
        }
    }

    /**
     * Unparks all the threads that were parked by us.
     */
    private void unparkParkedThreads() {
        cpBufThrottledThreads.values().forEach(LockSupport::unpark);
    }

    /** {@inheritDoc} */
    @Override public void onBeginCheckpoint() {
    }

    /** {@inheritDoc} */
    @Override public void onFinishCheckpoint() {
        cpBufProtector.reset();
        notInCheckpointProtection.reset();
    }
}
