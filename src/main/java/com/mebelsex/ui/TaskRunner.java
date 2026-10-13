package com.mebelsex.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

@Component
public class TaskRunner {

    private final ExceptionHandler exceptionHandler;

    public TaskRunner(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public <V> void run(Task<V> task) {
        run(task, null, null);
    }

    public <V> void run(java.util.concurrent.Callable<V> work, java.util.function.Consumer<V> onSuccess) {
        run(work, onSuccess, null);
    }

    public <V> void run(java.util.concurrent.Callable<V> work, java.util.function.Consumer<V> onSuccess,
            java.util.function.Consumer<Throwable> onError) {
        Task<V> task = new Task<>() {
            @Override
            protected V call() throws Exception {
                return work.call();
            }
        };
        run(task, onSuccess, onError);
    }

    public <V> void run(Task<V> task, java.util.function.Consumer<V> onSuccess) {
        run(task, onSuccess, null);
    }

    public <V> void run(Task<V> task, java.util.function.Consumer<V> onSuccess,
            java.util.function.Consumer<Throwable> onError) {
        // Build the dialog
        Stage progressStage = new Stage(StageStyle.UNDECORATED);
        progressStage.initModality(Modality.APPLICATION_MODAL);

        // Ensure CSS is loaded by inheriting active window or applying global CSS
        Window active = Stage.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null);
        if (active != null) {
            progressStage.initOwner(active);
        }

        ProgressBar progressBar = new ProgressBar();
        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setPrefWidth(200);

        Label title = new Label(com.mebelsex.common.Messages.get("ui.in_progress"));
        title.getStyleClass().add("section-title");

        VBox layout = new VBox(16, title, progressBar);
        layout.setAlignment(Pos.CENTER);
        layout.getStyleClass().addAll("dialog-pane", "card");

        Scene scene = new Scene(layout);
        ThemeManager theme = ThemeManager.getInstance();
        if (theme != null) {
            theme.install(scene);
        } else {
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        }
        progressStage.setScene(scene);

        // Delay popup by 300ms
        PauseTransition delay = new PauseTransition(Duration.millis(300));
        delay.setOnFinished(e -> {
            if (task.isRunning()) {
                progressStage.show();
            }
        });

        task.addEventHandler(javafx.concurrent.WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            cleanup(progressStage, delay, task);
            if (onSuccess != null) {
                Platform.runLater(() -> onSuccess.accept(task.getValue()));
            }
        });

        task.addEventHandler(javafx.concurrent.WorkerStateEvent.WORKER_STATE_FAILED, e -> {
            cleanup(progressStage, delay, task);
            exceptionHandler.handle(task.getException());
            if (onError != null) {
                Platform.runLater(() -> onError.accept(task.getException()));
            }
        });

        task.addEventHandler(javafx.concurrent.WorkerStateEvent.WORKER_STATE_CANCELLED, e -> {
            cleanup(progressStage, delay, task);
        });

        delay.play();

        Thread bgThread = new Thread(task);
        bgThread.setDaemon(true);
        bgThread.start();
    }

    private void cleanup(Stage progressStage, PauseTransition delay, Task<?> task) {
        delay.stop();
        if (progressStage.isShowing()) {
            progressStage.close();
        }
    }
}
