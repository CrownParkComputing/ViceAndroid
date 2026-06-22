package com.viceandroid.c64;

import android.util.Log;
import android.view.Surface;

import java.io.File;

final class C64Native {
    private static final String TAG = "C64Native";

    private C64Native() {
    }

    static void initialize(File dataDir) {
        ViceNative.initialize(dataDir);
        Log.i(TAG, "C64 native bridge initialized");
    }

    static void setSurface(Surface surface) {
        ViceNative.setSurface(surface);
    }

    static void setDisplayOptions(int aspectMode, boolean crtEnabled, boolean bezelEnabled) {
        ViceNative.setDisplayOptions(aspectMode, crtEnabled, bezelEnabled);
    }

    static boolean hasRealCore() {
        return ViceNative.hasRealCore();
    }

    static int getFps() {
        return ViceNative.getFps();
    }

    static String launch(String mediaPath, MediaType mediaType) {
        return ViceNative.launch(mediaPath, mediaType.delegate, true);
    }

    static String launch(String mediaPath, MediaType mediaType, boolean tapeAutostart) {
        return ViceNative.launch(mediaPath, mediaType.delegate, tapeAutostart);
    }

    static String launch(String mediaPath, MediaType mediaType, boolean tapeAutostart,
                         boolean autoloadOnly) {
        return ViceNative.launch(mediaPath, mediaType.delegate, tapeAutostart, autoloadOnly);
    }

    static String launch(String mediaPath, MediaType mediaType, boolean tapeAutostart,
                         boolean autoloadOnly, String postLoadCommand) {
        return ViceNative.launch(mediaPath, mediaType.delegate, tapeAutostart, autoloadOnly, postLoadCommand);
    }

    static void reset() {
        ViceNative.reset();
    }

    static void setPaused(boolean paused) {
        ViceNative.setPaused(paused);
    }

    static void setJoystick(int port, int mask) {
        ViceNative.setJoystick(port, mask);
    }

    static void setKey(C64Key key, boolean pressed) {
        ViceNative.setKey(key.delegate, pressed);
    }

    static void setMatrixKey(int row, int column, boolean pressed) {
        ViceNative.setMatrixKey(row, column, pressed);
    }

    static void tapeCommand(TapeCommand command) {
        ViceNative.tapeCommand(command.delegate);
    }

    static void setTapeTurbo(boolean enabled) {
        ViceNative.setTapeTurbo(enabled);
    }

    static String getViceResource(String name) {
        return ViceNative.getViceResource(name);
    }

    static boolean setViceResourceInt(String name, int value) {
        return ViceNative.setViceResourceInt(name, value);
    }

    static boolean setViceResourceString(String name, String value) {
        return ViceNative.setViceResourceString(name, value);
    }

    static void feedKeyboard(String text) {
        ViceNative.feedKeyboard(text);
    }

    enum C64Key {
        SPACE(ViceNative.C64Key.SPACE),
        RUN_STOP(ViceNative.C64Key.RUN_STOP),
        RETURN(ViceNative.C64Key.RETURN),
        F1(ViceNative.C64Key.F1),
        F3(ViceNative.C64Key.F3),
        F5(ViceNative.C64Key.F5),
        F7(ViceNative.C64Key.F7);

        final ViceNative.C64Key delegate;

        C64Key(ViceNative.C64Key delegate) {
            this.delegate = delegate;
        }
    }

    enum TapeCommand {
        PLAY(ViceNative.TapeCommand.PLAY),
        STOP(ViceNative.TapeCommand.STOP),
        FORWARD(ViceNative.TapeCommand.FORWARD),
        REWIND(ViceNative.TapeCommand.REWIND),
        RECORD(ViceNative.TapeCommand.RECORD),
        RESET(ViceNative.TapeCommand.RESET),
        RESET_COUNTER(ViceNative.TapeCommand.RESET_COUNTER);

        final ViceNative.TapeCommand delegate;

        TapeCommand(ViceNative.TapeCommand delegate) {
            this.delegate = delegate;
        }
    }

    enum MediaType {
        PRG(ViceNative.MediaType.PRG),
        DISK(ViceNative.MediaType.DISK),
        TAPE(ViceNative.MediaType.TAPE),
        CARTRIDGE(ViceNative.MediaType.CARTRIDGE),
        UNKNOWN(ViceNative.MediaType.UNKNOWN);

        final ViceNative.MediaType delegate;

        MediaType(ViceNative.MediaType delegate) {
            this.delegate = delegate;
        }
    }
}
