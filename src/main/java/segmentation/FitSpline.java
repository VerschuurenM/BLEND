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

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;
import java.util.ArrayList;
import ij.ImagePlus;
import ij.measure.Calibration;

public class FitSpline {

    protected ArrayList<Roi> exec(ImagePlus imp, ArrayList<Roi> roiList) {
        Calibration cal = imp.getLocalCalibration();
        int fit = Math.round((float) (0.5) / (float) (cal.pixelWidth));
        ArrayList<Roi> roiListFitted = new ArrayList<Roi>(roiList.size());
        for(int i=0; i<roiList.size();i++){
            Roi roi = roiList.get(i);
            FloatPolygon interpolatedRoi = roi.getInterpolatedPolygon(fit, false);
            PolygonRoi polygonRoi= new PolygonRoi(interpolatedRoi.xpoints, interpolatedRoi.ypoints, Roi.POLYGON);
            polygonRoi.fitSpline();
            Roi roiFitted = polygonRoi;
            roiListFitted.add(roiFitted);
        }
        return roiListFitted;
    }
}
