package org.textensor.stochdiff;

import org.catacomb.dataview.CCViz;


public class SDTestSuite3B {


    public static void main(String[] argv) {
        String root = "data/apr25/New_EPAC_S_1500/HEK293model";
        String[] args = {root + ".xml"};

        StochDiff.main(args);

        String[] sa = {root + ".out"};
        CCViz.main(sa);
    }


}
