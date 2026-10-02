package org.example;

import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

/** Primary monitor size and DPI. Used before JavaFX starts. */
public final class WindowsDisplay {
    public final int widthPx;
    public final int heightPx;
    public final int dpi;

    public WindowsDisplay(int widthPx, int heightPx, int dpi) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.dpi = dpi;
    }

    public static WindowsDisplay read() {
        if (!WindowsIntegration.isWindows()) return new WindowsDisplay(1920, 1080, 96);
        WinDef.HDC dc = null;
        try {
            int width = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSCREEN);
            int height = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYSCREEN);
            dc = User32.INSTANCE.GetDC(null);
            // LOGPIXELSX: pixels per inch of the primary screen.
            int dpi = dc == null ? 96 : GDI32.INSTANCE.GetDeviceCaps(dc, 88);
            if (width < 640 || height < 480) {
                width = 1920;
                height = 1080;
            }
            if (dpi < 72 || dpi > 480) dpi = 96;
            return new WindowsDisplay(width, height, dpi);
        } catch (Throwable failure) {
            System.err.println("Cannot read the Windows display: " + failure);
            return new WindowsDisplay(1920, 1080, 96);
        } finally {
            if (dc != null) User32.INSTANCE.ReleaseDC(null, dc);
        }
    }
}
