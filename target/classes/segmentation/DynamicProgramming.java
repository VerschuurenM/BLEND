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
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;

public class DynamicProgramming {

    protected float pathStrenght;
    protected double fuzzyBorder;

    protected boolean[][] exec(float[][] edgeArray, boolean createImage) {
    //Determine where Nucleus is located: upper/lowerhalf of SE
        if(createImage==true){
            createImage(edgeArray, "edgeArrayBefore");
        }
        FloatProcessor edge = new FloatProcessor(edgeArray);
        boolean nucleiUpperLimit = false;
        int sumUpperHalf = 0;
        int sumLowerHalf = 0;
        for (int j = 0; j < edgeArray.length; j++) {
            for (int i = 0; i < edgeArray[j].length; i++) {
                if (i < edgeArray[j].length / 2) {
                    sumUpperHalf += edgeArray[j][i];
                } else {
                    sumLowerHalf += edgeArray[j][i];
                }
            }
        }
        if (sumUpperHalf > sumLowerHalf) {
            nucleiUpperLimit = true;
        }
   
    // Take first unidirectional derivative; depends on boolean nulceiUpperlimit     
        FloatProcessor edgeDerivativeY = edge;
        if (nucleiUpperLimit == true) {
            float[] kernel = {(float) (1), (float) (2), (float) (1), (float) (0), (float) (0), (float) (0), (float) (-1), (float) (-2), (float) (-1)};
            edgeDerivativeY.convolve(kernel, 3, 3);
        } else {
            float[] kernel = {(float) (-1), (float) (-2), (float) (-1), (float) (0), (float) (0), (float) (0), (float) (1), (float) (2), (float) (1)};
            edgeDerivativeY.convolve(kernel, 3, 3);
        }
        float[][] edgeDerivativeYArray = edgeDerivativeY.getFloatArray();
        
        if(createImage==true){
        createImage(edgeDerivativeYArray, "1st derivative");
        }

    //Normalise each element in function of the max element of the column
        float[][] edgeDerivativeArrayNormalised = new float[edgeDerivativeYArray.length][edgeDerivativeYArray[0].length];
        for (int j = 0; j < edgeDerivativeYArray.length; j++) {
            float columnMax = 0;
            for (int i = 0; i < edgeDerivativeYArray[j].length; i++) {
                if (columnMax < edgeDerivativeYArray[j][i]) {
                    columnMax = edgeDerivativeYArray[j][i];
                }
            }
            for (int i = 0; i < edgeDerivativeYArray[j].length; i++) {
                edgeDerivativeArrayNormalised[j][i] = edgeDerivativeYArray[j][i] / columnMax;
            }
        }

    //Input array P = edgeArray; Output array Q = DynamicProgrammingStep1Array; IterationStep array T = iterationsArray
        //createImage(edgeArray, "edgeArrayNormalize");
        int[][] iterationsArray = new int[edgeDerivativeArrayNormalised.length][edgeDerivativeArrayNormalised[0].length];
        float[][] DynamicProgrammingStep1Array = new float[edgeDerivativeArrayNormalised.length][edgeDerivativeArrayNormalised[0].length];
        
        //Fill 0th column of DynamicProgrammingStep1Array (values) and the iterationsArray (steps)
        for (int i = 0; i < edgeDerivativeArrayNormalised[0].length; i++) {
            DynamicProgrammingStep1Array[0][i] = edgeDerivativeArrayNormalised[0][i];
            iterationsArray[0][i] = 1;
        }
        
        // From 1ste element on second column up to last element of the last column
        for (int j = 1; j < edgeDerivativeArrayNormalised.length; j++) {
            for (int i = 0; i < edgeDerivativeArrayNormalised[j].length; i++) {
                float maxValue = 0;
                int maxIterations = 0;

                //Calculate average path intensity for n paths going to edgeArray[i][j]
                //Each path exist out of 2 parts: 
                    //First column to element of column i-1
                    //Element of column i-1 to element p(i,j)
                for (int n = 0; n < edgeDerivativeArrayNormalised[j - 1].length; n++) {
                    float avarageValue = edgeDerivativeArrayNormalised[j][i] + DynamicProgrammingStep1Array[j - 1][n];
                    int iterations = 1;
                    if (n > i) {
                        for (int p = i; p < n; p++) {
                            avarageValue += edgeDerivativeArrayNormalised[j - 1][p];
                            iterations += 1;
                        }
                    } else if (n < i) {
                        for (int p = n + 1; p <= i; p++) {

                            avarageValue += edgeDerivativeArrayNormalised[j - 1][p];
                            iterations += 1;
                        }
                    }

                    int totalIterations = iterations + iterationsArray[j - 1][n];
                    avarageValue = avarageValue / (float) totalIterations;
                    if (maxValue < avarageValue) {
                        maxValue = avarageValue;
                        maxIterations = totalIterations;
                    }
                }
                DynamicProgrammingStep1Array[j][i] = maxValue * maxIterations;
                iterationsArray[j][i] = maxIterations;
            }
        }
    
    //Q/T
        //createImage(DynamicProgrammingStep1Array, "DP");
        for (int j = 1; j < DynamicProgrammingStep1Array.length; j++) {
            for (int i = 0; i < DynamicProgrammingStep1Array[j].length; i++) {
                DynamicProgrammingStep1Array[j][i] = DynamicProgrammingStep1Array[j][i] / iterationsArray[j][i];
            }
        }

        //createImage(DynamicProgrammingStep1Array, "DP/iteration");
        boolean[][] maxColumnBoolean = new boolean[DynamicProgrammingStep1Array.length][DynamicProgrammingStep1Array[0].length];
        //float[][] maxColumn127 = new float[DynamicProgrammingStep1Array.length][DynamicProgrammingStep1Array[0].length];
    
    //Find max in every column --> optimal path
    //Pathstrength is max in last column
        int maxRow = 0;
        float maxValue = 0;
        float sumIntPath = 0;
        float sumIntDerivativePath = 0;
        for (int row = 0; row < DynamicProgrammingStep1Array[0].length; row++) {
            if (maxValue < DynamicProgrammingStep1Array[DynamicProgrammingStep1Array.length - 1][row]) {
                maxValue = DynamicProgrammingStep1Array[DynamicProgrammingStep1Array.length - 1][row];
                pathStrenght = DynamicProgrammingStep1Array[DynamicProgrammingStep1Array.length - 1][row];
                maxRow = row;
            }
        }
        maxColumnBoolean[DynamicProgrammingStep1Array.length - 1][maxRow] = true;
        
    //Search in all rows of previous column (>45°)
        for (int j=0; j<DynamicProgrammingStep1Array.length - 1; j++) {
            maxValue = 0;
            for (int row = 0; row < DynamicProgrammingStep1Array[j].length; row++) {
                if (maxValue < DynamicProgrammingStep1Array[j][row]) {
                    maxValue = DynamicProgrammingStep1Array[j][row];
                    maxRow = row;
                }
            }
            //avarageIntensityPath += maxValue;
            maxColumnBoolean[j][maxRow] = true;
        }
        
        if(createImage==true){
            createImageFromBoolean(maxColumnBoolean, "Optimal");
        }
        return maxColumnBoolean;

    }
    protected void createImage(float[][] edgeArray, String title) {
        ImageProcessor ip = new FloatProcessor(edgeArray.length, edgeArray[0].length);
        ImagePlus imp = new ImagePlus(title, ip);
        imp.getProcessor().setFloatArray(edgeArray);
        imp.show();
    }

    protected void createImageFromBoolean(boolean[][] booleanPathArray, String title) {
        float[][] pathArray = new float[booleanPathArray.length][booleanPathArray[0].length];
        for (int j = 0; j < booleanPathArray.length; j++) {
            for (int i = 0; i < booleanPathArray[0].length; i++) {
                if (booleanPathArray[j][i] == true) {
                    pathArray[j][i] = (float) (127);
                } else {
                    pathArray[j][i] = (float) (0);
                }
            }
        }
        createImage(pathArray, title);
    }

    public float getPathStrenght() {
        return pathStrenght;
    }

}
