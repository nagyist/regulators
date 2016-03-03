/*
 * Copyright (c) 2016 by Gerrit Grunwald
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.hansolo.fx.regulators;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;


/**
 * Created by hansolo on 03.03.16.
 */
public class ColorRegulator extends Region {
    private static final double         PREFERRED_WIDTH  = 250;
    private static final double         PREFERRED_HEIGHT = 250;
    private static final double         MINIMUM_WIDTH    = 50;
    private static final double         MINIMUM_HEIGHT   = 50;
    private static final double         MAXIMUM_WIDTH    = 1024;
    private static final double         MAXIMUM_HEIGHT   = 1024;
    private static final double         MIN_VALUE        = 0d;
    private static final double         MAX_VALUE        = 1d;
    private              double         BAR_START_ANGLE  = -130;
    private              double         ANGLE_RANGE      = 280;
    private final        RegulatorEvent TARGET_SET_EVENT = new RegulatorEvent(RegulatorEvent.TARGET_SET);
    private double                      size;
    private Canvas                      barCanvas;
    private GraphicsContext             barCtx;
    private Arc                         buttonOn;
    private Arc                         buttonOff;
    private Shape                       ring;
    private Shape                       innerRing;
    private Circle                      mainCircle;
    private Text                        textOn;
    private Text                        textOff;
    private Circle                      indicator;
    private Group                       shadowGroup;
    private Circle                      currentColorCircle;
    private Pane                        pane;
    private InnerShadow                 indicatorShadow;
    private DropShadow                  dropShadow;
    private InnerShadow                 highlight;
    private InnerShadow                 innerShadow;
    private InnerShadow                 currentColorCircleShadow;
    private Rotate                      indicatorRotate;
    private double                      scaleFactor;
    private DoubleProperty              targetValue;
    private ObjectProperty<Color>       targetColor;
    private double                      angleStep;
    private ConicalGradient             barGradient;
    private GradientLookup              gradientLookup;


    // ******************** Constructors **************************************
    public ColorRegulator() {
        scaleFactor  = 1d;
        targetValue  = new DoublePropertyBase(0) {
            @Override public void set(final double VALUE) {
                super.set(clamp(MIN_VALUE, MAX_VALUE, VALUE));
            }
            @Override public Object getBean() { return ColorRegulator.this; }
            @Override public String getName() { return "targetValue"; }
        };
        targetColor  = new ObjectPropertyBase<Color>(Color.YELLOW) {
            @Override public void set(final Color COLOR) {
                super.set(null == COLOR ? Color.BLACK : COLOR);
                currentColorCircle.setFill(COLOR);
            }
            @Override public Object getBean() { return ColorRegulator.this; }
            @Override public String getName() { return "targetColor"; }
        };
        angleStep    = ANGLE_RANGE / (MAX_VALUE - MIN_VALUE);
        init();
        initGraphics();
        registerListeners();
    }


    // ******************** Initialization ************************************
    private void init() {
        if (Double.compare(getPrefWidth(), 0.0) <= 0 || Double.compare(getPrefHeight(), 0.0) <= 0 ||
            Double.compare(getWidth(), 0.0) <= 0 || Double.compare(getHeight(), 0.0) <= 0) {
            if (getPrefWidth() > 0 && getPrefHeight() > 0) {
                setPrefSize(getPrefWidth(), getPrefHeight());
            } else {
                setPrefSize(PREFERRED_WIDTH, PREFERRED_HEIGHT);
            }
        }
        if (Double.compare(getMinWidth(), 0.0) <= 0 || Double.compare(getMinHeight(), 0.0) <= 0) {
            setMinSize(MINIMUM_WIDTH, MINIMUM_HEIGHT);
        }
        if (Double.compare(getMaxWidth(), 0.0) <= 0 || Double.compare(getMaxHeight(), 0.0) <= 0) {
            setMaxSize(MAXIMUM_WIDTH, MAXIMUM_HEIGHT);
        }
    }

