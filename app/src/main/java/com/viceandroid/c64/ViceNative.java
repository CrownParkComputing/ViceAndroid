package com.viceandroid.c64;

import android.util.Log;
import android.view.Surface;

import java.io.File;
final class ViceNative {
    private static final String TAG = "C64Native";
    private static String viceDataDir = "";
    private static boolean nativeLoaded;

    static {
        try {
            System.loadLibrary("viceandroid");
            nativeLoaded = true;
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "Could not load native bridge", error);
        }
    }

    private ViceNative() {
    }

    static void initialize(File dataDir) {
        viceDataDir = dataDir == null ? "" : dataDir.getAbsolutePath();
        Log.i(TAG, "C64 data dir " + viceDataDir);
        if (nativeLoaded) {
            nativeInitialize(viceDataDir);
        }
    }

    static void setSurface(Surface surface) {
        Log.i(TAG, "Surface " + (surface == null ? "detached" : "attached"));
        if (nativeLoaded) {
            nativeSetSurface(surface);
        }
    }

    static void setDisplayOptions(int aspectMode, boolean crtEnabled, boolean bezelEnabled) {
        if (nativeLoaded) {
            nativeSetDisplayOptions(aspectMode, crtEnabled, bezelEnabled);
        }
    }

    static boolean hasRealCore() {
        return nativeLoaded && nativeHasRealCore();
    }

    static int getFps() {
        return nativeLoaded ? nativeGetFps() : 0;
    }

    static String launch(String mediaPath, MediaType mediaType) {
        return launch(mediaPath, mediaType, true);
    }

    static String launch(String mediaPath, MediaType mediaType, boolean tapeAutostart) {
        return launch(mediaPath, mediaType, tapeAutostart, false);
    }

    static String launch(String mediaPath, MediaType mediaType, boolean tapeAutostart,
                         boolean autoloadOnly) {
        return launch(mediaPath, mediaType, tapeAutostart, autoloadOnly, null);
    }

    static String launch(String mediaPath, MediaType mediaType, boolean tapeAutostart,
                         boolean autoloadOnly, String postLoadCommand) {
        String command;
        String data = viceDataDir.isEmpty() ? "" : "-directory \"" + viceDataDir + "\" ";
        switch (mediaType) {
            case PRG:
                command = autoloadOnly
                        ? "-autostartprgmode 1 -autoload \"" + mediaPath + "\""
                        : "-autostartprgmode 1 -autostart \"" + mediaPath + "\"";
                break;
            case DISK:
                command = "-8 \"" + mediaPath + "\" -autostart \"" + mediaPath + "\"";
                break;
            case TAPE:
                command = tapeAutostart
                        ? "-tapebasicload -autostart \"" + mediaPath + "\""
                        : "-1 \"" + mediaPath + "\"";
                break;
            case CARTRIDGE:
                command = "-cartcrt \"" + mediaPath + "\"";
                break;
            default:
                command = "-autostart \"" + mediaPath + "\"";
                break;
        }
        String fullCommand = data + command;
        Log.i(TAG, "C64 pending native core: " + fullCommand);
        if (hasRealCore()) {
            nativeLaunch(mediaPath, mediaType.ordinal(), fullCommand);
        }
        return fullCommand;
    }

    static void reset() {
        Log.i(TAG, "Reset pending native core");
        if (nativeLoaded) {
            nativeReset();
        }
    }

    static void setPaused(boolean paused) {
        Log.i(TAG, "Pause " + paused);
        if (nativeLoaded) {
            nativeSetPaused(paused);
        }
    }

    static void setJoystick(int port, int mask) {
        Log.i(TAG, "Joystick port " + port + " mask " + mask);
        if (nativeLoaded) {
            nativeSetJoystick(port, mask);
        }
    }

    static void setKey(C64Key key, boolean pressed) {
        Log.i(TAG, "Key " + key + " " + (pressed ? "down" : "up"));
        if (nativeLoaded) {
            nativeSetKey(key.ordinal(), pressed);
        }
    }

    static void setMatrixKey(int row, int column, boolean pressed) {
        Log.i(TAG, "Matrix key " + row + "," + column + " " + (pressed ? "down" : "up"));
        if (nativeLoaded) {
            nativeSetMatrixKey(row, column, pressed);
        }
    }

    static void tapeCommand(TapeCommand command) {
        Log.i(TAG, "Tape " + command);
        if (nativeLoaded) {
            nativeTapeCommand(command.nativeCommand);
        }
    }

    static void setTapeTurbo(boolean enabled) {
        Log.i(TAG, "Tape turbo " + enabled);
        if (nativeLoaded) {
            nativeSetTapeTurbo(enabled);
        }
    }

    static String getViceResource(String name) {
        if (!nativeLoaded || name == null || name.isBlank()) {
            return "";
        }
        return nativeGetViceResource(name);
    }

    static boolean setViceResourceInt(String name, int value) {
        if (!nativeLoaded || name == null || name.isBlank()) {
            return false;
        }
        return nativeSetViceResourceInt(name, value);
    }

    static boolean setViceResourceString(String name, String value) {
        if (!nativeLoaded || name == null || name.isBlank()) {
            return false;
        }
        return nativeSetViceResourceString(name, value == null ? "" : value);
    }

    static void feedKeyboard(String text) {
        Log.i(TAG, "Keyboard feed " + (text == null ? 0 : text.length()));
        if (nativeLoaded && text != null && !text.isEmpty()) {
            nativeFeedKeyboard(text);
        }
    }

    private static native void nativeInitialize(String dataDir);
    private static native void nativeSetSurface(Surface surface);
    private static native void nativeSetDisplayOptions(int aspectMode, boolean crtEnabled, boolean bezelEnabled);
    private static native void nativeLaunch(String mediaPath, int mediaType, String commandLine);
    private static native void nativeReset();
    private static native void nativeSetPaused(boolean paused);
    private static native void nativeSetJoystick(int port, int mask);
    private static native void nativeSetKey(int key, boolean pressed);
    private static native void nativeSetMatrixKey(int row, int column, boolean pressed);
    private static native void nativeFeedKeyboard(String text);
    private static native void nativeTapeCommand(int command);
    private static native void nativeSetTapeTurbo(boolean enabled);
    private static native String nativeGetViceResource(String name);
    private static native boolean nativeSetViceResourceInt(String name, int value);
    private static native boolean nativeSetViceResourceString(String name, String value);
    private static native boolean nativeHasRealCore();
    private static native int nativeGetFps();

    enum C64Key {
        SPACE,
        RUN_STOP,
        RETURN,
        F1,
        F3,
        F5,
        F7
    }

    enum TapeCommand {
        PLAY(1),
        STOP(0),
        FORWARD(2),
        REWIND(3),
        RECORD(4),
        RESET(5),
        RESET_COUNTER(6);

        final int nativeCommand;

        TapeCommand(int nativeCommand) {
            this.nativeCommand = nativeCommand;
        }
    }

    enum MediaType {
        PRG,
        DISK,
        TAPE,
        CARTRIDGE,
        UNKNOWN
    }
}
