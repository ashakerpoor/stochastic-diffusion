//7 11 2008 RO & WK: IF ( ( NumberofMolecules*(Probablity of diffusion or reaction) ) < NP && using binomial),
//          THEN calculate ngo from poisson distribution, ELSE calculate ngo from gaussian; NP=20.
//7 3 2008  RO: if observe negative ngo, ngo=0
//7 2 2008  WK: In parallelAndSharedDiffusionStep() function, when np0 > NMAX_STOCHASTIC and if n*p < 10,
//          then use poission to get ngo; otherwise, use gaussian
//9 25 2007 WK: In advance() function, we set the inc/decrements (i.e., ngo*xxx) to zero explicitly
//          to avoid floating point error
//9 11 2007 WK: In parallelAndSharedDiffusionStep(), for the independent diffusion,
//          (i) the probability to diffuse to a neighboring subvolume is the probability
//          to diffuse to that neighbor divided by the sum of all the probabilities
//          to diffuse to all neighbors; (ii) for calculating ngo, we use
//          binomial variance.
//8 28 2007 WK: In advance(), for the diffusion step, when algoID is INDEPENDENT,
//          we call parallelAndSharedDiffusionStep() instead of calling
//          parallelDiffusionStep().  WK added the parallelAndSharedDiffusionStep()
//          function and SHARED_DIFF_PARTICLES constant.
//6 18 2007 WK: The getGridConcsPlainText_dumb() function is modified to (i) flag
//           a volume element as either on submembrane or on cytosol, and
//           (ii) identify its region.
//5 16 2007: modified by RO & WK (modifications within initials ... initials)
//written by Robert Cannon
package org.textensor.stochdiff.numeric.grid;

import java.io.File;
import java.util.StringTokenizer;
import java.util.Arrays;

import org.textensor.report.Debug;
import org.textensor.report.E;
import org.textensor.stochdiff.model.SDRun;
import org.textensor.stochdiff.numeric.BaseCalc;
import org.textensor.stochdiff.numeric.math.MersenneTwister;
import org.textensor.stochdiff.numeric.morph.VolumeGrid;
import org.textensor.stochdiff.numeric.stochastic.InterpolatingStepGenerator;
import org.textensor.stochdiff.numeric.stochastic.StepGenerator;
import org.textensor.util.ArrayUtil;
import org.textensor.vis.CCViz3D;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


/*
 * Approximate stochastic calculation with a fixed timestep where
 * the number of reactions orruring in a volume and the number of particles
 * moving to neighbouring volumes are both generated from lookup tables
 * in p (probability of event for one particle)  and
 * n (number of particles of a given type)  for a uniform random number.
 *
 * This is approximate because the effect of the particles on their new
 * location is not taken into account until the following step.
 */

public class SteppedStochasticGridCalc extends GridCalc {
    static final Logger log = LogManager.getLogger(SteppedStochasticGridCalc.class);

    // WK 8 28 2007
    // in parallelAndSharedDiffusionStep(),
    // (1) if the number of molecules to duffuse is less than
    // SHARED_DIFF_PARTICLES*(number of neighboring subvolume elements),
    // we do shared diffusion,
    // (2) otherwise, we do parallel diffusion.
    public static final int SHARED_DIFF_PARTICLES = 4;
    public static final int NP = 30;         // AB Changed from 20 to 30. 2011.09.23

    double[] lnfdiff;

    double[][] lnCC;

    int[][] wkA;
    int[][] wkB;
    int[] wkReac;

    int[][] nparticle;

    int nreaction;
    public int nspecie;
    String[] speciesIDs;
    double[] diffusionConstants;

    int[][] reactantIndices;
    int[][] productIndices;

    int[][] reactantStochiometry;
    int[][] productStochiometry;

    double[] rates;
    double[] lnrates;

    double lndt;

    int ninjected = 0;

    InterpolatingStepGenerator interpSG;
    MersenneTwister random;
    int nwarn;
    int nngowarn = 0;         //added in v2.1.1 by BHK to keep track of a different type of warning
    int ninfo;

    double[][] pSharedOut;
    double[][] lnpSharedOut;
    double[][][] fSharedExit;

    public SteppedStochasticGridCalc(SDRun sdm) {
        super(sdm);
    }

    protected int randomRound(double number) {
        int i = (int) number;
        double d = number - i;

        // random allocation to implement the remainder
        // (some cells get an extra particle, some don't)
        if (random.random() < d)
            i++;

        return i;
    }

