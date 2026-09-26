package org.example;

public class Launcher {
    public static void main(String[] args) {
        WindowsIntegration.initialize();
        try {
            Main.main(args);
        } catch (Throwable failure) {
            WindowsIntegration.reportStartupFailure(failure);
            System.exit(1);
        }
    }
}
