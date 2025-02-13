package com.example.demo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HelloApplication extends Application {

    private TextField titleField = new TextField();
    private TextField authorField = new TextField();
    private TextArea jsonInput = new TextArea();
    private ProgressBar progressBar = new ProgressBar(0);
    private Button startButton = new Button("Start");
    private Button stopButton = new Button("Stop");

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Task<Void> downloadTask;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("pobieracz");

        // UI Layout
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setVgap(10);
        grid.setHgap(10);

        // UI Components
        grid.add(new Label("tytuł:"), 0, 0);
        grid.add(titleField, 1, 0);
        titleField.setEditable(false);

        grid.add(new Label("autor:"), 0, 1);
        grid.add(authorField, 1, 1);
        authorField.setEditable(false);

        grid.add(new Label("JSON:"), 0, 2);
        jsonInput.setPrefRowCount(10);
        grid.add(jsonInput, 1, 2);

        grid.add(startButton, 1, 3);
        stopButton.setDisable(true);
        grid.add(stopButton, 1, 4);

        progressBar.setPrefWidth(200);
        grid.add(progressBar, 1, 5);

        // Button Actions
        startButton.setOnAction(e -> startProcessing());
        stopButton.setOnAction(e -> stopProcessing());

        // Scene Setup
        primaryStage.setScene(new Scene(grid, 450, 400));
        primaryStage.show();
    }

    private void startProcessing() {
        String jsonText = jsonInput.getText();
        if (jsonText.isEmpty()) {
            showAlert("stop", "pole na JSON musi być pełne.");
            return;
        }

        // Disable start and enable stop
        startButton.setDisable(true);
        stopButton.setDisable(false);

        downloadTask = new Task<>() {
            @Override
            protected Void call() {
                try {
                    String title = extractValue(jsonText, "\"title\"\\s*:\\s*\"(.*?)\"");
                    String author = extractValue(jsonText, "\"copyright\"\\s*:\\s*\"(.*?)\"");
                    String imageUrl = extractValue(jsonText, "\"url\"\\s*:\\s*\"(.*?)\"");

                    for (int i = 0; i <= 100; i += 10) {
                        if (isCancelled()) return null;
                        Thread.sleep(100);
                        updateProgress(i, 100);
                    }

                    if (!isCancelled()) {
                        Platform.runLater(() -> {
                            titleField.setText(title.isEmpty() ? "Unknown" : title);
                            authorField.setText(author.isEmpty() ? "Unknown" : author);
                        });
                        downloadImage(imageUrl);
                    }
                } catch (Exception ex) {
                    showAlert("stop", "zatrzymano pobieranie");
                } finally {
                    Platform.runLater(() -> {
                        startButton.setDisable(false);
                        stopButton.setDisable(true);
                    });
                }
                return null;
            }
        };

        progressBar.progressProperty().bind(downloadTask.progressProperty());
        executorService.submit(downloadTask);
    }

    private void stopProcessing() {
        if (downloadTask != null && downloadTask.isRunning()) {
            downloadTask.cancel();
        }
        startButton.setDisable(false);
        stopButton.setDisable(true);
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
    }

    private void downloadImage(String imageUrl) {
        executorService.submit(() -> {
            try {
                String imgFolder = "img";
                Files.createDirectories(Paths.get(imgFolder));
                String fileName = imgFolder + "/" + imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                try (InputStream in = new URL(imageUrl).openStream();
                     OutputStream out = new FileOutputStream(fileName)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (downloadTask.isCancelled()) return;
                        out.write(buffer, 0, bytesRead);
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Stop", ""));
            }
        });
    }

    private String extractValue(String json, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }

    public static void main(String[] args) {
        launch(args);
    }
}