    public final void init() {
        super.init();

        //not in Determinstic: stateSaveTime += sdRun.getStartTime();

        // something to generate the random nunmbers
        random = new MersenneTwister(getCalculationSeed());

        nreaction = rtab.getNReaction();
        rates = rtab.getRates();

        // Debug.dump("rates", rates);

        lnrates = ArrayUtil.log(rates, -999.);

        reactantIndices = rtab.getReactantIndices();
        productIndices = rtab.getProductIndices();

        reactantStochiometry = rtab.getReactantStochiometry();
        productStochiometry = rtab.getProductStochiometry();

        nspec = rtab.getNSpecies();
        specieIDs = rtab.getSpecieIDs();
        volumes = vgrid.getElementVolumes();
        lnvolumes = ArrayUtil.log(volumes, -999.);

        // RO
        // ----------------------
        // System.out.println("Number of files        : " + NspeciesFilef);
        // System.out.println("Total numer of species : " + NspeciesIDsOutf);

        // ----------------------
        // RO
        extractOutputScheme(rtab); // see BaseCalc.java

        surfaceAreas = vgrid.getExposedAreas();

        fdiff = rtab.getDiffusionConstants();
        lnfdiff = ArrayUtil.log(fdiff, -999.);

        neighbors = vgrid.getPerElementNeighbors();
        couplingConstants = vgrid.getPerElementCouplingConstants();
        lnCC = ArrayUtil.log(couplingConstants, -999.);

        stimTab = getStimulationTable();
        stimtargets = vgrid.getAreaIndexes(stimTab.getTargetIDs());

        // workspace for the calculation
        wkA = new int[nel][nspec];
        wkB = new int[nel][nspec];
        wkReac = new int[nreaction];

        double[][] regcon = getRegionConcentrations();
        double[][] regsd = getRegionSurfaceDensities();

        // apply initial conditions over the grid
        for (int i = 0; i < nel; i++) {
            double v = volumes[i];
            double[] rcs = regcon[eltregions[i]];

            for (int j = 0; j < nspec; j++)
                wkA[i][j] = wkB[i][j] = randomRound(v * rcs[j] * PARTICLES_PUVC);

            double a = surfaceAreas[i];
            double[] scs = regsd[eltregions[i]];
            if (a > 0 && scs != null) {
                for (int j = 0; j < nspec; j++)
                    if (Double.isNaN(scs[j])) {
                        // means not specified by the user;
                    } else
                        wkA[i][j] = wkB[i][j] = this.randomRound(a * scs[j] * PARTICLES_PUASD);
            }

            /*
             * if (i % 20 == 0) { E.info("elt " + i + " region " + eltregions[i]
             * + " n0 " + wkA[i][0]); }
             */
        }

        if (sdRun.initialStateFile != null) {
            double[][] cc = readInitialState(sdRun.initialStateFile, nel, nspec, speciesIDs);
            if (cc != null) {
                for (int i = 0; i < nel; i++) {
                    for (int j = 0; j < nspec; j++) {
                        int np = (int) Math.round(cc[i][j] * volumes[i] / NM_PER_PARTICLE_PUV);
                        wkA[i][j] = np;
                        wkB[i][j] = np;
                    }
                }
            }
        }

        dt = sdRun.fixedStepDt;
        lndt = Math.log(dt);

        // final things we need is something to generate particle numbers
        // for steps of given n, p
        if (useBinomial())
            interpSG = InterpolatingStepGenerator.getBinomialGenerator();
        else if (usePoisson()) {
            interpSG = InterpolatingStepGenerator.getPoissonGenerator();
        } else {
            E.error("unknown probability distribution");
        }

        if (doShared() || doParticle() || doIndependent()) {
            if (doShared()) {
                E.info("Using SHARED destination allocation");
            } else {
                E.info("Using PER PARTICLE destination allocation");
            }
            lnpSharedOut = new double[nel][nspec];
            pSharedOut = new double[nel][nspec];
            fSharedExit = new double[nel][nspec][];

            int maxnn = 0;
            for (int iel = 0; iel < nel; iel++) {
                for (int k = 0; k < nspec; k++) {
                    int nn = neighbors[iel].length;
                    fSharedExit[iel][k] = new double[nn];
                    if (nn > maxnn) {
                        maxnn = nn;
                    }
                }
            }
            E.info("max no of neighbors for a single element is " + maxnn);

            for (int iel = 0; iel < nel; iel++) {
                for (int k = 0; k < nspec; k++) {
                    int inbr[] = neighbors[iel];
                    double lngnbr[] = lnCC[iel];
                    int nnbr = inbr.length;
                    // int np0 = wkA[iel][k];

                    double ptot = 0.;
                    double[] pcnbr = new double[nnbr];

                    for (int j = 0; j < nnbr; j++) {
                        double lnpgo = lnfdiff[k] + lngnbr[j] + lndt - lnvolumes[iel];
                        // probability is dt * K_diff * contact_area /
                        // (center_to_center_distance * source_volume)
                        // gnbr contains the gometry: contact_area / distance

                        double p = Math.exp(lnpgo);
                        ptot += p;
                        pcnbr[j] = ptot;
                    }

                    double lnptot = Math.log(ptot);
                    if (lnptot > -1.) {
                        // WK 9 11 2007
                        System.out.println("WK===================================");
                        System.out.println("In DIFFUSION: probability TOO HIGH!");
                        System.out.println("Reduce your timestep, and try again...");
                        System.out.println("WK====================================");
                        System.exit(3);

                        /*
                         * if (nwarn < 4) {
                         * E.warning("p too large at element " + iel +
                         * " species " + k + " - capping from " +
                         * Math.exp(lnptot) + " to " + Math.exp(-1.)); nwarn++;
                         * } lnptot= -1.;
                         */
                        // WK
                    }

                    pSharedOut[iel][k] = ptot;
                    lnpSharedOut[iel][k] = lnptot;
                    for (int j = 0; j < nnbr; j++) {
                        fSharedExit[iel][k][j] = pcnbr[j] / ptot;
                    }
                }
            }
        }
    }