    private void initGraphics() {
        dropShadow  = new DropShadow(BlurType.TWO_PASS_BOX, Color.rgb(0, 0, 0, 0.65), PREFERRED_WIDTH * 0.016, 0.0, 0, PREFERRED_WIDTH * 0.028);
        highlight   = new InnerShadow(BlurType.TWO_PASS_BOX, Color.rgb(255, 255, 255, 0.2), PREFERRED_WIDTH * 0.008, 0.0, 0, PREFERRED_WIDTH * 0.008);
        innerShadow = new InnerShadow(BlurType.TWO_PASS_BOX, Color.rgb(0, 0, 0, 0.2), PREFERRED_WIDTH * 0.008, 0.0, 0, -PREFERRED_WIDTH * 0.008);
        highlight.setInput(innerShadow);
        dropShadow.setInput(highlight);

        indicatorShadow = new InnerShadow(BlurType.TWO_PASS_BOX, Color.rgb(0, 0, 0, 0.75), PREFERRED_WIDTH * 0.008, 0.0, 0, PREFERRED_WIDTH * 0.004);

        currentColorCircleShadow = new InnerShadow(BlurType.TWO_PASS_BOX, Color.rgb(0, 0, 0, 0.65), PREFERRED_WIDTH * 0.075, 0.0, 0, 0);

        Stop[] stops = { new Stop(0.0, Color.rgb(255,255,0)),
                         new Stop(0.125, Color.rgb(255,0,0)),
                         new Stop(0.375, Color.rgb(255,0,255)),
                         new Stop(0.5, Color.rgb(0,0,255)),
                         new Stop(0.625, Color.rgb(0,255,255)),
                         new Stop(0.875, Color.rgb(0,255,0)),
                         new Stop(1.0, Color.rgb(255,255,0)) };

        List<Stop> reorderedStops = reorderStops(stops);

        gradientLookup = new GradientLookup(stops);

        barGradient = new ConicalGradient(reorderedStops);
        barCanvas   = new Canvas(PREFERRED_WIDTH, PREFERRED_HEIGHT);
        barCtx      = barCanvas.getGraphicsContext2D();
        barCtx.setLineCap(StrokeLineCap.ROUND);
        barCtx.setStroke(barGradient.getImagePattern(new Rectangle(0, 0, PREFERRED_WIDTH, PREFERRED_HEIGHT)));

        buttonOn = new Arc(PREFERRED_WIDTH * 0.5, PREFERRED_HEIGHT * 0.5, PREFERRED_WIDTH * 0.46, PREFERRED_HEIGHT * 0.46, -125, 34.75);
        buttonOn.setFill(null);
        buttonOn.setStroke(Color.rgb(66, 71, 79));
        buttonOn.setStrokeLineCap(StrokeLineCap.BUTT);
        buttonOn.setStrokeWidth(PREFERRED_WIDTH * 0.072);
        buttonOn.setEffect(dropShadow);

        buttonOff = new Arc(PREFERRED_WIDTH * 0.5, PREFERRED_HEIGHT * 0.5, PREFERRED_WIDTH * 0.46, PREFERRED_HEIGHT * 0.46, -89.75, 34.75);
        buttonOff.setFill(null);
        buttonOff.setStroke(Color.rgb(66, 71, 79));
        buttonOff.setStrokeLineCap(StrokeLineCap.BUTT);
        buttonOff.setStrokeWidth(PREFERRED_WIDTH * 0.072);
        buttonOff.setEffect(dropShadow);

        double center = PREFERRED_WIDTH * 0.5;
        ring = Shape.subtract(new Circle(center, center, PREFERRED_WIDTH * 0.42),
                              new Circle(center, center, PREFERRED_WIDTH * 0.3));
        ring.setFill(Color.rgb(66,71,79));
        ring.setEffect(highlight);

        mainCircle = new Circle();
        mainCircle.setFill(Color.rgb(14,22,33));

        textOn = new Text("ON");
        textOn.setFill(Color.WHITE);
        textOn.setTextOrigin(VPos.CENTER);
        textOn.setMouseTransparent(true);
        textOn.setRotate(17);

        textOff = new Text("OFF");
        textOff.setFill(Color.WHITE);
        textOff.setTextOrigin(VPos.CENTER);
        textOff.setMouseTransparent(true);
        textOff.setRotate(-17);

        indicatorRotate = new Rotate(-ANGLE_RANGE *  0.5, center, center);

        indicator = new Circle();
        indicator.setFill(Color.rgb(36, 44, 53));
        indicator.setMouseTransparent(true);
        indicator.getTransforms().add(indicatorRotate);

        shadowGroup = new Group(indicator);
        shadowGroup.setEffect(indicatorShadow);

        innerRing = Shape.subtract(new Circle(center, center, PREFERRED_WIDTH * 0.24),
                                   new Circle(center, center, PREFERRED_WIDTH * 0.2));
        innerRing.setFill(Color.rgb(66,71,79));
        innerRing.setEffect(dropShadow);

        currentColorCircle = new Circle();
        currentColorCircle.setFill(targetColor.get());
        currentColorCircle.setEffect(currentColorCircleShadow);

        pane = new Pane(barCanvas, ring, mainCircle, currentColorCircle, innerRing, shadowGroup, buttonOn, textOn, buttonOff, textOff);
        pane.setPrefSize(PREFERRED_HEIGHT, PREFERRED_HEIGHT);
        pane.setBackground(new Background(new BackgroundFill(Color.rgb(36, 44, 53), new CornerRadii(1024), Insets.EMPTY)));
        pane.setEffect(highlight);

        getChildren().setAll(pane);
    }

