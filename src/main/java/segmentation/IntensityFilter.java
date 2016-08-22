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

import ij.ImagePlus;
import ij.gui.Roi;
import java.util.ArrayList;
import ij.measure.*;
import ij.plugin.filter.Analyzer;

public class IntensityFilter {

    int impWidth;
    int impHeight;

    protected ArrayList<Roi> exec(ImagePlus imp, ArrayList<Roi> roiList,double[]measureFGBG) {
    //Some seperated ROIs are seen as one --> Split these combined ROIs
        Analyzer.setMeasurements(0);
        int measurements = Measurements.MEAN;
        Analyzer.setMeasurements(measurements);
        
        for(int i=0; i< roiList.size(); i++){
            ResultsTable rtRoi = new ResultsTable();
            Analyzer analyzerRoi = new Analyzer(imp, measurements, rtRoi);
            //imp.show()
            imp.setRoi(roiList.get(i));
            analyzerRoi.measure();
            imp.killRoi();
            double meanRoi = rtRoi.getValueAsDouble(ResultsTable.MEAN, 0);
            if (meanRoi < (measureFGBG[0]-2*measureFGBG[1])){
                roiList.remove(i);
                i--;
            }
        }   
        return roiList;
    }
}