    public final int run() {
        init();

        assert this.resultWriters.size() > 0;

        double time = sdRun.getStartTime();
        double endtime = sdRun.getEndTime();

        for(ResultWriter resultWriter: this.resultWriters)
            resultWriter.writeGrid(vgrid, sdRun.getStartTime(), fnmsOut, this);

        E.info("Running from time=" + time + "ms to time=" + endtime + "ms");

        double tlog = 5.;

        long startTime = System.currentTimeMillis();

        // int iwr = 0;
        double writeTime = time - 1.e-9;

        double[] writeTimeArray = new double[fnmsOut.length];
        Arrays.fill(writeTimeArray, -1.e-9);

        while (time < endtime) {

            if (time >= writeTime) {
                for(ResultWriter resultWriter: this.resultWriters)
                    resultWriter.writeGridConcs(time, nel, ispecout, this);

                writeTime += sdRun.outputInterval;
            }
            for (int i = 0; i < fnmsOut.length; i++) {
                if (time >= writeTimeArray[i]) {
                    for(ResultWriter resultWriter: this.resultWriters)
                        resultWriter.writeGridConcsDumb(i, time, nel, fnmsOut[i], this);
                    writeTimeArray[i] += Double.valueOf(dtsOut[i]);
                }
            }

            time += advance(time);

            if (time > tlog) {
                E.info("time " + time + " dt=" + dt);
                tlog += Math.max(50 * sdRun.outputInterval, 5);
            }

            if (time >= stateSaveTime) {
                for(ResultWriter resultWriter: this.resultWriters)
                    resultWriter.saveState(time, sdRun.stateSavePrefix, getStateText());
                stateSaveTime += sdRun.getStateSaveInterval();
            }
        }

        long endTime = System.currentTimeMillis();
        E.info("total time " + (endTime - startTime) + "ms");
        return 0;

    }

    protected int calculateNgo(String where, int n, double exp){
        final double g = random.gaussian(), r = random.random();
        final String msg;
        final int ngo;

        if (useBinomial()) {
            if (n * exp < NP) {
                ngo = StepGenerator.gaussianStep(n, exp, g, r, random.poisson(n * exp), NP);
                msg = "n*exp < " + NP;
            } else {
                ngo = StepGenerator.gaussianStep(n, exp, g, r);
                msg = "n*exp >= " + NP;
            }
        } else {
            ngo = StepGenerator.poissonStep(n, exp, g, r);
            msg = "not using binomial";
        }

        if (ngo >= 0)
            return ngo;

        log.warn("{} with {}: ngo is NEGATIVE (ngo={}, n={}, Math.exp(lnp)={})",
                 where, msg, ngo, n, exp);
        return 0;
    }

