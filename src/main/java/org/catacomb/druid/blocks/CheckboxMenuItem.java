
package org.catacomb.druid.blocks;

import org.catacomb.druid.build.Context;
import org.catacomb.druid.build.GUIPath;
import org.catacomb.druid.build.Realizer;
import org.catacomb.druid.gui.edit.DruCheckboxMenuItem;


public class CheckboxMenuItem implements Realizer {

    public String id;
    public String label;

    public String info;

    public String action;

    public String depends;
    public String flavor;

    public CheckboxMenuItem() {
    }


    public CheckboxMenuItem(String s) {
        label = s;
    }


    public Object realize(Context ctx, GUIPath gpathin) {
        GUIPath gpath = gpathin;
        gpath = gpath.extend(id);

        if (action == null) {
            action = label;
        }
        DruCheckboxMenuItem  drum = new DruCheckboxMenuItem(label, action);

        if (info != null) {
            drum.setInfoReceiver(ctx.getInfoAggregator());
            drum.setInfo(info);
        }

        ctx.addComponent(drum, gpath);

        if (depends != null) {
            if (flavor == null) {
                flavor = "selection";
            }
            ctx.getMarketplace().addViewer("TreeSelection", drum, flavor);
            drum.setEnableOnSelection(depends);
        }


        return drum;
    }


}
