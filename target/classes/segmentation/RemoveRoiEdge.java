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
import java.awt.Rectangle;

public class RemoveRoiEdge {

    protected ArrayList<Roi> exec(ImagePlus imp, ArrayList<Roi> roiList) {
        int impWidth = imp.getWidth();
        int impHeight = imp.getHeight();
        ArrayList<Roi> roiListCheckedEdge = new ArrayList<Roi>();

        for (int i = 0; i < roiList.size(); i++) {
            Rectangle boundingRect = roiList.get(i).getBounds();
            if (boundingRect.getWidth() + boundingRect.x < impWidth - 1
                    && boundingRect.getHeight() + boundingRect.y < impHeight - 1
                    && boundingRect.x > 0
                    && boundingRect.y > 0) {
                roiListCheckedEdge.add(roiList.get(i));
            }
        }
        return roiListCheckedEdge;
    }
}
