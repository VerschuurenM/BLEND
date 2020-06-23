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
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import java.awt.Color;

public class AreaFilterPre {
    protected ImagePlus exec(ImagePlus imp, double minArea) {
        ThresholdToSelection ThresholdToSelectionObject = new ThresholdToSelection();
        ImagePlus impAreaFilter = IJ.createImage("impAreaFilter", "8-bit black", imp.getWidth(), imp.getHeight(), 1);
        imp.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);

        // Roi: all foreground pixels
        Roi roiGlobal = ThresholdToSelectionObject.convert(imp.getProcessor());

        //Convert ShapeRoi into RoiArray (Seperate Rois)
        ShapeRoi roiGlobalShape = new ShapeRoi(roiGlobal);
        Roi[] roiArray = roiGlobalShape.getRois();
        //Fix Bug .getRois()
        if (roiArray.length==1){
            roiArray = new Roi[]{roiGlobalShape};
        }
        
        //Measurements
        Analyzer.setMeasurements(0);
        int measurements = Measurements.AREA;
        //Set White Color
        Color white = new Color(255, 255, 255);
        impAreaFilter.getProcessor().setColor(white);
        //Measure and fill binary
        for(int i=0; i< roiArray.length; i++){
            ResultsTable rt = new ResultsTable();
            Analyzer analyzer = new Analyzer(imp, measurements, rt);
            //imp.show()
            imp.setRoi(roiArray[i]); 
            analyzer.measure();
            imp.killRoi();
            double areaRoi = rt.getValueAsDouble(ResultsTable.AREA, 0);
            if (areaRoi > minArea) {
                impAreaFilter.getProcessor().fill(roiArray[i]);
            }
        } 
        
        /*
        //Remove small ROIs < 5% maxPerimeter
        //maxLength is maximum perimeter of Roi
        double maxLength = 0;
        for (int i = 0; i < roiArray.length; i++) {
            if (roiArray[i].getLength() > maxLength) {
                maxLength = roiArray[i].getLength();
            }
        }

        //Set White Color
        Color white = new Color(255, 255, 255);
        impAreaFilter.getProcessor().setColor(white);

        //Set threshold for perimeter at 5% of maxLength --> fill Roi in impAreaFilter
        for (int i = 0; i < roiArray.length; i++) {
            if (roiArray[i].getLength() > maxLength / 100 * 5) {
                impAreaFilter.getProcessor().fill(roiArray[i]);
            }
        }
        //*/
        return impAreaFilter;
    }
}