    // NB the following method is one of the only two that need optimizing
    // (the other is nGo in the interpolating step generator)
    // things to do (in the c version)
    // - use BLAS calls for array operations,
    // - remove the two remaining exps
    // - unwrap inner conditionals for different reaction types
    // - make nGo inlinable

    public double advance(double tnow) {

        // add in any injections
        double[][] stims = stimTab.getStimsForInterval(tnow, dt);
        for (int i = 0; i < stims.length; i++) {
            double[] astim = stims[i];
            for (int j = 0; j < astim.length; j++) {
                if (astim[j] > 0.) {
                    // the stimulus could be spread over a number of elements
                    // as yet, assume equal probability of entering any of these
                    // elements (TODO)
                    // the random < asr ensures we get the right number of
                    // particles even the average entry per volume is less than
                    // one
                    // TODO - allow stim type (deterministic or poisson etc) in
                    // config;

                    int nk = stimtargets[i].length;
                    if (nk > 0) {
                        double as = astim[j] / nk;

                        for (int k = 0; k < nk; k++) {
                            int nin = this.randomRound(as);
                            ninjected += nin;
                            wkA[stimtargets[i][k]][j] += nin;
                        }
                    }
                }
            }
        }

        // initialize wkB to the current values.
        // It will hold the midstep values for the leapfrog, after diffusion
        // but before reactions.
        ArrayUtil.copy(wkA, wkB);

        // diffusion step;
        for (int iel = 0; iel < nel; iel++) {

            for (int k = 0; k < nspec; k++) {
                if (lnfdiff[k] > -90) {

                    int np0 = wkA[iel][k];

                    if (np0 > 0) {

                        switch(algoID) {
                        case INDEPENDENT:
                            // WK 8 28 2007
                            // parallelDiffusionStep(iel, k);
                            parallelAndSharedDiffusionStep(iel, k);
                            // WK
                            break;
                        case SHARED:
                            // WK 9 11 2007
                            // sharedDiffusionStep(iel, k);
                            parallelAndSharedDiffusionStep(iel, k);
                            // WK
                            break;
                        case PARTICLE:
                            particleDiffusionStep(iel, k);
                            break;

                        default:
                            assert false;
                        }
                    }
                }
            }
        }

        // for the reaction step, the source array is wkB and the
        // destination is wkA
        ArrayUtil.copy(wkB, wkA);

        // reaction step;
        for (int iel = 0; iel < nel; iel++) {
            double lnvol = lnvolumes[iel];

            // start and end quantities for each species in a single
            // volume
            int[] nstart = wkB[iel];
            int[] nend = wkA[iel];
            for (int isp = 0; isp < nspecie; isp++) {
                nend[isp] = nstart[isp];
            }

            for (int ireac = 0; ireac < nreaction; ireac++) {
                // total number of possible reactions is the number of
                // particles in the smallest reactant population

                int[] ri = reactantIndices[ireac];
                int[] pi = productIndices[ireac];

                int[] rs = reactantStochiometry[ireac];
                int[] ps = productStochiometry[ireac];

                double lnp = lnrates[ireac] + lndt;

                int n = nstart[ri[0]];

                if (ri[1] >= 0) {
                    int nk = nstart[ri[1]];

                    if (nk < n) {
                        lnp += intlog(n);
                        n = nk;
                    } else {
                        lnp += intlog(nk);
                    }
                    lnp -= lnvol;
                    lnp -= LN_PARTICLES_PUVC;
                }

                if (lnp > -1.) {
                    if (nwarn < 5) {
                        E.warning("p too large at element " + iel + " reaction " + ireac + " capping from "
                                       + Math.exp(lnp) + " to " + " exp(-1.)");
                        nwarn++;
                    }
                    lnp = -1.;
                }

                if (n <= 0) {

                } else {
                    int ngo = 0;
                    if (n == 1)
                        // TODO use table to get rid of exp
                        ngo = (random.random() < Math.exp(lnp) ? 1 : 0);
                    else if (n < StepGenerator.NMAX_STOCHASTIC)
                        ngo = interpSG.nGo(n, lnp, random.random());
                    else
                        ngo = this.calculateNgo("advance(reaction)", n, Math.exp(lnp));

                    // WK 7 2 2008: if ngo is negative, exit.
                    // if (ngo < 0)
                    // {
                    // System.out.println("in advance: ngo is NEGATIVE. Exiting...");
                    // System.exit(3);
                    // }
                    // WK

                    // update the new quantities in npn;
                    int ri0 = ri[0];
                    int ri1 = ri[1];
                    int rs0 = rs[0];
                    int rs1 = rs[1];

                    int navail = nend[ri0] / rs[0];
                    // AB changed navail > nend[ri1] / rs1 to navail < nend[ri1]
                    // / rs1
                    if (ri1 >= 0 && navail > nend[ri1] / rs1)
                        navail = nend[ri1] / rs1;

                    if (ngo > navail) {
                        // TODO as for diffusion, we've got more particles going
                        // than there actually are. Should regenerate all
                        // reactions on theis element
                        // or use a binomial to share them out
                        // or use a smaller timestep;

                        if (nwarn < 10) {
                            E.warning("reaction " + ireac + " ran out of particles - need " + ngo + " but have "
                                           + navail);
                            nwarn++;
                        }

                        ngo = navail;

                    }

                    // WK 9 25 2007: setting inc/decrements (i.e., ngo*xxx) to
                    // zero explicitly
                    // to avoid floating point error
                    if (ngo == 0) {
                        int pi0 = pi[0];
                        int pi1 = pi[1];
                        nend[ri0] -= 0;
                        if (ri1 >= 0)
                            nend[ri1] -= 0;
                        nend[pi0] += 0;
                        if (pi1 >= 0)
                            nend[pi1] += 0;
                    } else {
                        // WK
                        nend[ri0] -= ngo * rs0;

                        if (ri1 >= 0) {
                            nend[ri1] -= ngo * rs1;
                        }
                        // WK 3/16/2010
                        if (nend[ri0] < 0) {
                            System.out.println("nend[ri0] is NEGATIVE!");
                        }
                        if (ri1 >= 0 && nend[ri1] < 0) {
                            System.out.println("nend[ri1] is NEGATIVE!");
                        }
                        // WK

                        int pi0 = pi[0];
                        int pi1 = pi[1];

                        nend[pi0] += ngo * ps[0];
                        if (pi1 >= 0) {
                            nend[pi1] += ngo * ps[1];
                        }
                    }

                    // TODO this "if (ri[1] >= 0)" business is not great
                    // it applies for the A+B->C case, where there is a
                    // second reactant. We could probably do better by
                    // unrolling the four cases into separate blocks according
                    // to the reaction type
                    // - a good case for code generation.
                }
            }
        }

        // now wkA contains the actual numbers again;
        return dt;
    }

