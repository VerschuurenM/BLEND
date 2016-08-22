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
import ij.plugin.ImageCalculator;
import ij.process.*;
import java.util.Arrays;

public class GradientRemoval {
    protected ImagePlus exec(ImagePlus imp, int divisions) {
        //Gradient removal: This algorithm tries suppress any gradual changes in the background intensity.
        //Dividing the image into 3x3 Roi-"superpixels" 
        //For each of these superpixels the median is taken which is than assumed to be the background intensity of that superpixels centroid.
        //These centroid intensities are then bilinear interpolated to achieve an image that represent the gradual changes is background intensity. 
        //That image of the estimated background is then subtracted from the original image to create a background corrected image. 

        int impWidth = imp.getWidth();
        int impHeight = imp.getHeight();

        //Get array of pixel values
        int[][] impArray = imp.getProcessor().getIntArray();

        // Divide witdth&height by divisions
        double impWidthDivided = ((double) impWidth / (double) divisions);
        double impHeightDivided = ((double) impHeight / (double) divisions);

        // New Roi [] with length = divisions*divisions
        Roi[] roiArray = new Roi[divisions * divisions];

        // For loop: Create Roi = "Superpixels" (x,y,width,height) in original image
        for (int i = 0; i < divisions; i++) {
            for (int j = 0; j < divisions; j++) {

                roiArray[i * divisions + j] = new Roi(
                        j * (int) impWidthDivided, i * (int) impHeightDivided,
                        (int) impWidthDivided, (int) impHeightDivided);
            }
        }
        // Create array for all the medians of the "superpixel"-roi
        int[] medianArray = new int[roiArray.length];
        // Set minMedian at 2^bithdepth
        int minMedian = (int) Math.pow(2, imp.getBitDepth());
        
        //Loop over all superpixels: calculate median background for every superpixel
        for (int i = 0; i < roiArray.length; i++) {
            int addWidth;
            int addHeight;
            
            // Calculate width & height superpixel; depends on position superpixel
            if(i==(divisions*divisions)-1){
                addWidth = (int) (Math.floor(impWidthDivided)) + impWidth % divisions - 1;
                addHeight = (int) (Math.floor(impHeightDivided)) + impHeight % divisions - 1;
            }
            else if(i/divisions >= divisions-1){
                addWidth = (int) Math.floor(impWidthDivided);
                addHeight = (int) (Math.floor(impHeightDivided)) + impHeight % divisions - 1;    
            }
            else if(i % divisions == divisions - 1){
                addWidth = (int) (Math.floor(impWidthDivided)) + impWidth % divisions - 1;
                addHeight = (int) Math.floor(impHeightDivided);
            }
            else{
                addWidth = (int) Math.floor(impWidthDivided);
                addHeight = (int) Math.floor(impHeightDivided);
            }

            // Get Coordinates
            int xCoordinate0 = roiArray[i].getBounds().x;
            int xCoordinate1 = roiArray[i].getBounds().x + addWidth;
            int yCoordinate0 = roiArray[i].getBounds().y;
            int yCoordinate1 = roiArray[i].getBounds().y + addHeight;
            
            // Set SuperPixel
            imp.setRoi(roiArray[i]);

            // Get autotreshold BASED ON ROI=Superpixel
            int threshold = imp.getProcessor().getAutoThreshold();
            
            // Get values background and calculate median
            if (threshold >= 0) {
                int[] localIntensityArray = new int[(addWidth + 1) * (addHeight + 1)];
                int inc = 0;
                // For loop: Pixel value < Treshold --> LocalIntensityArray
                for (int n = xCoordinate0; n <= xCoordinate1; n++) {
                    for (int m = yCoordinate0; m <= yCoordinate1; m++) {
                        if (impArray[n][m] < threshold) {
                            localIntensityArray[inc] = impArray[n][m];
                            inc += 1;
                        }
                    }
                }
                // Cut localIntensityArray + Sort --> Take Median  
                if (inc > 0) {
                    int[] localIntensityArrayCut = Arrays.copyOfRange(localIntensityArray, 0, inc);
                    Arrays.sort(localIntensityArrayCut);
                    medianArray[i] = localIntensityArrayCut[(int) (inc / 2.0)];
                } else {
                    medianArray[i] = 0;
                }
            } else {
                medianArray[i] = 0;
            }
            //Reset MinMedian
            if (minMedian > medianArray[i]) {
                minMedian = medianArray[i];
            }
        }

        int[][] thresholdBoundArray = new int[impWidth][impHeight];
        for (int i = 0; i < medianArray.length; i++) {
            int threshold = medianArray[i] - minMedian;
            int addWidth;
            int addHeight;

            // Calculate width & height superpixel; depends on position superpixel
            if(i==(divisions*divisions)-1){
                addWidth = (int) (Math.floor(impWidthDivided)) + impWidth % divisions - 1;
                addHeight = (int) (Math.floor(impHeightDivided)) + impHeight % divisions - 1;
            }
            else if(i/divisions >= divisions-1){
                addWidth = (int) Math.floor(impWidthDivided);
                addHeight = (int) (Math.floor(impHeightDivided)) + impHeight % divisions - 1;    
            }
            else if(i % divisions == divisions - 1){
                addWidth = (int) (Math.floor(impWidthDivided)) + impWidth % divisions - 1;
                addHeight = (int) Math.floor(impHeightDivided);
            }
            else{
                addWidth = (int) Math.floor(impWidthDivided);
                addHeight = (int) Math.floor(impHeightDivided);
            }
            
            // For loop over 4 corners off Roi-"Superpixel"
            // If value tresholdBoundArray (corner of Roi-"Superpixel") > 0 -> Set value: mean of threshold and value thresholdArray(corner of Roi-"Superpixel")
            // Else value tresholdBoundArray (corner of Roi-"Superpixel") = threshold
            for (int n = 0; n < 2; n++) {
                for (int m = 0; m < 2; m++) {
                    if (thresholdBoundArray[roiArray[i].getBounds().x + n * addWidth][roiArray[i].getBounds().y + m * addHeight] > 0) {
                        thresholdBoundArray[roiArray[i].getBounds().x + n * addWidth][roiArray[i].getBounds().y + m * addHeight]
                                = (threshold + thresholdBoundArray[roiArray[i].getBounds().x + n * addWidth][roiArray[i].getBounds().y + m * addHeight]) / 2;
                    } else {
                        thresholdBoundArray[roiArray[i].getBounds().x + n * addWidth][roiArray[i].getBounds().y + m * addHeight]
                                = threshold;
                    }
                }
            }
        }
        
        int[][] thresholdArray = new int[impWidth][impHeight];
        for (int i = 0; i < roiArray.length; i++) {
            int addWidth;
            int addHeight;
            
            // Calculate width & height superpixel; depends on position superpixel
            if(i==(divisions*divisions)-1){
                addWidth = (int) (Math.floor(impWidthDivided)) + impWidth % divisions - 1;
                addHeight = (int) (Math.floor(impHeightDivided)) + impHeight % divisions - 1;
            }
            else if(i/divisions >= divisions-1){
                addWidth = (int) Math.floor(impWidthDivided);
                addHeight = (int) (Math.floor(impHeightDivided)) + impHeight % divisions - 1;    
            }
            else if(i % divisions == divisions - 1){
                addWidth = (int) (Math.floor(impWidthDivided)) + impWidth % divisions - 1;
                addHeight = (int) Math.floor(impHeightDivided);
            }
            else{
                addWidth = (int) Math.floor(impWidthDivided);
                addHeight = (int) Math.floor(impHeightDivided);
            }
            // Get Coordinates of Roi-"Superpixel"
            int xCoordinate0 = roiArray[i].getBounds().x;
            int xCoordinate1 = roiArray[i].getBounds().x + addWidth;
            int yCoordinate0 = roiArray[i].getBounds().y;
            int yCoordinate1 = roiArray[i].getBounds().y + addHeight;

            //For-loop over all pixels in ROI-"Superpixel"
            for (int n = xCoordinate0; n <= xCoordinate1; n++) {
                for (int m = yCoordinate0; m <= yCoordinate1; m++) {
                    //AreaFraction: Area from pixel (n,m) to 4 corners Roi-"Superpixel"
                    double areaFraction1 = (double) Math.abs((n - xCoordinate1) * (m - yCoordinate1)) / (addWidth * addHeight);
                    double areaFraction2 = (double) Math.abs((n - xCoordinate0) * (m - yCoordinate1)) / (addWidth * addHeight);
                    double areaFraction3 = (double) Math.abs((n - xCoordinate1) * (m - yCoordinate0)) / (addWidth * addHeight);
                    double areaFraction4 = (double) Math.abs((n - xCoordinate0) * (m - yCoordinate0)) / (addWidth * addHeight);

                    //AreaFraction is weight given at ThresholdBoundArray ~ distance from corner Roi-"Superpixel"
                    double sumThreshold = thresholdBoundArray[xCoordinate0][yCoordinate0] * areaFraction1
                            + thresholdBoundArray[xCoordinate1][yCoordinate0] * areaFraction2
                            + thresholdBoundArray[xCoordinate0][yCoordinate1] * areaFraction3
                            + thresholdBoundArray[xCoordinate1][yCoordinate1] * areaFraction4;

                    if (thresholdArray[n][m] == 0) {
                        thresholdArray[n][m] = (int) sumThreshold;
                    } else {
                        thresholdArray[n][m] = (int) ((sumThreshold + (double) thresholdArray[n][m]) / 2.0);
                    }

                }
            }
            imp.deleteRoi();
        }
        
        //Substract gradient background from original image
        ImageProcessor ipThreshold = new ShortProcessor(impWidth, impHeight);
        ImagePlus impThreshold = new ImagePlus("Threshold", ipThreshold);
        impThreshold.getProcessor().setIntArray(thresholdArray);
        ImagePlus impWithoutGradient = new ImageCalculator().run("Subtract create", imp, impThreshold);
        impWithoutGradient.setTitle("impWithoutGradient");
        return impWithoutGradient;

    }
}
