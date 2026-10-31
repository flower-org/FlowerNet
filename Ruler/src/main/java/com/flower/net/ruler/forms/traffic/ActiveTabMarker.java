package com.flower.net.ruler.forms.traffic;

import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/** On/off badge for a rule list tab: white check on green, or white cross on red. Drawn with shapes, so no font dependency. */
final class ActiveTabMarker {
    private static final double RADIUS = 7;
    private static final Color ON_COLOR = Color.web("#2e9e3e");
    private static final Color OFF_COLOR = Color.web("#d83a3a");
    private static final String CHECK = "M -3.5 0 L -1 2.5 L 3.5 -2.5";
    private static final String CROSS = "M -2.5 -2.5 L 2.5 2.5 M 2.5 -2.5 L -2.5 2.5";

    private ActiveTabMarker() {}

    static Node create(ObservableBooleanValue active) {
        Circle circle = new Circle(RADIUS);
        SVGPath mark = new SVGPath();
        mark.setFill(null);
        mark.setStroke(Color.WHITE);
        mark.setStrokeWidth(2);
        mark.setStrokeLineCap(StrokeLineCap.ROUND);
        mark.setStrokeLineJoin(StrokeLineJoin.ROUND);

        StackPane marker = new StackPane(circle, mark);

        Runnable update = () -> {
            boolean on = active.get();
            circle.setFill(on ? ON_COLOR : OFF_COLOR);
            mark.setContent(on ? CHECK : CROSS);
        };
        active.addListener((obs, oldValue, newValue) -> update.run());
        update.run();
        return marker;
    }
}