    // WK 8 28 2007
    private final void parallelAndSharedDiffusionStep(int iel, int k) {
        int np0 = wkA[iel][k];
        int inbr[] = neighbors[iel];
        double[] fshare = fSharedExit[iel][k];

        int ngo;
        int ngo_remaining = 0; // for independent diffusion step      ***KTB edit - this is number of molecules not yet diffused
        int num_molecules_diffused_so_far = 0;

        if (np0 == 1) {
            ngo = (random.random() < pSharedOut[iel][k] ? 1 : 0);
        } else if (np0 < StepGenerator.NMAX_STOCHASTIC) {
            ngo = interpSG.nGo(np0, Math.log(pSharedOut[iel][k]), random.random());

            if (ngo < 0) {
                System.out.println("in parallelAndSharedDiffusionStep 1st else: ngo is NEGATIVE. Exiting...");
                System.exit(3);
            }
        } else {
            ngo = this.calculateNgo("parallelAndSharedDiffusionStep", np0, pSharedOut[iel][k]);
        }

        assert ngo >= 0;

        // WK
        // if (ngo < (# of neighbors)*SHARED_DIFF_PARTICLES) then do
        // shared_diffusion
        // else then do independent_diffusion
        if (ngo <= (inbr.length) * SHARED_DIFF_PARTICLES) // SHARED diffusion
        {
            wkB[iel][k] -= ngo;
            for (int i = 0; i < ngo; i++) {
                double r = random.random();
                int io = 0;
                while (r > fshare[io]) {
                    io++;
                }

                wkB[inbr[io]][k] += 1;
            }
        } else // MULTINOMIAL diffusion
        {
            ngo_remaining = ngo;         //**KTB  ngo_remaining is number of particles not yet diffused.  initially this is ngo
            //KTB 09-23-2011, use multi-nomial instead of separate binomials to calculate lnpgo, from ngo_remaining
            // WK 9 11 2007
            double prev = 0;
            for (int j = 0; j < inbr.length - 1; j++) {
                //double lnpgo = Math.log(fSharedExit[iel][k][j] - prev);  (KTB) old method - INDEPENDENT
                //BHK and KTB implemented the multinomial using tables instead of gaussianStep for small N, and use symmetry of binomial 09/23/11
                double pgoTmp = (fSharedExit[iel][k][j] - prev)/(fSharedExit[iel][k][inbr.length-1]-prev);
                double lnpgo;
                if ((pgoTmp > 0.5) && (ngo_remaining < StepGenerator.NMAX_STOCHASTIC))
                    lnpgo = Math.log(1.0-pgoTmp);
                else
                    lnpgo = Math.log(pgoTmp);

                prev = fSharedExit[iel][k][j];

                //This next section uses the tables KTB
                if (ngo_remaining < StepGenerator.NMAX_STOCHASTIC) {
                    if (ngo_remaining == 1)
                    {
                        ngo = (random.random() < fSharedExit[iel][k][j] ? 1 : 0); //2011 BHK for ngo_remaining == 1
                    }
                    else if (ngo_remaining == 0) // 2011 BHK, occaisionally will run out of particles on 2nd to last neighbor
                    {
                        ngo = 0;
                    }
                    else
                    {
                        if (pgoTmp <= 0.5)
                            ngo = interpSG.nGo(ngo_remaining, lnpgo, random.random()); // 2011 BHK
                        else  //pgoTmp > 0.5
                            ngo = ngo_remaining - interpSG.nGo(ngo_remaining, lnpgo, random.random());
                    }
                }
                else if (ngo_remaining * Math.exp(lnpgo) < NP) {
                    ngo = StepGenerator.gaussianStep(ngo_remaining, Math.exp(lnpgo), random.gaussian(), random.random(),
                                                     random.poisson(ngo_remaining * Math.exp(lnpgo)), NP);
                } else {
                    ngo = StepGenerator.gaussianStep(ngo_remaining, Math.exp(lnpgo), random.gaussian(), random.random());
                }
                if (ngo < 0) {
                    ngo = 0;
                    System.out.println("in parallelAndSharedDiffusionStep, MULTINOMIAL: ngo is NEGATIVE.");
                    System.out.println("ngo: " + ngo + " ngo_remaining: " + ngo_remaining + "pgoTmp " + pgoTmp);
                }
                else if (ngo > ngo_remaining) {
                    if (nngowarn < 10)
                    {
                        System.out.println("in parallelAndSharedDiffusionStep, MULTINOMIAL: ngo > ngo_remaining, setting ngo=ngo_remaining ");
                    }
                    ngo=ngo_remaining; //BHK 2011
                    nngowarn++;
                }
                // WK

                wkB[iel][k] -= ngo;
                wkB[inbr[j]][k] += ngo;
                ngo_remaining-=ngo;
            } //end of loop through all but last neighbor

            ngo = ngo_remaining;

            wkB[iel][k] -= ngo;
            wkB[inbr[inbr.length - 1]][k] += ngo;
            // WK 3/16/2010
            if (wkB[iel][k] < 0) {
                System.out.println("In INDEPENDENT DIFFUSION, wkB[iel][k] NEGATIVE!!!");
            }
            //

        }
    }

