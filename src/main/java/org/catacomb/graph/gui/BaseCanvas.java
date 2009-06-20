
package org.catacomb.graph.gui;

import org.catacomb.interlish.structure.Repaintable;
import org.catacomb.report.E;

import java.awt.*;



public class BaseCanvas extends BasePanel implements Repaintable {
    static final long serialVersionUID = 1001;

    int prefw;
    int prefh;


    Color bgColor;
    Color fgColor;

    BasicStroke bs1 = new BasicStroke((float)1.0);




    public BaseCanvas(int w, int h) {
        super();
        bgColor = Color.black;
        fgColor = Color.white;

        setPreferredSize(new Dimension(w, h));
        prefw = w;
        prefh = h;

        setBackground(bgColor);
        setFont(new Font("sansserif", Font.PLAIN, 12));
    }

    public void setPreferredSize(int w, int h) {
        prefw = w;
        prefh = h;
        setPreferredSize(new Dimension(prefw, prefh));
    }

    public void setBg(Color c) {
        bgColor = c;
        setBackground(bgColor);
    }


    public void setCursor(String type) {
        if (type.equals("default") || type.equals("pointer")) {
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

        } else if (type.equals("cross")) {
            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

        } else {
            E.warning("unrecognized cursor " + type);
        }
    }


    public void setDataBackground(Color c) {
        bgColor = c;
    }

    public Color getDataBackground() {
        return bgColor;
    }


    public Color getNormalForeground() {
        return fgColor;
    }


    public void paintComponent(Graphics g0) {

        g0.setColor(bgColor);
        g0.fillRect(0, 0, getWidth(), getHeight());
        Graphics2D g = (Graphics2D)g0;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);


        simpleStroke(g);

        paint2D(g);

    }


    final void simpleStroke(Graphics2D g) {
        g.setStroke(bs1);
    }


    public void paint2D(@SuppressWarnings("unused")
                        Graphics2D g) {

    }


    public void requestRepaint() {
        repaint();
    }



    /*
      is this ever necessary now??

    public int[][] getIntegerImage() {
       Image im = createImage(imw, imh);
       Graphics gg = im.getGraphics();
       printAll (gg);
       int[] pix = new int[imw * imh];
       PixelGrabber grabber = new PixelGrabber(im, 0, 0, imw,imh,
    				      pix, 0, imw);
       try {
     grabber.grabPixels();
       } catch (Exception e) {
     S.p ("pixel grabbing interrupted");
       }
       int[][] ret = new int[imh][imw];

       for (int i = 0; i < imh; i++) {
           for (int j = 0; j < imw; j++) {
              int pi = pix[i*imw + j];
              int ir = ((pi  >> 16) & 0xFF);
              int ig = ((pi >> 8) & 0xFF);
              int ib = (pi & 0xFF);
         ret[i][j] = (ir << 16) + (ig << 8) + ib;
           }
        }
        return ret;
    }

    */

}


