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

import java.util.LinkedHashMap;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import java.util.ArrayList;

public class ShapeDescriptors {

    double[] curvatureSettings = {0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5};
    double curvatureMin = 0.5;
    double curvatureMax = 1.5;
    int fourierDescriptor = 50;

    public LinkedHashMap<String, Double> exec(ImagePlus imp, Roi roi, String prefix) {

        LinkedHashMap<String, Double> results = new LinkedHashMap<String, Double>();

        StandardImageJDescriptors StandardImageJDescriptorsObject = new StandardImageJDescriptors(prefix);
        Curvature CurvatureObject = new Curvature();
        EllipticFD EllipticFDObject = new EllipticFD(fourierDescriptor, prefix);

        LinkedHashMap<String, Double> standardImageJDescriptors = StandardImageJDescriptorsObject.exec(imp, roi);

        LinkedHashMap<String, Double> curvatureDescriptors = new LinkedHashMap<String, Double>();
        Calibration cal = imp.getLocalCalibration();
        ArrayList<Integer> curvature = new ArrayList<Integer>();
        ArrayList<Double> curvatureMicron = new ArrayList<Double>();
        for (int i = 0; i < curvatureSettings.length; i++) {
            int temp = Math.round((float) curvatureSettings[i] / (float) (cal.pixelWidth));
            if (i > 0) {
                if (temp != curvature.get(curvature.size() - 1)) {
                    curvature.add(temp);
                    curvatureMicron.add(curvatureSettings[i]);
                }
            } else {
                curvature.add(temp);
                curvatureMicron.add(curvatureSettings[i]);
            }
        }
        for (int i = 0; i < curvature.size(); i++) {
            double curv = CurvatureObject.exec(roi, curvature.get(i));
            curvatureDescriptors.put("Morph_Curvature_" + Double.toString(curvatureMicron.get(i)), curv);
        }

        LinkedHashMap<String, Double> ellipticFourierDescriptors = EllipticFDObject.exec(roi);

        results.putAll(standardImageJDescriptors);
        results.putAll(curvatureDescriptors);
        results.putAll(ellipticFourierDescriptors);

        return results;
    }

    private void Stop() {
        System.exit(0);
    }

}