    // WK

    private final void parallelDiffusionStep(int iel, int k) {
        int inbr[] = neighbors[iel];
        double lngnbr[] = lnCC[iel];
        int nnbr = inbr.length;
        int np0 = wkA[iel][k];

        for (int j = 0; j < nnbr; j++) {
            // use logs here so the operations are all additions
            // and the compiler should be able to be clever

            double lnpgo = lnfdiff[k] + lngnbr[j] + lndt - lnvolumes[iel];
            // probability is dt * K_diff * contact_area /
            // (center_to_center_distance * source_volume)
            // gnbr contains the gometry: contact_area / distance

            if (lnpgo > -1.) {
                if (nwarn < 4) {
                    E.warning("p too large at element " + iel + " transition " + j + " to  " + inbr[j]
                                   + " - capping " + Math.exp(lnpgo) + " coupling is " + lngnbr[j]);
                    nwarn++;
                }
                lnpgo = -1.;
            }

            int ngo = 0;
            if (np0 == 1) {
                // TODO - use table anyway - avoid exp!
                ngo = (random.random() < Math.exp(lnpgo) ? 1 : 0);
            } else if (np0 < StepGenerator.NMAX_STOCHASTIC) {
                ngo = interpSG.nGo(np0, lnpgo, random.random());
            } else {
                if (useBinomial()) {
                    if (np0 * (Math.exp(lnpgo)) >= 10) {
                        ngo = StepGenerator.gaussianStep(np0, Math.exp(lnpgo), random.gaussian(), random.random());
                    } else {
                        ngo = StepGenerator.gaussianStep(np0, Math.exp(lnpgo), random.gaussian(), random.random(),
                                                         random.poisson(np0 * (Math.exp(lnpgo))), NP);
                    }
                } else {
                    ngo = StepGenerator.poissonStep(np0, Math.exp(lnpgo), random.gaussian(), random.random());
                }
            }

            // System.out.println("iel j ngo " + iel + " " + j + " " + ngo + " "
            // + np0);

            if (ngo > wkB[iel][k]) {
                if (nwarn < 10) {
                    E.warning("ran out of particles - curtailing last transition from " + ngo + " to "
                                   + wkB[iel][k] + " leaving point " + iel + " species " + k);
                } else if (nwarn == 10) {
                    E.info("Suppressing future warnings");
                }
                nwarn++;

                ngo = wkB[iel][k];
                // TODO probably worth flagging if this ever happens
                // it means your steps could be too large
                // MATH if it does happen, there is a consistent
                // bias in that the last exit is the one that
                // is curtailed. We should actually restart
                // this set of jumps and get new fluxes to all
                // neighbours
            }

            wkB[iel][k] -= ngo;
            wkB[inbr[j]][k] += ngo;
        }
    }

