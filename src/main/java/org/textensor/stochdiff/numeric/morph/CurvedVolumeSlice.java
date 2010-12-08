package org.textensor.stochdiff.numeric.morph;

import org.textensor.report.E;
import org.textensor.stochdiff.geom.*;

import java.util.ArrayList;
import java.util.HashMap;

public class CurvedVolumeSlice {

    double baseDelta;
    double radiusa;
    double radiusb;


    double[] bdsa;
    double[] bdsb;
    int[] nazim;

    ArrayList<CurvedVolumeElement> elements;
    HashMap<Integer, ArrayList<CurvedVolumeElement>> radHM;

    double maxAspectRatio = 2;

    public CurvedVolumeSlice(double delta, double ra, double rb) {
        baseDelta = delta;
        radiusa = ra;
        radiusb = rb;
    }


    public double[] getRadii(int end) {
        return (end == 0 ? bdsa : bdsb);
    }

    public int[] getNazimuthals() {
        return nazim;
    }


    public void discFill(Position pa, Position pb, String pointLabel, String regionLabel,
                         boolean hasSurfaceLayer, double slDepth, double maxAR) {
        maxAspectRatio = maxAR;

        double axlen = Geom.distanceBetween(pa, pb);
        Translation trans = Geom.translation(Geom.midpoint(pa, pb));
        Vector vab = Geom.fromToVector(pa, pb);
        double rottheta = Geom.zRotationAngle(Geom.unitY(), vab);
        Rotation rot = Geom.aboutZRotation(rottheta);

        elements = new ArrayList<CurvedVolumeElement>();


        // center of the box at 0,0


        // this is a little confusing. X and Y axes are used within the slice, but when these are
        // turned into boxes, the slab of boxes is initially created in the X-Z plane before being rotated
        // into place

        double maxr = Math.max(radiusa, radiusb);
        double[] bdm = getRadialSplit(maxr, hasSurfaceLayer, slDepth);

        bdsa = getRadialSplit(radiusa, bdm.length, hasSurfaceLayer, slDepth);
        bdsb = getRadialSplit(radiusb, bdm.length, hasSurfaceLayer, slDepth);


        nazim = getAzimuthalSplits(maxr, bdm);

        radHM = new HashMap<Integer, ArrayList<CurvedVolumeElement>>();

        for (int ir = 0; ir < bdsa.length; ir++) {


            double ra1 = (ir > 0 ? bdsa[ir-1] : 0);
            double ra2 = bdsa[ir];

            double rb1 = (ir > 0 ? bdsb[ir-1] : 0);
            double rb2 = bdsb[ir];

            double rc = ((ra1 + ra2) / 2 + (rb1 + rb2) / 2) / 2;


            int na = nazim[ir];
            double eltangle = 2. * Math.PI / na;

            double volouter = axlen * (ra2 * ra2 + rb2 * rb2 + ra2 * rb2) / 3.;
            double volinner = axlen * (ra1 * ra1 + rb1 * rb1 + ra1 * rb1) / 3.;
            double eltvol = (volouter - volinner) / na;


            double carea = axlen * ((ra2 - ra1) + (rb2 - rb1)) / 2;

            double subarea = (axlen * 2 * Math.PI * (ra1 + rb1) / 2) / na;



            ArrayList<CurvedVolumeElement> azb = null;
            double eltangleb = 1.;
            if (ir > 0) {
                azb = radHM.get(ir-1);
                eltangleb = 2 * Math.PI / azb.size();
            }


            ArrayList<CurvedVolumeElement> az = new ArrayList<CurvedVolumeElement>();
            for (int ia = 0; ia < na; ia++) {

                double theta = ia * eltangle;

                CurvedVolumeElement ve = new CurvedVolumeElement();
                if (regionLabel != null) {
                    ve.setRegion(regionLabel);
                }

                ve.setPositionIndexes(ir, ia);
                ve.setVolume(eltvol);

                if (na > 0) {
                    if (ia > 0) {
                        az.get(ia-1).coupleTo(ve, carea);
                    }
                    if (ia == na - 1) {
                        ve.coupleTo(az.get(0), carea);
                    }
                }

                if (ir > 1) {
                    double thc = theta + 0.5 * eltangle;
                    int ib = (int)(thc / eltangleb);
                    azb.get(ib).coupleTo(ve, subarea);
                }



                double thetaC = theta + 0.5 * eltangle;
                double vcx = rc * Math.cos(thetaC);
                double vcy = rc * Math.sin(thetaC);
                Position cp = Geom.position(vcx, vcy, 0.);
                Position pr = rot.getRotatedPosition(cp);
                Position pc = trans.getTranslated(pr);
                ve.setCenterPosition(pc.getX(), pc.getY(), pc.getZ());


                TrianglesSet ts = makeTriangles(axlen, ra1, ra2, rb1, rb2, theta, eltangle);

                ts.rotate(rot);
                ts.translate(trans);

                ve.setTriangles(ts.getStripLengths(), ts.getPositions(), ts.getNormals());

                az.add(ve);
                elements.add(ve);
            }
            radHM.put(ir, az);
        }
    }


