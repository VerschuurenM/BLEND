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
package shapeDescriptors;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.measure.*;
import java.util.LinkedHashMap;

public class StandardImageJDescriptors {

    private int measurements;
    private Analyzer analyzer;
    String prefix;

    public StandardImageJDescriptors(String prefix) {
        this.prefix=prefix;
        measurements = Analyzer.getMeasurements();
        Analyzer.setMeasurements(0);
        measurements = Measurements.AREA + Measurements.PERIMETER + Measurements.ELLIPSE + Measurements.SHAPE_DESCRIPTORS + Measurements.FERET;
        Analyzer.setMeasurements(measurements);
        
    }

    public LinkedHashMap<String,Double> exec(ImagePlus imp, Roi roi) {
        LinkedHashMap<String,Double> standardDescriptors = new LinkedHashMap<String,Double>();
        
        imp.setRoi(roi);

        ResultsTable rt = new ResultsTable();
        analyzer = new Analyzer(imp, measurements, rt);
        analyzer.measure();

        standardDescriptors.put(prefix+"_Area", rt.getValueAsDouble(ResultsTable.AREA, 0));
        standardDescriptors.put(prefix+"_Perimeter", rt.getValueAsDouble(ResultsTable.PERIMETER, 0));
        standardDescriptors.put(prefix+"_Major", rt.getValueAsDouble(ResultsTable.MAJOR, 0));
        standardDescriptors.put(prefix+"_Minor", rt.getValueAsDouble(ResultsTable.MINOR, 0));
        standardDescriptors.put(prefix+"_Feret", rt.getValueAsDouble(ResultsTable.FERET, 0));
        standardDescriptors.put(prefix+"_MinFeret", rt.getValueAsDouble(ResultsTable.MIN_FERET, 0));
        standardDescriptors.put(prefix+"_Circularity", rt.getValueAsDouble(ResultsTable.CIRCULARITY, 0));
        standardDescriptors.put(prefix+"_AspectRatio", rt.getValueAsDouble(ResultsTable.ASPECT_RATIO, 0));
        standardDescriptors.put(prefix+"_Roundness", rt.getValueAsDouble(ResultsTable.ROUNDNESS, 0));
        standardDescriptors.put(prefix+"_Solidity", rt.getValueAsDouble(ResultsTable.SOLIDITY, 0));

        return standardDescriptors;
    }
}
