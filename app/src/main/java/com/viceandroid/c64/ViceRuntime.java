package com.viceandroid.c64;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ViceRuntime {
    private static final String TAG = "C64Runtime";
    private static final int MONITOR_PORT = 6510;
    private static final String MONITOR_ADDRESS = "ip4://127.0.0.1:" + MONITOR_PORT;

    private final Context context;
    private final File viceDataDir;
    private final File executable;
    private final File logFile;

    private Process process;
    private Socket monitorSocket;
    private BufferedWriter monitorWriter;
    private String lastCommandLine = "";
    private Integer lastExitCode;

    ViceRuntime(Context context, File viceDataDir) {
        this.context = context.getApplicationContext();
        this.viceDataDir = viceDataDir;
        this.executable = new File(this.context.getApplicationInfo().nativeLibraryDir, "libx64sc.so");
        this.logFile = new File(this.context.getCacheDir(), "x64sc.log");
    }

    synchronized boolean hasExecutable() {
        return executable.isFile() && executable.canExecute();
    }

    synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    synchronized String launch(String mediaPath, ViceNative.MediaType mediaType, boolean tapeAutostart)
            throws IOException {
        if (!hasExecutable()) {
            throw new IOException("C64 executable missing: " + executable.getAbsolutePath());
        }
        stopLocked();
        if (logFile.isFile() && !logFile.delete()) {
            Log.w(TAG, "Could not delete old log: " + logFile.getAbsolutePath());
        }

        List<String> args = buildArguments(mediaPath, mediaType, tapeAutostart);
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(viceDataDir);
        builder.redirectErrorStream(true);
        Map<String, String> env = builder.environment();
        env.put("HOME", context.getFilesDir().getAbsolutePath());
        env.put("TMPDIR", context.getCacheDir().getAbsolutePath());
        env.put("VICE_DIRECTORY", viceDataDir.getAbsolutePath());
        env.put("LD_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir);

        process = builder.start();
        lastExitCode = null;
        lastCommandLine = joinForLog(args);
        startLogThread(process);
        startExitWatchThread(process);
        Log.i(TAG, "Started " + lastCommandLine);
        try {
            Thread.sleep(250);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (!process.isAlive()) {
            throw new IOException("x64sc exited during startup"
                    + (lastExitCode == null ? "" : " with code " + lastExitCode));
        }
        return lastCommandLine;
    }

    synchronized void reset() {
        sendMonitorCommandLocked("reset");
    }

    synchronized void tapeCommand(ViceNative.TapeCommand command) {
        sendMonitorCommandLocked("tapectrl " + command.nativeCommand);
    }

    synchronized void stop() {
        stopLocked();
    }

    private List<String> buildArguments(String mediaPath, ViceNative.MediaType mediaType, boolean tapeAutostart) {
        List<String> args = new ArrayList<>();
        args.add(executable.getAbsolutePath());
        args.add("-directory");
        args.add(viceDataDir.getAbsolutePath());
        args.add("-logfile");
        args.add(logFile.getAbsolutePath());
        args.add("-remotemonitor");
        args.add("-remotemonitoraddress");
        args.add(MONITOR_ADDRESS);

        switch (mediaType) {
            case PRG:
                args.add("-autostartprgmode");
                args.add("1");
                args.add("-autostart");
                args.add(mediaPath);
                break;
            case DISK:
                args.add("-8");
                args.add(mediaPath);
                args.add("-autostart");
                args.add(mediaPath);
                break;
            case TAPE:
                if (tapeAutostart) {
                    args.add("-tapebasicload");
                    args.add("-autostart");
                    args.add(mediaPath);
                } else {
                    args.add("-1");
                    args.add(mediaPath);
                }
                break;
            case CARTRIDGE:
                args.add("-cartcrt");
                args.add(mediaPath);
                break;
            default:
                args.add("-autostart");
                args.add(mediaPath);
                break;
        }
        return args;
    }

    private void sendMonitorCommandLocked(String command) {
        if (process == null || !process.isAlive()) {
            Log.w(TAG, "Ignoring monitor command with no running x64sc: " + command);
            return;
        }
        try {
            ensureMonitorConnectionLocked();
            monitorWriter.write(command);
            monitorWriter.write('\n');
            monitorWriter.write("exit\n");
            monitorWriter.flush();
            Log.i(TAG, "Monitor command: " + command);
        } catch (IOException ex) {
            closeMonitorLocked();
            Log.e(TAG, "Monitor command failed: " + command, ex);
        }
    }

    private void ensureMonitorConnectionLocked() throws IOException {
        if (monitorSocket != null && monitorSocket.isConnected() && !monitorSocket.isClosed()) {
            return;
        }
        IOException last = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("127.0.0.1", MONITOR_PORT), 200);
                monitorSocket = socket;
                monitorWriter = new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8));
                return;
            } catch (IOException ex) {
                last = ex;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for C64 monitor", interrupted);
                }
            }
        }
        throw last == null ? new IOException("C64 monitor unavailable") : last;
    }

    private void stopLocked() {
        closeMonitorLocked();
        if (process != null) {
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            process = null;
        }
    }

    private void closeMonitorLocked() {
        if (monitorSocket != null) {
            try {
                monitorSocket.close();
            } catch (IOException ignored) {
            }
            monitorSocket = null;
            monitorWriter = null;
        }
    }

    private void startLogThread(Process runningProcess) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    runningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.i(TAG, line);
                }
            } catch (IOException ex) {
                Log.d(TAG, "x64sc log stream closed");
            }
        }, "ViceX64scLog");
        thread.setDaemon(true);
        thread.start();
    }

    private void startExitWatchThread(Process runningProcess) {
        Thread thread = new Thread(() -> {
            try {
                int code = runningProcess.waitFor();
                synchronized (ViceRuntime.this) {
                    if (process == runningProcess) {
                        lastExitCode = code;
                    }
                }
                Log.w(TAG, "x64sc exited with code " + code);
                logViceFile();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "ViceX64scExitWatch");
        thread.setDaemon(true);
        thread.start();
    }

    private void logViceFile() {
        if (!logFile.isFile()) {
            Log.w(TAG, "C64 logfile was not created: " + logFile.getAbsolutePath());
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(logFile), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > 80) {
                    lines.remove(0);
                }
            }
            for (String out : lines) {
                Log.i(TAG, "x64sc.log: " + out);
            }
        } catch (IOException ex) {
            Log.e(TAG, "Could not read C64 logfile", ex);
        }
    }

    private static String joinForLog(List<String> args) {
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            if (out.length() > 0) {
                out.append(' ');
            }
            if (arg.indexOf(' ') >= 0 || arg.indexOf('"') >= 0) {
                out.append('"').append(arg.replace("\"", "\\\"")).append('"');
            } else {
                out.append(arg);
            }
        }
        return out.toString();
    }

    @Override
    public synchronized String toString() {
        return String.format(Locale.US, "%s running=%s command=%s",
                executable.getAbsolutePath(), isRunning(), lastCommandLine);
    }
}