    // TODO need a main method with some tests of getRadialSplit

    private TrianglesSet makeTriangles(double axlen, double ra1, double ra2, double rb1, double rb2, double theta,
                                       double eltangle) {

        // initial layout: elements are in the x-z plane, bottom surface at y = -0.5 * axlen, top at y = 0.5 * axlen
        // rotations measured up from the x axis,

        TrianglesSet ret = new TrianglesSet();

        double dth = Math.PI * 2. / 36.;
        int npart = (int)(Math.round(eltangle / dth));
        if (npart < 1) {
            npart = 1;
        }

        if (eltangle < 1.9 * Math.PI) {
            TriangleStrip tss = makeEnd(axlen, ra1, ra2, rb1, rb2, theta, -1);
            ret.add(tss);
            TriangleStrip tst = makeEnd(axlen, ra1, ra2, rb1, rb2, theta + eltangle, 1);
            ret.add(tst);
        }

        if (ra1 > 1.e-7) {
            TriangleStrip tsin = makeConeSurfacePart(axlen, ra1, rb1, theta, eltangle, -1, npart);
            ret.add(tsin);
        }
        TriangleStrip tsout = makeConeSurfacePart(axlen, ra2, rb2, theta, eltangle, 1, npart);
        ret.add(tsout);


        TriangleStrip tsp = makeSliceSurface(-0.5 * axlen, ra1, ra2, theta, eltangle, -1, npart);
        ret.add(tsp);

        TriangleStrip tsq = makeSliceSurface(0.5 * axlen, rb1, rb2, theta, eltangle, 1, npart);
        ret.add(tsq);

        return ret;
    }





    private TriangleStrip makeSliceSurface(double dy, double r1, double r2, double theta, double eltangle,
                                           int idir, int npart) {
        TriangleStrip ret = new TriangleStrip();

        double xn = 0.;
        double yn = idir;
        double zn = 0;

        for (int i = 0; i <= npart; i++) {
            double a = theta + i * eltangle / npart;
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            ret.addPoint(r1 * ca, dy, r1 * sa, xn, yn, zn);
            ret.addPoint(r2 * ca, dy, r2 * sa, xn, yn, zn);

        }
        if (idir > 0) {
            ret.flip();
        }
        return ret;
    }



    private TriangleStrip makeConeSurfacePart(double axlen, double ra, double rb,
            double theta, double eltangle, int idir, int npart) {
        TriangleStrip ret = new TriangleStrip();



        double ay = Math.atan2(rb - ra, axlen);
        double fy = Math.sin(ay);
        double fr = Math.cos(ay);


        double am = -0.5 * axlen;
        double ap = 0.5 * axlen;

        for (int i = 0; i < npart + 1; i++) {
            double a = theta + i * eltangle / npart;

            double ca = Math.cos(a);
            double sa = Math.sin(a);
            double xn = fr * idir * ca;
            double yn = -fy * idir;  // TODO check sign
            double zn = fr * idir * sa;

            ret.addPoint(ra * ca, am, ra* sa,  xn, yn, zn);
            ret.addPoint(rb * ca, ap, rb* sa,  xn, yn, zn);
        }
        if (idir < 0) {
            ret.flip();
        }

        return ret;
    }



    private TriangleStrip makeEnd(double axlen, double ra1, double ra2, double rb1, double rb2, double theta, int idir) {
        TriangleStrip ret = new TriangleStrip();

        double y = -0.5 * axlen;
        double ct = Math.cos(theta);
        double st = Math.sin(theta);

        double xn = idir * st;
        double yn = 0.;
        double zn = -idir * ct;

        ret.addPoint(ra1 * ct, y, ra1 * st, xn, yn, zn);
        ret.addPoint(ra2 * ct, y, ra2 * st, xn, yn, zn);
        y = 0.5 * axlen;
        ret.addPoint(rb1 * ct, y, rb1 * st, xn, yn, zn);
        ret.addPoint(rb2 * ct, y, rb2 * st, zn, yn, zn);

        if (idir < 0) {
            ret.flip();
        }

        return ret;
    }




