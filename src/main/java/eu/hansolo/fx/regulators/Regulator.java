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

import javafx.beans.property.*;
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
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;

import java.util.Locale;


public class Regulator extends Region {
    private static final double   PREFERRED_WIDTH  = 250;
    private static final double   PREFERRED_HEIGHT = 250;
    private static final double   MINIMUM_WIDTH    = 50;
    private static final double   MINIMUM_HEIGHT   = 50;
    private static final double   MAXIMUM_WIDTH    = 1024;
    private static final double   MAXIMUM_HEIGHT   = 1024;
    private double                BAR_START_ANGLE  = -130;
    private double                ANGLE_RANGE      = 280;
    private double                size;
    private Canvas                barCanvas;
    private GraphicsContext       barCtx;
    private Shape                 ring;
    private Circle                mainCircle;
    private Text                  text;
    private Circle                indicator;
    private Group                 shadowGroup;
    private Region                symbol;
    private Pane                  pane;
    private InnerShadow           indicatorShadow;
    private DropShadow            dropShadow;
    private InnerShadow           highlight;
    private InnerShadow           innerShadow;
    private DropShadow            barGlow;
    private Rotate                indicatorRotate;
    private double                scaleFactor;
    private DoubleProperty        minValue;
    private DoubleProperty        maxValue;
    private DoubleProperty        value;
    private IntegerProperty       decimals;
    private StringProperty        unit;
    private ObjectProperty<Color> barColor;
    private String                formatString;
    private double                angleStep;


    // ******************** Constructors **************************************
    public Regulator() {
        getStylesheets().add(Regulator.class.getResource("regulator.css").toExternalForm());
        scaleFactor  = 1d;
        minValue     = new DoublePropertyBase(0) {
            @Override public void set(final double VALUE) {
                super.set(clamp(-Double.MAX_VALUE, maxValue.get(), VALUE));
                angleStep = ANGLE_RANGE / (maxValue.get() - minValue.get());
            }
            @Override public Object getBean() { return Regulator.this; }
            @Override public String getName() { return "minValue"; }
        };
        maxValue     = new DoublePropertyBase(100) {
            @Override public void set(final double VALUE) {
                super.set(clamp(minValue.get(), Double.MAX_VALUE, VALUE));
                angleStep = ANGLE_RANGE / (maxValue.get() - minValue.get());
            }
            @Override public Object getBean() { return Regulator.this; }
            @Override public String getName() { return "maxValue"; }
        };
        value        = new DoublePropertyBase(0) {
            @Override public void set(final double VALUE) { super.set(clamp(minValue.get(), maxValue.get(), VALUE)); }
            @Override public Object getBean() { return Regulator.this; }
            @Override public String getName() { return "value"; }
        };
        decimals     = new IntegerPropertyBase(0) {
            @Override public void set(final int VALUE) {
                super.set(clamp(0, 2, VALUE));
                formatString = new StringBuilder("%.").append(Integer.toString(decimals.get())).append("f").append(getUnit()).toString();
                redraw();
            }
            @Override public Object getBean() { return Regulator.this; }
            @Override public String getName() { return "decimals"; }
        };
        unit         = new StringPropertyBase("") {
            @Override public void set(final String VALUE) {
                super.set(VALUE.equals("%") ? "%%" : VALUE);
                formatString = new StringBuilder("%.").append(Integer.toString(decimals.get())).append("f").append(get()).toString();
                redraw();
            }
            @Override public Object getBean() { return Regulator.this; }
            @Override public String getName() { return "unit"; }
        };
        barColor     = new ObjectPropertyBase<Color>(Color.CYAN) {
            @Override public void set(final Color COLOR) {
                super.set(null == COLOR ? Color.CYAN : COLOR);
                redraw();
            }
            @Override public Object getBean() { return Regulator.this; }
            @Override public String getName() { return "barColor"; }
        };
        formatString = new StringBuilder("%.").append(Integer.toString(decimals.get())).append("f").append(unit.get()).toString();
        angleStep    = ANGLE_RANGE / (maxValue.get() - minValue.get());
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

        barGlow   = new DropShadow(BlurType.TWO_PASS_BOX, barColor.get(), PREFERRED_WIDTH * 0.004, 0, 0, 0);
        barCanvas = new Canvas(PREFERRED_WIDTH, PREFERRED_HEIGHT);
        barCtx    = barCanvas.getGraphicsContext2D();
        barCtx.setLineCap(StrokeLineCap.ROUND);
        barCtx.setStroke(barColor.get());

        double center = PREFERRED_WIDTH * 0.5;
        ring = Shape.subtract(new Circle(center, center, PREFERRED_WIDTH * 0.42),
                              new Circle(center, center, PREFERRED_WIDTH * 0.3));
        ring.setFill(Color.rgb(66,71,79));
        ring.setEffect(dropShadow);

        mainCircle = new Circle();
        mainCircle.setFill(Color.rgb(14,22,33));

        text = new Text(String.format(Locale.US, formatString, getValue()));
        text.setFill(Color.WHITE);
        text.setTextOrigin(VPos.CENTER);

        indicatorRotate = new Rotate(-ANGLE_RANGE *  0.5, center, center);

        indicator = new Circle();
        indicator.setFill(Color.rgb(36, 44, 53));
        indicator.setMouseTransparent(true);
        indicator.getTransforms().add(indicatorRotate);

        shadowGroup = new Group(indicator);
        shadowGroup.setEffect(indicatorShadow);

        symbol = new Region();
        symbol.getStyleClass().setAll("symbol");

        pane = new Pane(barCanvas, ring, mainCircle, text, shadowGroup, symbol);
        pane.setPrefSize(PREFERRED_HEIGHT, PREFERRED_HEIGHT);
        pane.setBackground(new Background(new BackgroundFill(Color.rgb(36,44,53), new CornerRadii(1024), Insets.EMPTY)));
        pane.setEffect(highlight);

        getChildren().setAll(pane);
    }

