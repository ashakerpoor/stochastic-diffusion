package org.textensor.stochdiff.neuroml;

import java.util.ArrayList;
import java.util.HashMap;

import org.textensor.report.E;
import org.textensor.stochdiff.inter.AddableTo;
import org.textensor.stochdiff.inter.Transitional;
import org.textensor.stochdiff.model.Morphology;
import org.textensor.stochdiff.model.Segment;


public class cell implements MetaContainer, AddableTo, Transitional {

    public String name;

    public ArrayList<segment> segments = new ArrayList<segment>();

    public ArrayList<cable> cables = new ArrayList<cable>();

    public ArrayList<MorphMLCableGroup> cableGroups = new ArrayList<MorphMLCableGroup>();


    public String notes;

    HashMap<String, MorphMLPoint> srcptHM;

    //HashMap<String, MorphPoint> ptHM;
    // ArrayList<MorphPoint> points;

    // public NeuroMLBiophysics biophysics;

    meta meta;


    public void add(Object obj) {
        if (obj instanceof cable) {
            cables.add((cable)obj);
        } else if (obj instanceof MorphMLCableGroup) {
            cableGroups.add((MorphMLCableGroup)obj);

//		} else if (obj instanceof NeuroMLMechanism) {


        } else {
            E.error("cant add " + obj);
        }
    }


    public void addMetaItem(MetaItem mi) {
        if (meta == null) {
            meta = new meta();
        }
        meta.add(mi);
    }


    public ArrayList<segment> getSegments() {
        return segments;
    }


    public Object getFinal() {
        return getStochDiffMorphology();
    }





    public Morphology getStochDiffMorphology() {
        Morphology ret = new Morphology();

        HashMap<String, String> cableHM = new HashMap<String, String>();
        for (cable c : cables) {
            cableHM.put(c.getID(), c.getLabel());
        }


        ArrayList<segment> segs = getSegments();


        for (segment seg : segs) {
            Segment s = seg.getStochDiffSegment(cableHM);
            ret.add(s);
            E.info("added a segment " + s);
        }

        return ret;
    }




    /*

    public CellMorphology getCellMorphology() {
    	return getCellMorphology(name);
    }

    public CellMorphology getCellMorphology(String id) {
    	// E.info("finalizing from " + this + " " + setOfPoints.size() + " nseg=" + cell.getSegments().size());

    	boolean gotRoot = false;

    	ptHM = new HashMap<String, MorphPoint>();
    	points = new ArrayList<MorphPoint>();


    	ArrayList<MorphMLSegment> segs = getSegments();

    	for (MorphMLSegment seg : segs) {

    		String pid = seg.getParentID();

    		if (pid == null) {
    			if (gotRoot) {
    				E.error("multiple points with no parent?");
    			}
    			gotRoot = true;
    			// only allowed once in cell - defines the root segment
    			MorphPoint rpp = getOrMakePoint(seg.getProximal(), "rootpoint");
    			MorphPoint rpc = getOrMakePoint(seg.getDistal(), seg.id);
    			rpc.setParent(rpp);
    			rpc.minor = true;
    			String sn = seg.getName();
    			if (sn != null) {
    				rpp.addLabel(sn);
    				rpc.addLabel(sn);
    			}


    		} else {
    			MorphPoint rpp = ptHM.get(pid);
    			MorphPoint rpc = getOrMakePoint(seg.getDistal(), seg.getID());
    			rpc.minor = true;
    			String sn = seg.getName();
    			if (sn != null) {
    				rpc.addLabel(sn);
    			}

    			if (seg.getProximal() != null) {
    				// could be better to attach to rpp.parent, rather than rpp
    				MorphMLPoint p = seg.getProximal();
    				MorphPoint w = new MorphPoint(p.getID(), p.getX(), p.getY(), p.getZ(), p.getR());
    				if (rpp.getParent() != null &&
    						distanceBetween(w, rpp.getParent()) < distanceBetween(w, rpp)) {
    				   rpp = (MorphPoint)rpp.getParent();
    				   rpc.minor = true;
    				}
    			}
    			rpc.setParent(rpp);
    		}
    	}

    	CellMorphology cm = new CellMorphology();
    	cm.id = id;
    	cm.setPoints(points);
    	cm.resolve();
     //    E.info("returning MorphML import " + cm);

        cm.checkConnected();

        return cm;
    }









    private double distanceBetween(Point a, Point b) {
    	 return Geom.distanceBetween(a.getPosition(), b.getPosition());
    }



    private MorphPoint getOrMakePoint(MorphMLPoint p, String id) {
    	MorphPoint ret = null;
    		if (ptHM.containsKey(id)) {
    			ret = ptHM.get(id);
    		} else {

    		//	E.info("new point at " + sp.getID() + " " + sp.getX() + " " + sp.getY() + " " + sp.getR());
    			ret = new MorphPoint(id, p.getX(), p.getY(), p.getZ(), p.getR());
    			ptHM.put(id, ret);
    			points.add(ret);
    		}


    		return ret;
    	}
    */
}