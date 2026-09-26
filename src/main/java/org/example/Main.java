package org.example;

import javafx.application.Application;
import javafx.stage.Stage;
import org.example.ui.MainWindow;

public class Main extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        org.example.fanfic.FicbookCookies.INSTANCE.install();
        new MainWindow(stage).show(getParameters().getRaw().stream().findFirst().orElse(null));
        System.err.println("Main window ready");
    }
}
