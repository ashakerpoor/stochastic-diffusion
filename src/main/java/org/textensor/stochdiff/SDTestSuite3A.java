package org.textensor.stochdiff;

import org.catacomb.dataview.CCViz;


public class SDTestSuite3A {


    public static void main(String[] argv) {
        String root = "data/apr25/New_EPAC_S_272/HEK293model";
        String[] args = {root + ".xml"};

        StochDiff.main(args);

        String[] sa = {root + ".out"};
        CCViz.main(sa);
    }


}
