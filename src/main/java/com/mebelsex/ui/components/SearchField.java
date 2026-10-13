package com.mebelsex.ui.components;

import javafx.animation.PauseTransition;
import javafx.scene.control.TextField;
import javafx.util.Duration;

public class SearchField extends TextField {

    private PauseTransition debounceTimer;
    private Runnable onSearchAction;

    public SearchField() {
        super();
        debounceTimer = new PauseTransition(Duration.millis(300));
        debounceTimer.setOnFinished(e -> {
            if (onSearchAction != null) {
                onSearchAction.run();
            }
        });

        this.textProperty().addListener((obs, oldV, newV) -> debounceTimer.playFromStart());
    }

    public void setOnSearchAction(Runnable onSearchAction) {
        this.onSearchAction = onSearchAction;
    }

    public void setOnSearch(java.util.function.Consumer<String> handler) {
        this.onSearchAction = () -> handler.accept(this.getText());
    }
}
