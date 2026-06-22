package com.viceandroid.c64;

import android.content.Context;

import java.io.File;
import java.io.IOException;

final class C64Runtime {
    private final ViceRuntime delegate;

    C64Runtime(Context context, File c64DataDir) {
        this.delegate = new ViceRuntime(context, c64DataDir);
    }

    synchronized boolean hasExecutable() {
        return delegate.hasExecutable();
    }

    synchronized boolean isRunning() {
        return delegate.isRunning();
    }

    synchronized String launch(String mediaPath, C64Native.MediaType mediaType, boolean tapeAutostart)
            throws IOException {
        return delegate.launch(mediaPath, mediaType.delegate, tapeAutostart);
    }

    synchronized void reset() {
        delegate.reset();
    }

    synchronized void tapeCommand(C64Native.TapeCommand command) {
        delegate.tapeCommand(command.delegate);
    }

    synchronized void stop() {
        delegate.stop();
    }
}
