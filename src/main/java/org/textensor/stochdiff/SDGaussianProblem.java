package org.textensor.stochdiff;

import org.catacomb.dataview.CCViz;


public class SDGaussianProblem {


    public static void main(String[] argv) {
        String root = "data/may17/Test5B_model";
        String[] args = {root + ".xml"};

        StochDiff.main(args);

        String[] sa = {root + ".out"};
        CCViz.main(sa);
    }


}
