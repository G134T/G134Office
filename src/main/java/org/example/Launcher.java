package org.example;

public class Launcher {
    public static void main(String[] args) {
        WindowsIntegration.initialize();
        logStartup(args);
        org.example.ui.UiScreen.install();
        try {
            Main.main(args);
        } catch (Throwable failure) {
            WindowsIntegration.reportStartupFailure(failure);
            System.exit(1);
        }
    }

    /** What Windows passed, plus the charsets Java uses for paths. Goes to the startup log. */
    private static void logStartup(String[] args) {
        int count = args == null ? 0 : args.length;
        if (count == 0) {
            System.err.println("startup args=0");
        } else {
            for (int i = 0; i < count; i++) {
                System.err.println("startup arg[" + i + "]=" + args[i]);
            }
        }
        System.err.println("sun.jnu.encoding=" + System.getProperty("sun.jnu.encoding"));
        System.err.println("file.encoding=" + System.getProperty("file.encoding"));
    }
}
