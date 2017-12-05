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

import java.util.ArrayList;
import ij.gui.Roi;
import ij.ImagePlus;
import ij.measure.*;
import ij.plugin.filter.Analyzer;

public class AreaFilterPost {

    protected ArrayList<Roi> exec(ImagePlus imp, ArrayList<Roi> roiList, double minArea, double maxArea) {
        ArrayList<Roi> roiListChecked = new ArrayList<Roi>();

        Analyzer.setMeasurements(0);
        int measurements = Measurements.AREA;
                
        for (int i = 0; i < roiList.size(); i++) {
            if(roiList.get(i).getBounds().getWidth()>0 && roiList.get(i).getBounds().getHeight()>0 ){
                imp.setRoi(roiList.get(i));
                ResultsTable rt = new ResultsTable();
                Analyzer analyzer = new Analyzer(imp, measurements, rt);
                analyzer.measure();
                imp.killRoi();
                double area = rt.getValueAsDouble(ResultsTable.AREA, 0);
                if(area>minArea & area<maxArea){
                    roiListChecked.add(roiList.get(i));
                }
            } 
        }
        return roiListChecked;
    }
}
