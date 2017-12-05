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
import ij.gui.ShapeRoi;
import java.awt.Polygon;
import ij.gui.PolygonRoi;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import java.util.ArrayList;

public class RemoveOverlap {

    protected ArrayList<Roi> exec(ImagePlus imp, ArrayList<Roi> roiList) {
        ArrayList<Roi> roiListReturn = roiList;
        
        for (int i = 0; i < roiList.size(); i++) {

            ShapeRoi roi1 = new ShapeRoi(roiList.get(i));

            for (int j = i + 1; j < roiList.size(); j++) {
                ShapeRoi roi2 = new ShapeRoi(roiList.get(j));
                ShapeRoi overlap = roi1;
                overlap = overlap.and(roi2);
                roi1 = new ShapeRoi(roiList.get(i));

                if (overlap.getBounds().getHeight() != 0 && overlap.getBounds().getHeight() != 0
                        && roi1.getBounds().getHeight()>0 && roi1.getBounds().getHeight()>0
                        && roi2.getBounds().getHeight()>0 && roi2.getBounds().getHeight()>0) {
                    Polygon polygonConvexHull1 = roi1.getConvexHull();
                    Polygon polygonConvexHull2 = roi2.getConvexHull();
                    if (polygonConvexHull1!=null &&  polygonConvexHull2!=null) {

                        PolygonRoi convexHull1 = new PolygonRoi(polygonConvexHull1, Roi.POLYGON);
                        PolygonRoi convexHull2 = new PolygonRoi(polygonConvexHull2, Roi.POLYGON);
                        ShapeRoi test1 = new ShapeRoi(convexHull1).not(roi1);
                        ShapeRoi test2 = new ShapeRoi(convexHull2).not(roi2);

                        Analyzer.setMeasurements(0);
                        int measurements = Measurements.AREA;
                        ResultsTable rt = new ResultsTable();
                        Analyzer analyzer = new Analyzer(imp, measurements, rt);

                        imp.setRoi(test1);
                        analyzer.measure();
                        imp.killRoi();
                        double area1 = rt.getValueAsDouble(ResultsTable.AREA, 0);

                        imp.setRoi(test2);
                        analyzer.measure();
                        imp.killRoi();
                        double area2 = rt.getValueAsDouble(ResultsTable.AREA, 1);

                        if (area1 < area2) {
                            ShapeRoi newRoi1 = roi1.not(roi2);
                            roi1 = new ShapeRoi(roiList.get(i));                          
                            roiListReturn.set(i, newRoi1);
                        } else {
                            ShapeRoi newRoi2 = roi2.not(roi1);
                            roi2 = new ShapeRoi(roiList.get(j));
                            roiListReturn.set(j, newRoi2);
                        }
                    }
                }
            }
        }
        //Remove ROIs = Null (OriginalROI inside ROI is now assigned null)
        for(int i=0; i<roiListReturn.size();i++){
            if(roiListReturn.get(i).getBounds().getWidth()==0 && roiListReturn.get(i).getBounds().getHeight()==0){
                roiListReturn.remove(i);
                i--;
            }
        }
        return roiListReturn;
    }
}
