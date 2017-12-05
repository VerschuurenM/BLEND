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
import java.util.ArrayList;

public class CompositeFilter {

    int impWidth;
    int impHeight;

    protected ArrayList<Roi> exec(ImagePlus imp, ArrayList<Roi> roiList) {
    //Some seperated ROIs are seen as one --> Split these combined ROIs
        
        impWidth = imp.getWidth();
        impHeight = imp.getHeight();
        
        ArrayList<Roi> roiListReturn = new ArrayList<Roi>();
        for (int roiNr = 0; roiNr < roiList.size(); roiNr++) {

            ShapeRoi roiCombinedShape = new ShapeRoi(roiList.get(roiNr));
            Roi[] roiArrayLocal = roiCombinedShape.getRois();
            
            ShapeRoi mainRoiShape = null;

            double maxLength = 0;
            //For loop over Combined Rois -> Search voor Roi with largest bounding rectangle
            //Set this Roi as main-Roi
            for (int i = 0; i < roiArrayLocal.length; i++) {
                if (roiArrayLocal[i].getLength() > maxLength && roiArrayLocal[i].getLength() > 10) {
                    maxLength = roiArrayLocal[i].getLength();
                    ShapeRoi maxShapeRoi = new ShapeRoi(roiArrayLocal[i]);
                    mainRoiShape = maxShapeRoi;
                }
            }

            //For loop over Combined Rois 
            //Perimeter >=15% maxLenght & < maxLength--> Add Seperate Roi to RoiList
            for (int i = 0; i < roiArrayLocal.length; i++) {
                    if (roiArrayLocal[i].getLength() >= maxLength / 100 * 15 && roiArrayLocal[i].getLength() < maxLength) {
                    roiListReturn.add(roiArrayLocal[i]);
                }
            }
            if (mainRoiShape != null) {
                roiListReturn.add(mainRoiShape);
            }
        }
        return roiListReturn;

    }

}
