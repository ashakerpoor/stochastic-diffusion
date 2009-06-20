
package org.catacomb.graph.gui;

import java.awt.Graphics;

import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;

class HorizontalAxisGraphDivider extends BasicSplitPaneDivider {
    static final long serialVersionUID = 1001;

    GraphColors gcols;

    HorizontalAxisGraphDivider(BasicSplitPaneUI bspui, GraphColors gc) {
        super(bspui);
        gcols = gc;
        setBorder(new EmptyBorder(0, 0, 0, 0));
    }


    public void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();


        g.setColor(gcols.getGraphBg());
        g.fillRect(0, 0, w, h);

        g.setColor(gcols.getBorderFg());
        g.drawLine(0, h-1, w, h-1);
    }


}

