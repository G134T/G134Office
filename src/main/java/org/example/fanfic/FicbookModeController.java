package org.example.fanfic;

import javafx.scene.web.WebEngine;

import java.awt.Desktop;
import java.net.URI;

/** Общая логика режима ФФ: HTML5 WebView, адреса Ficbook и вход через браузер ОС. */
public final class FicbookModeController {
    public static final String HOME = "https://ficbook.net/";
    public static final String MY_FICS = "https://ficbook.net/home/myfics";
    public static final String WRITE = "https://ficbook.net/home/addfic";
    public static final String LOGIN = "https://ficbook.net/login";
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private FicbookModeController() {
    }

    /** Включает возможности HTML5, JavaScript и современный браузерный профиль WebView. */
    public static void configure(WebEngine engine) {
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent(USER_AGENT);
    }

    /** Приводит введённый адрес к безопасному адресу Ficbook. */
    public static String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return HOME;
        if (value.startsWith("https://") || value.startsWith("http://")) return value;
        if (value.startsWith("ficbook.net") || value.startsWith("www.ficbook.net")) {
            return "https://" + value;
        }
        return "https://ficbook.net/" + value.replaceFirst("^/+", "");
    }

    public static boolean isFicbookUrl(String url) {
        if (url == null) return false;
        try {
            String host = new URI(url).getHost();
            return host != null && (host.equalsIgnoreCase("ficbook.net")
                    || host.toLowerCase().endsWith(".ficbook.net"));
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Открывает вход во внешнем браузере, назначенном в Windows по умолчанию. */
    public static boolean openLoginInDefaultBrowser() {
        try {
            if (!Desktop.isDesktopSupported()) return false;
            Desktop.getDesktop().browse(URI.create(LOGIN));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
