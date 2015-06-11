package org.textensor.stochdiff;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.textensor.report.E;
import org.textensor.stochdiff.model.SDRun;
import org.textensor.stochdiff.model.SDRunWrapper;
import org.textensor.stochdiff.numeric.BaseCalc;
import org.textensor.stochdiff.numeric.grid.ResultWriter;
import org.textensor.stochdiff.numeric.grid.ResultWriterText;
import org.textensor.stochdiff.numeric.grid.ResultWriterHDF5;

import org.textensor.util.Settings;
import org.textensor.util.inst;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SDCalc {
    static final Logger log = LogManager.getLogger(SDCalc.class);

    final SDRun sdRun;

    final String[] writers = Settings.getPropertyList("stochdiff.writers", "text");
    final int trials = Settings.getProperty("stochdiff.trials", 1);
    final int threads = Settings.getProperty("stochdiff.threads", 0);

    protected final List<ResultWriter> resultWriters = inst.newArrayList();

    public SDCalc(SDRun sdr, File output) {
        this.sdRun = sdr;

        if (trials > 1 && sdr.simulationSeed > 0) {
            log.warn("Ignoring fixed simulation seed");
            sdr.simulationSeed = 0;
        }

        for (String type: writers) {
            final ResultWriter writer;
            if (type.equals("text")) {
                writer = new ResultWriterText(output, sdr, sdr.getOutputSets(), sdr.getSpecies(), false);
                log.info("Using text writer for {}", writer.outputFile());
            } else if (type.equals("h5")) {
                writer = new ResultWriterHDF5(output);
                log.info("Using HDF5 writer for {}", writer.outputFile());
            } else {
                log.error("Unknown writer '{}'", type);
                throw new RuntimeException("uknown writer: " + type);
            }
            this.resultWriters.add(writer);
        }

        //        if (sdRun.continueOutput() && outputFile.exists() && sdRun.getStartTime() > 0)
        //            resultWriter.pruneFrom("gridConcentrations", 3, sdRun.getStartTime());
    }

    protected BaseCalc prepareCalc(int trial, SDRunWrapper wrapper) {
        SDCalcType calculationType = SDCalcType.valueOf(wrapper.sdRun.calculation);
        BaseCalc calc = calculationType.getCalc(trial, wrapper);
        for (ResultWriter resultWriter: this.resultWriters)
                calc.addResultWriter(resultWriter);
        return calc;
    }

    public void run() {
        SDRunWrapper wrapper = new SDRunWrapper(this.sdRun);
        log.info("Wrapper is ready, beginning calculations");

        if (trials == 1)
            this.prepareCalc(0, wrapper).run();
        else {
            int poolSize = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
            ExecutorService pool = Executors.newFixedThreadPool(poolSize);
            log.info("Running with pool {}", pool);

            for (int i = 0; i < trials; i++) {
                log.info("Starting trial {}", i);
                pool.execute(this.prepareCalc(i, wrapper));
            }

            log.info("Executing shutdown of pool {}", pool);
            pool.shutdown();
            while (true) {
                try {
                    pool.awaitTermination(1, TimeUnit.MINUTES);
                    return;
                } catch(InterruptedException e) {
                    log.info("Waiting: {}", pool);
                }
            }
        }
    }
}
