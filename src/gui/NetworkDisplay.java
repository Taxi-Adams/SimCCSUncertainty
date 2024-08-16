package gui;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.scene.text.Font;

/**
 *
 * @author yaw
 */
class NetworkDisplay extends Pane {

    private DoubleProperty scale = new SimpleDoubleProperty(1.0);
    private ControlActions controlActions;

    public NetworkDisplay() {
        // add scale transform
        scaleXProperty().bind(scale);
        scaleYProperty().bind(scale);
    }

    public double getScale() {
        return scale.get();
    }

    public void setScale(double scale) {
        this.scale.set(scale);
    }

    public void setPivot(double x, double y) {
        setTranslateX(getTranslateX() - x);
        setTranslateY(getTranslateY() - y);
    }

    public void setControlActions(ControlActions controlActions) {
        this.controlActions = controlActions;
    }

    public ControlActions getControlActions() {
        return controlActions;
    }
}

class DragContext {

    double mouseAnchorX;
    double mouseAnchorY;

    double translateAnchorX;
    double translateAnchorY;
}

class SceneGestures {

    private static final double MAX_SCALE = 100.0d;
    private static final double MIN_SCALE = 1d;

    private DragContext sceneDragContext = new DragContext();

    NetworkDisplay canvas;

    private ArrayList<Pane> entitiesToResize = new ArrayList<>();
    private double radius = 5;
    private double fontSize = 13;

    public SceneGestures(NetworkDisplay canvas) {
        this.canvas = canvas;
    }

    public void addEntityToResize(Pane p) {
        entitiesToResize.add(p);
    }

    public EventHandler<ScrollEvent> getOnScrollEventHandler() {
        return onScrollEventHandler;
    }

    public EventHandler<MouseEvent> getMouseEventHandler() {
        return mouseEventHandler;
    }

    private EventHandler<MouseEvent> mouseEventHandler = new EventHandler<MouseEvent>() {
        private boolean dragging = false;

        public void handle(MouseEvent e) {
            if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {
                dragging = false;
                if (e.isPrimaryButtonDown()) {
                    sceneDragContext.mouseAnchorX = e.getSceneX();
                    sceneDragContext.mouseAnchorY = e.getSceneY();

                    sceneDragContext.translateAnchorX = canvas.getTranslateX();
                    sceneDragContext.translateAnchorY = canvas.getTranslateY();
                } else if (e.isSecondaryButtonDown()) {
                    // Open
                }
            } else if (e.getEventType() == MouseEvent.DRAG_DETECTED) {
                dragging = true;
            } else if (e.getEventType() == MouseEvent.MOUSE_CLICKED) {
                if (!dragging) {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        canvas.getControlActions().processLeftClick(canvas.getControlActions().displayXYToVectorized(e.getX(), e.getY()));
                    } else if (e.getButton() == MouseButton.SECONDARY) {
                        canvas.getControlActions().processRightClick(canvas.getControlActions().displayXYToVectorized(e.getX(), e.getY()));
                    }
                }
            } else if (e.getEventType() == MouseEvent.MOUSE_DRAGGED) {
                canvas.setTranslateX(sceneDragContext.translateAnchorX + e.getSceneX() - sceneDragContext.mouseAnchorX);
                canvas.setTranslateY(sceneDragContext.translateAnchorY + e.getSceneY() - sceneDragContext.mouseAnchorY);

                e.consume();
            }
        }
    };

    private EventHandler<ScrollEvent> onScrollEventHandler = new EventHandler<ScrollEvent>() {

        @Override
        public void handle(ScrollEvent event) {
            //canvas.getRightClickMenu().hide();

            double delta = 1.2;

            double scale = canvas.getScale();
            double oldScale = scale;

            if (event.getDeltaY() < 0) {
                scale /= delta;
            } else {
                scale *= delta;
            }

            scale = clamp(scale, MIN_SCALE, MAX_SCALE);

            double f = (scale / oldScale) - 1;

            double dx = (event.getSceneX() - (canvas.getBoundsInParent().getWidth() / 2 + canvas.getBoundsInParent().getMinX()));
            double dy = (event.getSceneY() - (canvas.getBoundsInParent().getHeight() / 2 + canvas.getBoundsInParent().getMinY()));

            canvas.setScale(scale);

            canvas.setPivot(f * dx, f * dy);

            // Resize components based on zoom level.
            resizeComponents(scale, oldScale);

            event.consume();
        }

        private void resizeComponents(double newScale, double oldScale) {
            // Resize entities.
            for (Pane p : entitiesToResize) {
                for (Node n : p.getChildren()) {
                    if (n instanceof Circle) {
                        Circle c = (Circle) n;
                        double radius = c.getRadius() * oldScale;
                        c.setRadius(radius / newScale);
                    } else if (n instanceof Arc) {
                        Arc arc = (Arc) n;
                        double radius = arc.getRadiusX() * oldScale;
                        arc.setRadiusX(radius / newScale);
                        arc.setRadiusY(radius / newScale);
                    } else if (n instanceof Label) {
                        Label l = (Label) n;
                        l.setFont(new Font("System Regular", Math.max(0.5, fontSize / Math.max(newScale / 2, 1))));
                        // TODO: Going to have to be more clever to shift labels...
                    } else if (n instanceof Path) {
                        Path path = (Path) n;
                        double radius = path.getStrokeWidth() * oldScale;
                        path.setStrokeWidth(radius / newScale);
                    }
                }
            }
        }
    };

    public static double clamp(double value, double min, double max) {
        if (Double.compare(value, min) < 0) {
            return min;
        }

        if (Double.compare(value, max) > 0) {
            return max;
        }

        return value;
    }
}