    private void registerListeners() {
        widthProperty().addListener(o -> resize());
        heightProperty().addListener(o -> resize());
        disabledProperty().addListener(o -> setOpacity(isDisabled() ? 0.4 : 1.0));
        valueProperty().addListener(o -> rotate(value.get()));
        ring.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> touchRotate(e.getSceneX(), e.getSceneY()));
        ring.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> touchRotate(e.getSceneX(), e.getSceneY()));
    }


    // ******************** Methods *******************************************
    public double getMinValue() { return minValue.get(); }
    public void setMinValue(final double VALUE) { minValue.set(VALUE); }
    public DoubleProperty minValueProperty() { return minValue; }

    public double getMaxValue() { return maxValue.get(); }
    public void setMaxValue(final double VALUE) { maxValue.set(VALUE); }
    public DoubleProperty maxValueProperty() { return maxValue; }

    public double getValue() { return value.get(); }
    public void setValue(final double VALUE) { value.set(VALUE); }
    public DoubleProperty valueProperty() { return value; }

    public int getDecimals() { return decimals.get(); }
    public void setDecimals(final int VALUE) { decimals.set(VALUE); }
    public IntegerProperty decimalsProperty() { return decimals; }

    public String getUnit()  { return unit.get(); }
    public void setUnit(final String UNIT) { unit.set(UNIT); }
    public StringProperty unitProperty() { return unit; }

    public Color getBarColor() { return barColor.get(); }
    public void setBarColor(final Color COLOR) { barColor.set(COLOR); }
    public ObjectProperty<Color> barColorProperty() { return barColor; }

    public void setSymbolPath(final double SCALE_X, final double SCALE_Y, final String PATH) {
        if (PATH.isEmpty()) {
            symbol.setVisible(false);
        } else {
            System.out.println("Path filled");
            symbol.setStyle(new StringBuilder().append("-fx-scale-x:").append(clamp(0d, 1d, SCALE_X)).append(";")
                                               .append("-fx-scale-y:").append(clamp(0d, 1d, SCALE_Y)).append(";")
                                               .append("-fx-shape:\"").append(PATH).append("\";")
                                               .toString());
            symbol.setVisible(true);
        }
        resize();
    }

    private <T extends Number> T clamp(final T MIN, final T MAX, final T VALUE) {
        if (VALUE.doubleValue() < MIN.doubleValue()) return MIN;
        if (VALUE.doubleValue() > MAX.doubleValue()) return MAX;
        return VALUE;
    }

    private void adjustTextSize(final Text TEXT, final double MAX_WIDTH, double fontSize) {
        final String FONT_NAME = TEXT.getFont().getName();
        while (TEXT.getLayoutBounds().getWidth() > MAX_WIDTH && fontSize > 0) {
            fontSize -= 0.005;
            TEXT.setFont(new Font(FONT_NAME, fontSize));
        }
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
        setValue(angle / angleStep + minValue.get());
    }


    // ******************** Resizing ******************************************
    private void rotate(final double VALUE) {
        drawBar(barCtx, VALUE);
        indicatorRotate.setAngle((VALUE - minValue.get()) * angleStep - ANGLE_RANGE * 0.5);
        text.setText(String.format(Locale.US, formatString, VALUE));
        adjustTextSize(text, size * 0.48, size * 0.216);
        text.setLayoutX((size - text.getLayoutBounds().getWidth()) * 0.5);
    }

    private void drawBar(final GraphicsContext CTX, final double VALUE) {
        CTX.clearRect(0, 0, size, size);
        double barXY          = size * 0.04;
        double barWH          = size * 0.92;
        double barAngleExtend = (VALUE - minValue.get()) * angleStep;
        CTX.save();
        CTX.setEffect(barGlow);
        CTX.strokeArc(barXY, barXY, barWH, barWH, BAR_START_ANGLE, -barAngleExtend, ArcType.OPEN);
        CTX.restore();
    }

    private void resize() {
        double width  = getWidth() - getInsets().getLeft() - getInsets().getRight();
        double height = getHeight() - getInsets().getTop() - getInsets().getBottom();
        size   = width < height ? width : height;

        if (width > 0 && height > 0) {
            pane.setMaxSize(size, size);
            pane.setPrefSize(size, size);
            pane.relocate((getWidth() - size) * 0.5, (getHeight() - size) * 0.5);

            barCanvas.setWidth(size);
            barCanvas.setHeight(size);
            barGlow.setRadius(size * 0.016);
            barCtx.setLineWidth(size * 0.04);
            drawBar(barCtx, value.get());

            dropShadow.setRadius(size * 0.016);
            dropShadow.setOffsetY(size * 0.016);
            highlight.setRadius(clamp(1d, 2d, size * 0.004));
            highlight.setOffsetY(clamp(1d, 2d, size * 0.004));
            innerShadow.setRadius(clamp(1d, 2d, size * 0.004));
            innerShadow.setOffsetY(clamp(-1d, -2d, -size * 0.004));
            indicatorShadow.setRadius(size * 0.036);
            indicatorShadow.setOffsetY(size * 0.006);

            double center = size * 0.5;
            scaleFactor = size / PREFERRED_WIDTH;
            ring.getTransforms().setAll(new Scale(scaleFactor, scaleFactor, 0, 0));

            mainCircle.setRadius(size * 0.3);
            mainCircle.setCenterX(center); mainCircle.setCenterY(center);

            text.setFont(Fonts.robotoMedium(size * 0.216));
            text.relocate((size - text.getLayoutBounds().getWidth()) * 0.5, size * 0.33);

            indicator.setRadius(size * 0.032);
            indicator.setCenterX(center);
            indicator.setCenterY(size * 0.148);

            indicatorRotate.setPivotX(center);
            indicatorRotate.setPivotY(center);

            symbol.setPrefSize(size * 0.112, size * 0.112);
            symbol.relocate((size - symbol.getPrefWidth()) * 0.5, size * 0.62);

            redraw();
        }
    }

    private void redraw() {
        symbol.setBackground(new Background(new BackgroundFill(barColor.get(), CornerRadii.EMPTY, Insets.EMPTY)));
        barCtx.setStroke(barColor.get());
        barGlow.setColor(barColor.get());
        rotate(value.get());
    }
}
