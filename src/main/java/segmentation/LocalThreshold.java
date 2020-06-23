/*
 * BLEND: FIJI plugin for accurate detection of dysmorphic nuclei.
 * Copyright (C) 2016 Marlies Verschuuren, Jonas De Vylder, Hannes Catrysse, Wilfried Philips and Winnok H. De Vos
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package segmentation;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.util.ArrayList;

public class LocalThreshold {

    int impWidth;
    int impHeight;

    AutoThresholder.Method localThresholdMethod;

    protected float maxPathStrenght;
    protected float pathStrenght;
    protected float[][] edgeArrayCopy;
    protected boolean iterateDP;
    protected double[][][] correspondanceArray;
    Roi[] roiArray;
    ImagePlus imp;
    ArrayList<Roi> roiListLT = new ArrayList<Roi>();

    public LocalThreshold(AutoThresholder.Method localThresholdMethod, ImagePlus imp) {
        this.localThresholdMethod = localThresholdMethod;
        this.imp = imp;
    }

    protected ArrayList<Roi> exec(ImagePlus impBinary) {
        impWidth = imp.getWidth();
        impHeight = imp.getHeight();

        // Get ROIs from Binary Dilated image
        ThresholdToSelection ThresholdToSelectionObject = new ThresholdToSelection();
        impBinary.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
        Roi roiGlobal = ThresholdToSelectionObject.convert(impBinary.getProcessor());
        ShapeRoi roiGlobalShape = new ShapeRoi(roiGlobal);
        roiArray = roiGlobalShape.getRois();
        //Fix Bug .getRois()
        if (roiArray.length==1){
            roiArray = new Roi[]{roiGlobalShape};
        }

        // LocalThreshold in Dilated Roi
        for (int i = 0; i < roiArray.length; i++) {
            if (roiArray[i] != null) {
                //Get Threshold
                
                ImagePlus impDup = new Duplicator().run(imp);
                impDup.setTitle("Local");
                //Fix Bug getRois
                impDup.setRoi(roiArray[i]);                
                impDup.getProcessor().setAutoThreshold(localThresholdMethod, true);
                IJ.run(impDup, "Make Binary", "");
                // If roi is not entire image --> Fill black outside dilated Roi
                impDup.getProcessor().setColor(Color.BLACK);
                if (roiArray[i].getBounds().getWidth() < impWidth || roiArray[i].getBounds().getHeight() < impHeight) {
                 impDup.getProcessor().fillOutside(roiArray[i]);
                }
                
                //Reset roi
                impDup.killRoi();

                //Get new Roi
                impDup.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
                int [] hist = impDup.getProcessor().getHistogram();
                if(hist[hist.length-1]!=0){
                    //System.out.println(impDup.getProcessor().getHistogram());
                    Roi roi = ThresholdToSelectionObject.convert(impDup.getProcessor());
                    roiListLT.add(roi);
                }
                
            }

        }
        return roiListLT;
    }

    protected double[] getMeasurements() {
        double[] measure = new double[4];
        Analyzer.setMeasurements(0);
        int measurements = Measurements.MEAN + Measurements.STD_DEV;
        ResultsTable rt = new ResultsTable();
        Analyzer analyzer = new Analyzer(imp, measurements, rt);

        ShapeRoi foreground = new ShapeRoi(roiListLT.get(0));
        for (int i = 1; i < roiListLT.size(); i++) {
            foreground.or(new ShapeRoi(roiListLT.get(i)));
        }

        imp.setRoi(foreground);
        analyzer.measure();
        imp.killRoi();

        Roi entireImage = new Roi(0, 0, impWidth, impHeight);
        ShapeRoi background = (new ShapeRoi(entireImage)).not(new ShapeRoi(foreground));
        imp.setRoi(background);
        analyzer.measure();
        imp.killRoi();

        double meanForeGround = rt.getValueAsDouble(ResultsTable.MEAN, 0);
        double stdForeGround = rt.getValueAsDouble(ResultsTable.STD_DEV, 0);
        double meanBackGround = rt.getValueAsDouble(ResultsTable.MEAN, 1);
        double stdBackGround = rt.getValueAsDouble(ResultsTable.STD_DEV, 1);

        measure[0] = meanForeGround;
        measure[1] = stdForeGround;
        measure[2] = meanBackGround;
        measure[3] = stdBackGround;

        return measure;
    }

    protected Roi execRoi(ImagePlus imp, Roi roiBefore) {
        impWidth = imp.getWidth();
        impHeight = imp.getHeight();

        ImagePlus impDup = new Duplicator().run(imp);
        impDup.setTitle("Local");
        impDup.setRoi(roiBefore);
        impDup.getProcessor().setAutoThreshold(localThresholdMethod, true);
        IJ.run(impDup, "Make Binary", "");

        impDup.show();
        //impDup.getProcessor().fillOutside(rectangle);
        impDup.getProcessor().fillOutside(roiBefore);

        //Reset roi
        impDup.killRoi();

        //Add roi to roiListLT
        ThresholdToSelection ThresholdToSelectionObject = new ThresholdToSelection();
        impDup.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
        return ThresholdToSelectionObject.convert(impDup.getProcessor());

    }

}
