package org.catacomb.graph.gui;

import org.catacomb.datalish.Box;
import org.catacomb.report.E;

import java.awt.Graphics2D;

import java.awt.Color;


public class PickWorldCanvas extends WorldCanvas {

    static final long serialVersionUID = 1001;

    PickStore pickStore;
    Builder builder;
    PickHandler pickHandler;

    BuildPaintInstructor buildPaintInstructor;

    GridPainter gridPainter;

    boolean drawGrid = true;


    public PickWorldCanvas(int w, int h, boolean interact) {
        super(w, h, interact);

        pickStore = new PickStore();
        builder = new Builder(getPainter(), pickStore);
        pickHandler = new PickHandler(pickStore, getWorldTransform());
        prependHandler(pickHandler);
        gridPainter = new GridPainter();

    }


    public void setXAxisLabel(String s) {
        gridPainter.setXAxisLabel(s);
    }

    public void setBg(Color c) {
        super.setBg(c);
        if (gridPainter != null) {
            gridPainter.setGridBackground(c);
        }
    }


    public void setNoGrid() {
        gridPainter = null;
    }

    public void setShowGrid(boolean b) {
        drawGrid = b;
    }


    public void setBuildPaintInstructor(BuildPaintInstructor pi) {
        buildPaintInstructor = pi;
        if (pi instanceof PickListener) {
            setPickListener((PickListener)pi);
        }
    }


    public void setPickListener(PickListener pl) {
        pickHandler.setPickListener(pl);
    }


    public void setGridColor(Color c) {
        if (gridPainter != null) {
            gridPainter.setGridColor(c);
        }
    }

    public void setAxisColor(Color c) {
        if (gridPainter != null) {
            gridPainter.setAxisColor(c);
        }
    }


    public void prePaint(Graphics2D g) {
        builder.clear();
        builder.setCanvasSize(getWidth(), getHeight());

        if (drawGrid && gridPainter != null) {
            gridPainter.paint(painter);
        }

    }


    public void postPaint(Graphics2D g) {
        if (mouse.isDown()) {

        } else {
            pickHandler.echoPaint(painter, showTooltips());
        }
    }


    public void paint2D(Graphics2D g) {

        applyAAPreference(g);

        if (buildPaintInstructor != null) {
            buildPaintInstructor.instruct(painter, builder);

        } else if (paintInstructor != null) {
            paintInstructor.instruct(painter);
        }
    }


    public void attach(Object obj) {
        boolean done = false;

        if (obj instanceof BuildPaintInstructor) {
            setBuildPaintInstructor((BuildPaintInstructor)obj);
            done = true;

        } else if (obj instanceof PaintInstructor) {
            setPaintInstructor((PaintInstructor)obj);
            done = true;
        }

        if (obj instanceof PickListener) {
            setPickListener((PickListener)obj);
            done = true;
        }

        if (!done) {
            E.error(" - cannot attach " + obj + " to a PickWorldCanvas");
        }

    }


    public void setOnGridAxes() {
        gridPainter.setOnGridAxes();
    }


    public void reframe() {
        if (buildPaintInstructor != null) {
            Box box = buildPaintInstructor.getLimitBox(painter);
            frameToBox(box);

        } else if (paintInstructor != null) {
            Box box = paintInstructor.getLimitBox();
            frameToBox(box);

        } else {
            E.shortWarning("no paint instructor?");
        }

    }






}
