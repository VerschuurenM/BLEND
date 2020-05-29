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
import ij.gui.*;
import ij.measure.Measurements;
import ij.plugin.Duplicator;
import ij.measure.ResultsTable;
import ij.plugin.filter.*;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;

public class GlobalThreshold {

    ImagePlus impBinary;
    ImagePlus impOriginal;
    Roi foreground;
    int impWidth;
    int impHeight;

    protected ImagePlus exec(ImagePlus imp, AutoThresholder.Method globalThresholdMethod) {
        impOriginal = new Duplicator().run(imp);
        impBinary = new Duplicator().run(imp);
        impBinary.setTitle("Global");
        impWidth = imp.getWidth();
        impHeight = imp.getHeight();
        impBinary.getProcessor().setAutoThreshold(globalThresholdMethod, true);
        IJ.run(impBinary,"Make Binary","");
        return impBinary;
    }

    protected double [] getMeasurements() {    
    //Get Measurements Mean & STD_DEV from foreground & background
        
        double[] measure = new double[4];
        
        ThresholdToSelection TTS = new ThresholdToSelection();
        impBinary.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
        Roi foreground = TTS.convert(impBinary.getProcessor());
        
        Analyzer.setMeasurements(0);
        int measurements = Measurements.MEAN + Measurements.STD_DEV;
        ResultsTable rt = new ResultsTable();
        Analyzer analyzer = new Analyzer(impOriginal, measurements, rt);
        
        impOriginal.setRoi(foreground);
        analyzer.measure();
        impOriginal.killRoi();
        
        Roi entireImage = new Roi(0, 0, impWidth, impHeight);
        ShapeRoi background = (new ShapeRoi(entireImage)).not(new ShapeRoi(foreground));
        impOriginal.setRoi(background);
        analyzer.measure();
        impOriginal.killRoi();

        double meanForeGround = rt.getValueAsDouble(ResultsTable.MEAN, 0);
        double stdForeGround = rt.getValueAsDouble(ResultsTable.STD_DEV, 0);
        double meanBackGround = rt.getValueAsDouble(ResultsTable.MEAN, 1);
        double stdBackGround = rt.getValueAsDouble(ResultsTable.STD_DEV, 1);

        measure[0]=meanForeGround;
        measure[1]=stdForeGround;
        measure[2]=meanBackGround;
        measure[3]=stdBackGround;

        return measure;
    }
}
