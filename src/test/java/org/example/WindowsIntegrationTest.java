package org.example;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowsIntegrationTest {
    @Test void usesUserWritableDirectoryWithSpacesAndCyrillic() {
        assertEquals(Path.of("C:/Users/Анатолий/AppData/Local", "G134Office"),
                WindowsIntegration.dataDirectory("C:/Users/Анатолий/AppData/Local", "ignored"));
        assertEquals(Path.of("C:/Users/Test User", ".g134office"),
                WindowsIntegration.dataDirectory(" ", "C:/Users/Test User"));
    }

    @Test void findsNativeFailureWrappedByJavaFx() {
        assertTrue(WindowsIntegration.isNativeLibraryFailure(
                new RuntimeException(new UnsatisfiedLinkError("glass.dll"))));
        assertFalse(WindowsIntegration.isNativeLibraryFailure(new IllegalStateException("other failure")));
    }

    @Test void handlesCyclicExceptionCauses() {
        var first = new RuntimeException();
        var second = new RuntimeException(first);
        first.initCause(second);
        assertFalse(WindowsIntegration.isNativeLibraryFailure(first));
    }
}