    private void registerListeners() {
        widthProperty().addListener(o -> resize());
        heightProperty().addListener(o -> resize());
        disabledProperty().addListener(o -> setOpacity(isDisabled() ? 0.4 : 1.0));
        targetValueProperty().addListener(o -> rotate(targetValue.get()));
        ring.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> touchRotate(e.getSceneX(), e.getSceneY()));
        ring.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> touchRotate(e.getSceneX(), e.getSceneY()));
        ring.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> fireEvent(TARGET_SET_EVENT));
        buttonOn.setOnMousePressed(e -> buttonOnPressed(true));
        buttonOn.setOnMouseReleased(e -> buttonOnPressed(false));
        buttonOff.setOnMousePressed(e -> buttonOffPressed(true));
        buttonOff.setOnMouseReleased(e -> buttonOffPressed(false));
    }


    // ******************** Methods *******************************************
    public double getTargetValue() { return targetValue.get(); }
    public void setTargetValue(final double VALUE) { targetValue.set(VALUE); }
    public DoubleProperty targetValueProperty() { return targetValue; }

    public Color getTargetColor() { return targetColor.get(); }
    public void setTargetColor(final Color COLOR) { targetColor.set(COLOR); }
    public ObjectProperty<Color> targetColorProperty() { return targetColor; }

    public List<Stop> getGradientStops() { return barGradient.getStops(); }
    public void setGradientStops(final Stop... STOPS) { setGradientStops(Arrays.asList(STOPS)); }
    public void setGradientStops(final List<Stop> STOPS) {
        gradientLookup.setStops(STOPS);
        barGradient = new ConicalGradient(reorderStops(STOPS));
        barCtx.setStroke(barGradient.getImagePattern(new Rectangle(0, 0, PREFERRED_WIDTH, PREFERRED_HEIGHT)));
    }

    private List<Stop> reorderStops(final Stop... STOPS) { return reorderStops(Arrays.asList(STOPS)); }
    private List<Stop> reorderStops(final List<Stop> STOPS) {
        /*
        0.0 -> 0.611
        0.5 -> 0.0 & 1.0
        1.0 -> 0.389
         */
        double range     = 0.778;
        double halfRange = range * 0.5;

        Map<Double, Color> stopMap = new HashMap<>();
        STOPS.forEach(stop -> stopMap.put(stop.getOffset(), stop.getColor()));

        List<Stop>        sortedStops     = new ArrayList<>(STOPS.size());
        SortedSet<Double> sortedFractions = new TreeSet<>(stopMap.keySet());
        if (sortedFractions.last() < 1) {
            stopMap.put(1.0, stopMap.get(sortedFractions.last()));
            sortedFractions.add(1.0);
        }
        if (sortedFractions.first() > 0) {
            stopMap.put(0.0, stopMap.get(sortedFractions.first()));
            sortedFractions.add(0.0);
        }
        for (double fraction : sortedFractions) {
            double offset = fraction * range - halfRange;
            offset = offset < 0 ? 1.0 + offset : offset;
            sortedStops.add(new Stop(offset, stopMap.get(fraction)));
        }
        return sortedStops;
    }

    private <T extends Number> T clamp(final T MIN, final T MAX, final T VALUE) {
        if (VALUE.doubleValue() < MIN.doubleValue()) return MIN;
        if (VALUE.doubleValue() > MAX.doubleValue()) return MAX;
        return VALUE;
    }

    private void touchRotate(final double X, final double Y) {
        Point2D p      = sceneToLocal(X, Y);
        double  deltaX = p.getX() - (pane.getLayoutX() + size * 0.5);
        double  deltaY = p.getY() - (pane.getLayoutY() + size * 0.5);
        double  radius = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
        double  nx     = deltaX / radius;
        double  ny     = deltaY / radius;
        double  theta  = Math.atan2(ny, nx);
        theta         = Double.compare(theta, 0.0) >= 0 ? Math.toDegrees(theta) : Math.toDegrees((theta)) + 360.0;
        double angle  = (theta + 230) % 360;
        if (angle > 320 && angle < 360) {
            angle = 0;
        } else if (angle <= 320 && angle > ANGLE_RANGE) {
            angle = ANGLE_RANGE;
        }
        setTargetValue(angle / angleStep + MIN_VALUE);
    }


    // ******************** Resizing ******************************************
    private void rotate(final double VALUE) {
        indicatorRotate.setAngle((VALUE - MIN_VALUE) * angleStep - ANGLE_RANGE * 0.5);
        targetColor.set(gradientLookup.getColorAt(VALUE));
        currentColorCircle.setFill(targetColor.get());
    }

    private void drawBar(final GraphicsContext CTX, final double VALUE) {
        CTX.clearRect(0, 0, size, size);
        double barXY          = size * 0.04;
        double barWH          = size * 0.92;
        double barAngleExtend = (VALUE - MIN_VALUE) * angleStep;
        CTX.save();
        CTX.strokeArc(barXY, barXY, barWH, barWH, BAR_START_ANGLE, -barAngleExtend, ArcType.OPEN);
        CTX.restore();
    }

    private void buttonOnPressed(final boolean PRESSED) {
        buttonOn.setEffect(PRESSED ? innerShadow : dropShadow);
        textOn.relocate(buttonOn.getLayoutBounds().getMinX() + (buttonOn.getLayoutBounds().getWidth() - textOn.getLayoutBounds().getWidth()) * 0.5, PRESSED ? size * 0.913 : size * 0.91);
        currentColorCircle.setVisible(true);
    }
    private void buttonOffPressed(final boolean PRESSED) {
        buttonOff.setEffect(PRESSED ? innerShadow : dropShadow);
        textOff.relocate(buttonOff.getLayoutBounds().getMinX() + (buttonOff.getLayoutBounds().getWidth() - textOff.getLayoutBounds().getWidth()) * 0.5, PRESSED ? size * 0.913 : size * 0.91);
        currentColorCircle.setVisible(false);
    }

    private void resize() {
        double width  = getWidth() - getInsets().getLeft() - getInsets().getRight();
        double height = getHeight() - getInsets().getTop() - getInsets().getBottom();
        size   = width < height ? width : height;

        if (width > 0 && height > 0) {
            double center = size * 0.5;

            pane.setMaxSize(size, size);
            pane.setPrefSize(size, size);
            pane.relocate((getWidth() - size) * 0.5, (getHeight() - size) * 0.5);

            barCanvas.setWidth(size);
            barCanvas.setHeight(size);
            barCtx.setLineWidth(size * 0.04);
            barCtx.setStroke(barGradient.getImagePattern(new Rectangle(0, 0, size, size)));
            drawBar(barCtx, MAX_VALUE);

            buttonOn.setCenterX(center);
            buttonOn.setCenterY(center);
            buttonOn.setRadiusX(size * 0.46);
            buttonOn.setRadiusY(size * 0.46);
            buttonOn.setStrokeWidth(size * 0.072);

            buttonOff.setCenterX(center);
            buttonOff.setCenterY(center);
            buttonOff.setRadiusX(size * 0.46);
            buttonOff.setRadiusY(size * 0.46);
            buttonOff.setStrokeWidth(size * 0.072);

            dropShadow.setRadius(size * 0.016);
            dropShadow.setOffsetY(size * 0.016);
            highlight.setRadius(clamp(1d, 2d, size * 0.004));
            highlight.setOffsetY(clamp(1d, 2d, size * 0.004));
            innerShadow.setRadius(clamp(1d, 2d, size * 0.004));
            innerShadow.setOffsetY(clamp(-1d, -2d, -size * 0.004));
            indicatorShadow.setRadius(size * 0.036);
            indicatorShadow.setOffsetY(size * 0.006);
            currentColorCircleShadow.setRadius(size * 0.075);

            scaleFactor = size / PREFERRED_WIDTH;
            ring.getTransforms().setAll(new Scale(scaleFactor, scaleFactor, 0, 0));

            mainCircle.setRadius(size * 0.3);
            mainCircle.setCenterX(center); mainCircle.setCenterY(center);

            textOn.setFont(Fonts.robotoLight(size * 0.04));
            textOn.relocate(buttonOn.getLayoutBounds().getMinX() + (buttonOn.getLayoutBounds().getWidth() - textOn.getLayoutBounds().getWidth()) * 0.5, size * 0.91);

            textOff.setFont(Fonts.robotoLight(size * 0.04));
            textOff.relocate(buttonOff.getLayoutBounds().getMinX() + (buttonOff.getLayoutBounds().getWidth() - textOff.getLayoutBounds().getWidth()) * 0.5, size * 0.91);

            indicator.setRadius(size * 0.032);
            indicator.setCenterX(center);
            indicator.setCenterY(size * 0.148);

            indicatorRotate.setPivotX(center);
            indicatorRotate.setPivotY(center);

            currentColorCircle.setCenterX(center);
            currentColorCircle.setCenterY(center);
            currentColorCircle.setRadius(size * 0.2);

            innerRing.getTransforms().setAll(new Scale(scaleFactor, scaleFactor, 0, 0));

            rotate(targetValue.get());
        }
    }


    // ******************** Event Handling ************************************
    public void setOnButtonOnPressed(final EventHandler<MouseEvent> HANDLER) { buttonOn.addEventHandler(MouseEvent.MOUSE_PRESSED, HANDLER); }
    public void removeOnButtonOnPressed(final EventHandler<MouseEvent> HANDLER) { buttonOn.removeEventHandler(MouseEvent.MOUSE_PRESSED, HANDLER); }

    public void setOnButtonOnReleased(final EventHandler<MouseEvent> HANDLER) { buttonOn.addEventHandler(MouseEvent.MOUSE_RELEASED, HANDLER); }
    public void removeOnButtonOnReleased(final EventHandler<MouseEvent> HANDLER) { buttonOn.removeEventHandler(MouseEvent.MOUSE_RELEASED, HANDLER); }

    public void setOnButtonOffPressed(final EventHandler<MouseEvent> HANDLER) { buttonOff.addEventHandler(MouseEvent.MOUSE_PRESSED, HANDLER); }
    public void removeOnButtonOffPressed(final EventHandler<MouseEvent> HANDLER) { buttonOff.removeEventHandler(MouseEvent.MOUSE_PRESSED, HANDLER); }

    public void setOnButtonOffReleased(final EventHandler<MouseEvent> HANDLER) { buttonOff.addEventHandler(MouseEvent.MOUSE_RELEASED, HANDLER); }
    public void removeOnButtonOffReleased(final EventHandler<MouseEvent> HANDLER) { buttonOff.removeEventHandler(MouseEvent.MOUSE_RELEASED, HANDLER); }

    public void setOnTargetSet(final EventHandler<RegulatorEvent> HANDLER) { addEventHandler(RegulatorEvent.TARGET_SET, HANDLER); }
    public void removeOnTargetSet(final EventHandler<RegulatorEvent> HANDLER) { removeEventHandler(RegulatorEvent.TARGET_SET, HANDLER); }
}
