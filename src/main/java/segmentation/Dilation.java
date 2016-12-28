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
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.EDM;
import ij.process.ImageProcessor;

public class Dilation {

    protected ImagePlus exec(ImagePlus impBinary, int dilations) {
        //Dilate ROIs in Voronoi-cel
        ImagePlus impVoronoi=voronoi(impBinary);

        ImagePlus impDilated = new Duplicator().run(impBinary);
        for (int i = 0; i < dilations; i++) {
            //Dilate
            impDilated.getProcessor().erode();
            //Dilation in Voronoi-cell only: AND-operation
            impDilated = new ImageCalculator().run("AND create", impDilated, impVoronoi);

        }
        impDilated.setTitle("impDilated");
        return impDilated;
    }

    protected ImagePlus voronoi (ImagePlus imp) {
    //Creating an Euclidean Distance Map or EDM = greyscale representation of the distance of every pixel to the nearest foreground pixel= belonging to the segmented nuclei. 
        //The points that are saddle points and located in the background of the globally thresholded image, are points that make up the borders of the regions where the ROI of each nucleus can grow in. 
        //These points can be seen as the "ridges". 

        //Duplicate imp
        ImagePlus impVoronoi = new Duplicator().run(imp);
        EDM getVoronoi = new EDM();
        //Output = BYTE_OVERWRITE
        EDM.setOutputType(0);

        //Prepare for processing: String name + imagePlus impVoronoi
        getVoronoi.setup("voronoi", impVoronoi);
        getVoronoi.run(impVoronoi.getProcessor());

        
        //Apply threshold = 0 --> Binary Image
        impVoronoi.getProcessor().setThreshold(1, Math.pow(2,impVoronoi.getBitDepth()), ImageProcessor.RED_LUT);
        IJ.run(impVoronoi, "Make Binary", "");
        //Invert impVoronoi 
        impVoronoi.getProcessor().invert();
        impVoronoi.setTitle("impVoronoi");
        return impVoronoi;
    }
}