    private int[] getAzimuthalSplits(double radius, double[] bdm) {
        int[] ret = new int[bdm.length];
        int npre = 1;
        for (int i = 0; i < bdm.length; i++) {
            double rin = (i > 0 ? bdm[i-1] : 0);
            double rout = bdm[i];
            double rc = (rin + rout) / 2;

            double dr = rout - rin;
            double circ = 2 * Math.PI * rc;

            int nfac = (int)Math.round(Math.ceil((circ / (dr * maxAspectRatio)) / npre));
            ret[i] = npre * nfac;
            npre = ret[i];
        }
        return ret;
    }



    private double[] getRadialSplit(double r, boolean hsl, double sld) {
        return getRadialSplit(r, 0, hsl, sld);
    }



    private double[] getRadialSplit(double r, int ansplit, boolean hsl, double sld) {
        double[] ret = null;
        int nsplit = ansplit;
        if (hsl) {
            if (r < sld) {
                ret = new double[1];
                ret[0] = r;
            } else {
                int nr = 1;
                if (ansplit > 0) {
                    nr = ansplit - 1;
                } else {
                    nr = (int)Math.round((r - sld) / baseDelta);
                    if (nr < 1) {
                        nr = 1;
                    }
                }
                ret = new double[nr + 1];
                for (int i = 0; i < nr+1; i++) {
                    ret[i] = ((i + 1.)/(nr)) * (r - sld);
                }
                ret[nr] = r;
            }


        } else {
            if (nsplit > 0) {
                ret = new double[nsplit];
            } else {
                nsplit = (int)Math.round(r / baseDelta);
                if (nsplit < 1) {
                    nsplit = 1;
                }
            }
            ret = new double[nsplit];
            for (int i = 0; i < nsplit; i++) {
                ret[i] = ((i + 1.)/ nsplit) * r;
            }
        }

        return ret;
    }



    public CurvedVolumeElement getRAElement(int ir, int ia) {
        return radHM.get(ir).get(ia);
    }




    public void planeConnect(CurvedVolumeSlice vg) {
        double[] ras = getRadii(1);
        int[] zas = getNazimuthals();

        double[] rbs = vg.getRadii(0);
        int[] zbs = vg.getNazimuthals();

        double eps = 1.e-6;

        for (int ira = 0; ira < ras.length; ira++) {
            double ra = ras[ira];
            double ra0 = (ira > 0 ? ras[ira-1] : 0);

            for (int irb = 0; irb < rbs.length; irb++) {
                double rb = rbs[irb];
                double rb0 = (irb > 0 ? rbs[irb-1] : 0);

                if (rb < ra0 + eps) {
                    // b elt completely below a elt
                } else if (rb0 > ra - eps) {
                    // b elt comletely above a

                } else {
                    // they overlap
                    //   E.info("olrings " + ira + " " + irb + "     " + ra0 + " " + ra + "    " + rb0 + " " + rb);


                    double ro0 = (ra0 > rb0 ? ra0 : rb0);
                    double ro1 = (ra < rb ? ra : rb);
                    double olarea = Math.PI * (ro0 * ro0  +  ro1 * ro1  +  ro0 * ro1) / 3.;

                    int na = zas[ira];
                    int nb = zbs[irb];

                    if (na == nb) {
                        // E.info("PCexact " + ira + " " + irb + " " + na);
                        double carea = olarea / na;
                        // they match up exactly - simple
                        for (int iz = 0; iz < na; iz++) {
                            getRAElement(ira, iz).coupleTo(vg.getRAElement(irb, iz), carea);

                        }

                    } else {
                        double da = 1. / na;
                        double db = 1. / nb;

                        int izb = 0;

                        for (int iza = 0; iza < na; iza++) {
                            double a0 = iza * da;
                            double a1 = a0 + da;

                            while (izb * db < a1 - eps) {
                                double b0 = izb * db;
                                double b1 = b0 + db;
                                double fc = Math.min(b1, a1) - Math.max(a0, b0);

                                if (fc > eps) {
                                    getRAElement(ira, iza).coupleTo(vg.getRAElement(irb, izb), fc * olarea);
                                }
                                // E.info("PCol " + ira + " " + irb + " " + na + " " + nb + "    " + iza + " " + izb);

                                b0 = b1;
                                b1 += db;
                                izb += 1;
                            }
                            izb -= 1;

                        }

                    }
                }
            }


        }

        // vg is the next slice, startiong at our pb
    }