    private final void sharedDiffusionStep(int iel, int k) {

        int np0 = wkA[iel][k];
        int inbr[] = neighbors[iel];
        // int nnbr = inbr.length;
        double[] fshare = fSharedExit[iel][k];
        double lnptot = lnpSharedOut[iel][k];

        int ngo = 0;
        if (np0 == 1) {
            // TODO - use table anyway - avoid exp!
            ngo = (random.random() < Math.exp(lnptot) ? 1 : 0);

        } else if (np0 < StepGenerator.NMAX_STOCHASTIC) {
            ngo = interpSG.nGo(np0, lnptot, random.random());

        } else {
            if (useBinomial()) {
                ngo = StepGenerator.gaussianStep(np0, Math.exp(lnptot), random.gaussian(), random.random());
            } else {
                ngo = StepGenerator.poissonStep(np0, Math.exp(lnptot), random.gaussian(), random.random());
            }
        }

        wkB[iel][k] -= ngo;
        for (int i = 0; i < ngo; i++) {
            double r = random.random();
            int io = 0;
            while (r > fshare[io]) {
                io++;
            }
            wkB[inbr[io]][k] += 1;
        }
    }

    private final void particleDiffusionStep(int iel, int k) {
        int np0 = wkA[iel][k];
        int inbr[] = neighbors[iel];
        // int nnbr = inbr.length;
        double[] fshare = fSharedExit[iel][k];
        double ptot = pSharedOut[iel][k];

        for (int i = 0; i < np0; i++) {
            double r = random.random();

            if (r < ptot) {
                wkB[iel][k] -= 1;
                double fr = r / ptot;
                int io = 0;
                while (fr > fshare[io]) {
                    io++;
                }
                wkB[inbr[io]][k] += 1;
            }
        }
    }

    final private static double[] intlogs = ArrayUtil.logArray(10000, -99);
    public final static double intlog(int i) {
        if (i <= 0)
            return intlogs[0];
        else
            return i < intlogs.length ? intlogs[i] : Math.log(i);
    }

    public long getParticleCount() {
        long ret = 0;
        for (int i = 0; i < nel; i++)
            for (int j = 0; j < nspec; j++)
                ret += wkA[i][j];

        E.info("number injected = " + ninjected);
        return ret;
    }

    @Override
    public int getGridPartNumb(int i, int outj) {
        return wkA[i][outj];
    }

    @Override
    public double getGridPartConc(int i, int outj) {
        int val = getGridPartNumb(i, outj);
        return val * NM_PER_PARTICLE_PUV / volumes[i];
    }

}
