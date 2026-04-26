package org.test.magicmod.model;

import java.util.concurrent.atomic.AtomicInteger;

public final class GpuMeshBuffer implements AutoCloseable {
    private static final AtomicInteger LIVE_BUFFERS = new AtomicInteger();

    private boolean closed;

    public GpuMeshBuffer() {
        LIVE_BUFFERS.incrementAndGet();
    }

    public static int liveBufferCount() {
        return LIVE_BUFFERS.get();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        LIVE_BUFFERS.decrementAndGet();
    }
}