    public void subPlaneConnect(TreePoint tp, TreePoint tpn, CurvedVolumeSlice vg, double partBranchOffset) {
        planeConnect(vg);
        // MUSTDO - this ignores the partBranchOffset, and conects them as though they were aligned
    }



    public ArrayList<CurvedVolumeElement> getElements() {
        return elements;
    }



    /*
           double vcx = x0 + i * boxSize;
           double vcy =  y0 + j * boxSize;

           VolumeElement ve = new VolumeElement();
           elements[i][j] = ve;
           if (regionLabel != null) {
              ve.setRegion(regionLabel);
           }
           ve.setVolume(boxSize * boxSize * sl);
           ve.setDeltaZ(boxSize);

           Position cp = Geom.position(vcx, vcy, 0.);
           Position pr = rot.getRotatedPosition(cp);
           Position pc = trans.getTranslated(pr);
           ve.setCenterPosition(pc.getX(), pc.getY(), pc.getZ());


           ve.setAlongArea(boxSize * sl);
           ve.setSideArea(boxSize * boxSize);
           ve.setTopArea(boxSize * sl);


           // this is the boundary of a slice through the box perpendicular to the z axis
           // it is not used for the computation, just for visualization
           Position[] pbdry = {Geom.position(vcx - 0.5 * boxSize, -0.5 * sl, vcy),
                 Geom.position(vcx - 0.5 * boxSize, 0.5 * sl, vcy),
                 Geom.position(vcx + 0.5 * boxSize, 0.5 * sl, vcy),
                 Geom.position(vcx + 0.5 * boxSize, -0.5 * sl, vcy)};

            for (int ib = 0; ib < pbdry.length; ib++) {
               pbdry[ib] = trans.getTranslated(rot.getRotatedPosition(pbdry[ib]));
            }
            ve.setBoundary(pbdry);


            if (regionLabel != null) {
               ve.setRegion(regionLabel);
            }



            boolean surf = false;
            double hb = 0.5 * boxSize;
            Position[] psb = new Position[4];
            // four different cases here since the boundary points have to go in the right order to give
            // the right-hand normal pointing outwards
            if (i == 0 || !present[i-1][j]) {
               surf = true;
               double xb = vcx + -0.5 * boxSize;
               psb[0] = Geom.position(xb, -0.5 * sl, vcy - hb);
               psb[1] = Geom.position(xb, -0.5 * sl, vcy + hb);
               psb[2] = Geom.position(xb, 0.5 * sl, vcy + hb);
               psb[3] = Geom.position(xb, 0.5 * sl, vcy - hb);

            } else if (i == nx-1 || !present[i+1][j]) {
               surf = true;
               double xb = vcx + 0.5 * boxSize;
               psb[0] = Geom.position(xb, -0.5 * sl, vcy + hb);
               psb[1] = Geom.position(xb, -0.5 * sl, vcy - hb);
               psb[2] = Geom.position(xb, 0.5 * sl, vcy - hb);
               psb[3] = Geom.position(xb, 0.5 * sl, vcy + hb);

            } else if (j == 0 || !present[i][j-1]) {
               surf = true;
               double yb = vcy - 0.5 * boxSize;
               psb[0] = Geom.position(vcx + hb, -0.5 * sl, yb);
               psb[1] = Geom.position(vcx - hb, -0.5 * sl, yb);
               psb[2] = Geom.position(vcx - hb, 0.5 * sl, yb);
               psb[3] = Geom.position(vcx + hb, 0.5 * sl, yb);

            } else if (j == ny - 1 || !present[i][j+1]) {
               surf = true;
               double yb = vcy + 0.5 * boxSize;
               psb[0] = Geom.position(vcx - hb, -0.5 * sl, yb);
               psb[1] = Geom.position(vcx + hb, -0.5 * sl, yb);
               psb[2] = Geom.position(vcx + hb, 0.5 * sl, yb);
               psb[3] = Geom.position(vcx - hb, 0.5 * sl, yb);
            }

            if (surf) {
               ve.setSubmembrane();

                 for (int ib = 0; ib < psb.length; ib++) {
                    psb[ib] = trans.getTranslated(rot.getRotatedPosition(psb[ib]));
                 }
                  ve.setSurfaceBoundary(psb);
                  ve.setExposedArea(sl * boxSize);
               }
            }


    if (pointLabel != null) {
     elements[icenter][icenter].setLabel(pointLabel);
    }
    neighborize();

    // neighborize calls v.coupleTo(vnbr, area-of-contact);
    */





