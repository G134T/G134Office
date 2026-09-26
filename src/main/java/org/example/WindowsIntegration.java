package org.example;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import javax.swing.JOptionPane;

/** Windows startup support. Does not change firewall rules or require elevation. */
public final class WindowsIntegration {
    private static Path logFile;
    private static boolean initialized;

    private WindowsIntegration() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    static Path dataDirectory(String localAppData, String userHome) {
        return localAppData == null || localAppData.isBlank()
                ? Path.of(userHome, ".g134office")
                : Path.of(localAppData, "G134Office");
    }

    public static synchronized void initialize() {
        if (initialized || !isWindows()) return;
        initialized = true;
        try {
            Path root = dataDirectory(System.getenv("LOCALAPPDATA"), System.getProperty("user.home"));
            Path logs = Files.createDirectories(root.resolve("logs"));
            // One file per process: simultaneous launches must not overwrite each other's evidence.
            logFile = logs.resolve("startup-" + ProcessHandle.current().pid() + "-"
                    + System.currentTimeMillis() + ".log");
            PrintStream output = new PrintStream(Files.newOutputStream(logFile), true, StandardCharsets.UTF_8);
            System.setOut(output);
            System.setErr(output);
            System.err.println("G134Office startup " + Instant.now());
            System.err.println("Java: " + System.getProperty("java.runtime.version"));
            System.err.println("Runtime: " + System.getProperty("java.home"));
            System.err.println("Working directory: " + System.getProperty("user.dir"));
            System.err.println("OS: " + System.getProperty("os.name") + " / " + System.getProperty("os.arch"));
        } catch (IOException | RuntimeException failure) {
            System.err.println("Cannot initialize Windows startup log: " + failure);
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            System.err.println("Unhandled exception in " + thread.getName());
            error.printStackTrace(System.err);
        });
    }

    public static void reportStartupFailure(Throwable error) {
        error.printStackTrace(System.err);
        if (!isWindows()) return;
        String details = logFile == null ? "Не удалось создать журнал запуска."
                : "Подробности записаны в:\n" + logFile;
        String advice = "Запускайте EXE вместе с папками app, bin и runtime из установленного приложения.";
        if (isNativeLibraryFailure(error)) {
            advice = "Не удалось загрузить системную библиотеку приложения (DLL).\n"
                    + "Если в журнале указана блокировка политикой Windows, нужна сборка\n"
                    + "с доверенной цифровой подписью EXE и DLL. Правила брандмауэра это не исправляют.";
        }
        try {
            JOptionPane.showMessageDialog(null,
                    "Не удалось запустить G134Office.\n" + error + "\n\n" + details
                    + "\n\n" + advice,
                    "G134Office — ошибка запуска", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable dialogFailure) {
            dialogFailure.printStackTrace(System.err);
        }
    }

    static boolean isNativeLibraryFailure(Throwable error) {
        java.util.Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof UnsatisfiedLinkError) return true;
        }
        return false;
    }
}
