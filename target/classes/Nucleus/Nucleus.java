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

package Nucleus;

import java.util.*;
import ij.gui.*;

public class Nucleus {
    public double intNeun;
    public Roi roiNucleus;
    public LinkedHashMap index = new LinkedHashMap();
    public LinkedHashMap morpho = new LinkedHashMap();
    public LinkedHashMap textural = new LinkedHashMap();
    public String labelClass;
    
    public Nucleus(String image, Roi roi, int roiIndex){
        roiNucleus=roi;
        index.put("Image", image);
        index.put("Index", roiIndex);
        index.put("roiX", roi.getBounds().x);
        index.put("roiY", roi.getBounds().y);
        index.put("roiWidth", roi.getBounds().width);
        index.put("roiHeight", roi.getBounds().height);
    }
}