    /*


    private GeometryArray makeDonuts(float[][] csp, float fac) {
    	float eps = (float)(0.1 * fac);

    	float heps = 0.3f * eps;

    	float rr2 = (float)(1 / Math.sqrt(2.));

    	int nc = csp.length;

    	int nside = 15;
    	int nstrip = 2;

    	int nvert = (2 * nside * nstrip) * nc;

    	int[] svcs = new int[nc * nstrip];
    	for (int i = 0; i < nstrip * nc; i++) {
    		svcs[i] = (2 * nside);
    	}


    	float[] fsa = new float[nside];
    	float[] fsb = new float[nside];
    	for (int i = 0; i < nside; i++) {
    		double th = i * (2. * Math.PI / (nside-1));
    		fsa[i] = (float)Math.cos(th);
    		fsb[i] = (float)Math.sin(th);
    	}



    	float[] datv = new float[3 * nvert];
    	float[] datn = new float[3 * nvert];

    	for (int i = 0; i < nc; i++) {
    		float vx = csp[i][5];
    		float vy = csp[i][6];
    		float vz = csp[i][7];

    		float x = fac * csp[i][0] - 0.1f * eps * vx;
    		float y = fac * csp[i][1] - 0.1f * eps * vy;
    		float z = fac * csp[i][2]  - 0.1f * eps * vz;
    		float lxy = (float)Math.sqrt(vx * vx + vy* vy);

    		float px = vy / lxy;
    		float py = -vx / lxy;
    		float pz = 0.f;

    		float qx = -vz * py;
    		float qy = vz * px;
    		float qz = vx*py - vy*px;


    		int ko = 3 * (nside * nstrip * 2) * i;


    		for (int js = 0; js < nside; js++) {

    			float ox = fsa[js] * px + fsb[js] * qx;
    			float oy = fsa[js] * py + fsb[js] * qy;
    			float oz = fsa[js] * pz + fsb[js] * qz;


    			datv[ko] = x + eps * ox + eps * vx;
    			datv[ko+1] = y + eps * oy + eps * vy;
    			datv[ko+2] = z + eps * oz + eps * vz;

    			datn[ko] = rr2 * (ox + vx);
    			datn[ko+1] = rr2 * (oy + vy);
    			datn[ko+2] = rr2 * (oz + vz);
    			ko += 3;

    			datv[ko] = x + eps * ox;
    			datv[ko+1] = y + eps * oy;
    			datv[ko+2] = z + eps * oz;

    			datn[ko] = ox;
    			datn[ko+1] = oy;
    			datn[ko+2] = oz;

    			ko += 3;



    		}



    		for (int js = 0; js < nside; js++) {

    			float ox = fsa[js] * px + fsb[js] * qx;
    			float oy = fsa[js] * py + fsb[js] * qy;
    			float oz = fsa[js] * pz + fsb[js] * qz;




    			datv[ko] = x + heps * ox + eps * vx;
    			datv[ko+1] = y + heps * oy + eps * vy;
    			datv[ko+2] = z + heps * oz + eps * vz;

    			datn[ko] = vx;
    			datn[ko+1] = vy;
    			datn[ko+2] = vz;
    			ko += 3;


    			datv[ko] = x + eps * ox + eps * vx;
    			datv[ko+1] = y + eps * oy + eps * vy;
    			datv[ko+2] = z + eps * oz + eps * vz;

    			datn[ko] = rr2 * (ox + vx);
    			datn[ko+1] = rr2 * (oy + vy);
    			datn[ko+2] = rr2 * (oz + vz);
    			ko += 3;
    		}
    	}

    	TriangleStripArray ret = new TriangleStripArray(nvert,
    			GeometryArray.COORDINATES | GeometryArray.NORMALS, svcs);
    		ret.setCoordinates(0, datv);
    		ret.setNormals(0, datn);
    		return ret;

    }

    */




}