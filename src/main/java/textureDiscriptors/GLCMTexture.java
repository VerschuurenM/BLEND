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
package textureDiscriptors;

import ij.*;
import ij.gui.*;
import ij.measure.Measurements;
import ij.process.*;
import java.awt.*;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import java.util.LinkedHashMap;

//==========================================================
public class GLCMTexture {

    static int d = 1;
    //static int phi = 0;
    static boolean symmetry = true;
    static boolean doASM = true;
    static boolean doContrast = true;
    static boolean doCorrelation = true;
    static boolean doIDM = true;
    static boolean doEntropy = true;
    static boolean doEnergy = true;
    static boolean doInertia = true;
    static boolean doHomogeneity = true;
    static boolean doProminence = true;
    static boolean doVariance = true;
    static boolean doShade = true;

    ResultsTable rt = ResultsTable.getResultsTable();

    public LinkedHashMap<String, Double> exec(ImagePlus imp, Roi inputRoi, String title) {
        double[][] textureArray = new double[4][11];
        LinkedHashMap<String, Double> textureDescriptors = new LinkedHashMap<String, Double>();

        rt.reset();

        //ImagePlus imp = IJ.getImage();
        imp.killRoi();
        ImagePlus impDup = imp.duplicate();

        //Intensity measurements
        int m = Measurements.MEAN;
        Analyzer analyzer = new Analyzer(impDup, m, rt);
        impDup.setRoi(inputRoi);
        analyzer.measure();
        imp.killRoi();
        textureDescriptors.put(title + "_Intensity", rt.getValueAsDouble(ResultsTable.MEAN, 0));

        rt.reset();
        imp.killRoi();
        impDup = imp.duplicate();

        IJ.run(impDup, "8-bit", "");

        //No normalization
        //ImageProcessor ip = impDup.getProcessor();        
        //ip.setRoi(inputRoi);
        //Normalize intensities 
        ImagePlus impNorm = imp.duplicate();
        FloatProcessor ip32 = impNorm.getProcessor().convertToFloatProcessor();
        ip32.setRoi(inputRoi);
        double meanRoi = ip32.getStatistics().mean;
        ip32.resetRoi();
        ip32.multiply(1 / meanRoi);
        impNorm.setProcessor(ip32);
        //impNorm.show();
        ip32.setMinAndMax(0.15, 2);
        ByteProcessor ip8 = ip32.convertToByteProcessor(true);
        impNorm.setProcessor(ip8);

        ImageProcessor ip = impNorm.getProcessor();

//Save normalized nuclei
//        if (label != "") {
//            Calibration calibration = impDup.getCalibration();
//            int dim = (int) (50.0 / calibration.pixelWidth);
//            ImagePlus impCropNorm = IJ.createImage("CropNorm", "8-bit Black", dim, dim, 1);
//            impCropNorm.setCalibration(calibration);
//            impNorm.setRoi(inputRoi);
//            impNorm.copy();
//            impCropNorm.paste();
//            impCropNorm.show();
//            String newFolder = folder + "CroppedImages/Normalized/";
//            (new File(newFolder)).mkdirs();
//            IJ.selectWindow("CropNorm");
//            String impTitleCrop = imp.getTitle() + ";" + roiIndex + ";" + label;
//            IJ.saveAs("tiff", newFolder + impTitleCrop);
//            impCropNorm.changes=false;
//            impCropNorm.close();
//            impNorm.killRoi();
//        }
        ip.setRoi(inputRoi);

        // use the bounding rectangle ROI to roughly limit processing
        Rectangle roi = ip.getRoi();

        // get byte arrays for the image pixels and mask pixels
        int width = ip.getWidth();
        int height = ip.getHeight();
        byte[] pixels = (byte[]) ip.getPixels();
        byte[] mask = ip.getMaskArray();

        // value = value at pixel of interest; dValue = value of pixel at offset    
        int value;
        int dValue;
        double totalPixels = roi.height * roi.width;
        if (symmetry) {
            totalPixels = totalPixels * 2;
        }
        double pixelProgress = 0;
        double pixelCount = 0;
        int pixelDepth = (int) Math.pow(2.0, (double) ip.getBitDepth());

        //====================================================================================================
        //compute the Gray Level Correlation Matrix
        int offsetX = 1;
        int offsetY = 0;
        double[][] glcm = new double[pixelDepth][pixelDepth];

        for (int phi = 0; phi <= 135; phi = phi + 45) {

            // set our offsets based on the selected angle
            if (phi == 0) {
                offsetX = d;
                offsetY = 0;
            } else if (phi == 45) {
                offsetX = d;
                offsetY = -d;
            } else if (phi == 90) {
                offsetX = 0;
                offsetY = -d;
            } else if (phi == 135) {
                offsetX = -d;
                offsetY = -d;
            } else {
                // the angle is not one of the options
                IJ.showMessage("The requested angle," + phi + ", is not one of the supported angles (0,45,90,135)");
            }

            // loop through the pixels in the ROI bounding rectangle
            for (int y = roi.y; y < (roi.y + roi.height); y++) {
                for (int x = roi.x; x < (roi.x + roi.width); x++) {
                    // check to see if the pixel is in the mask (if it exists)
                    if ((mask == null) || ((0xff & mask[(((y - roi.y) * roi.width) + (x - roi.x))]) > 0)) {
                        // check to see if the offset pixel is in the roi
                        int dx = x + offsetX;
                        int dy = y + offsetY;
                        if (((dx >= roi.x) && (dx < (roi.x + roi.width))) && ((dy >= roi.y) && (dy < (roi.y + roi.height)))) {
                            // check to see if the offset pixel is in the mask (if it exists) 
                            if ((mask == null) || ((0xff & mask[(((dy - roi.y) * roi.width) + (dx - roi.x))]) > 0)) {
                                value = 0xff & pixels[(y * width) + x];
                                dValue = 0xff & pixels[(dy * width) + dx];
                                glcm[value][dValue]++;
                                pixelCount++;
                            }
                            // if symmetry is selected, invert the offsets and go through the process again
                            if (symmetry) {
                                dx = x - offsetX;
                                dy = y - offsetY;
                                if (((dx >= roi.x) && (dx < (roi.x + roi.width))) && ((dy >= roi.y) && (dy < (roi.y + roi.height)))) {
                                    // check to see if the offset pixel is in the mask (if it exists) 
                                    if ((mask == null) || ((0xff & mask[(((dy - roi.y) * roi.width) + (dx - roi.x))]) > 0)) {
                                        value = 0xff & pixels[(y * width) + x];
                                        dValue = 0xff & pixels[(dy * width) + dx];
                                        glcm[dValue][value]++;
                                        pixelCount++;
                                    }
                                }
                            }
                        }
                    }
                    pixelProgress++;
                }
            }

            //=====================================================================================================
            //convert the GLCM from absolute counts to probabilities
            for (int i = 0; i < pixelDepth; i++) {
                for (int j = 0; j < pixelDepth; j++) {
                    glcm[i][j] = (glcm[i][j]) / (pixelCount);
                }
            }

            //=====================================================================================================
            //calculate meanx, meany, stdevx and stdevy for the glcm
            double[] px = new double[pixelDepth];
            double[] py = new double[pixelDepth];
            double meanx = 0.0;
            double meany = 0.0;
            double stdevx = 0.0;
            double stdevy = 0.0;

            // Px(i) and Py(j) are the marginal-probability matrix; sum rows (px) or columns (py) 
            // First, initialize the arrays to 0
            for (int i = 0; i < pixelDepth; i++) {
                px[i] = 0.0;
                py[i] = 0.0;
            }

            // sum the glcm rows to Px(i)
            for (int i = 0; i < pixelDepth; i++) {
                for (int j = 0; j < pixelDepth; j++) {
                    px[i] += glcm[i][j];
                }
            }

            // sum the glcm rows to Py(j)
            for (int j = 0; j < pixelDepth; j++) {
                for (int i = 0; i < pixelDepth; i++) {
                    py[j] += glcm[i][j];
                }
            }

            // calculate meanx and meany
            for (int i = 0; i < pixelDepth; i++) {
                meanx += (i * px[i]);
                meany += (i * py[i]);
            }

            // calculate stdevx and stdevy
            for (int i = 0; i < pixelDepth; i++) {
                stdevx += ((Math.pow((i - meanx), 2)) * px[i]);
                stdevy += ((Math.pow((i - meany), 2)) * py[i]);
            }

            int row = rt.getCounter();
            rt.incrementCounter();
            //=====================================================================================================
            //calculate the angular second moment (asm)

            if (doASM == true) {
                double asm = 0.0;
                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        asm += (glcm[i][j] * glcm[i][j]);
                    }
                }
                rt.setValue("Angular Second Moment", row, asm);
                textureArray[(int) (phi / 45)][0] = asm;
            }

            //===============================================================================================
            //calculate the inverse difference moment (idm) (Walker, et al. 1995)
            //this is calculated using the same formula as Conners, et al., 1984 "Local Homogeneity"
            if (doIDM == true) {
                double IDM = 0.0;
                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        IDM += ((1 / (1 + (Math.pow(i - j, 2)))) * glcm[i][j]);
                    }
                }
                rt.setValue("Inverse Difference Moment", row, IDM);
                textureArray[(int) (phi / 45)][1] = IDM;
            }

            //=====================================================================================================
            //calculate the contrast (Haralick, et al. 1973)
            //similar to the inertia, except abs(i-j) is used
            if (doContrast == true) {
                double contrast = 0.0;

                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        contrast += Math.pow(Math.abs(i - j), 2) * (glcm[i][j]);
                    }
                }
                rt.setValue("Contrast", row, contrast);
                textureArray[(int) (phi / 45)][2] = contrast;
            }

            //===============================================================================================
            //calculate the energy
            if (doEnergy == true) {
                double energy = 0.0;
                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        energy += Math.pow(glcm[i][j], 2);
                    }
                }
                rt.setValue("Energy", row, energy);
                textureArray[(int) (phi / 45)][3] = energy;
            }

            //===============================================================================================
            //calculate the entropy (Haralick et al., 1973; Walker, et al., 1995)
            if (doEntropy == true) {
                double entropy = 0.0;
                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        if (glcm[i][j] != 0) {
                            entropy = entropy - (glcm[i][j] * (Math.log(glcm[i][j])));
                            //the next line is how Xite calculates it -- I am not sure why they use this, I do not think it is correct
                            //(they also use log base 10, which I need to implement)
                            //entropy = entropy-(glcm[i][j]*((Math.log(glcm[i][j]))/Math.log(2.0)) );
                        }
                    }
                }
                rt.setValue("Entropy", row, entropy);
                textureArray[(int) (phi / 45)][4] = entropy;
            }
            //===============================================================================================
            //calculate the homogeneity (Parker)
            //"Local Homogeneity" from Conners, et al., 1984 is calculated the same as IDM above
            //Parker's implementation is below; absolute value of i-j is taken rather than square

            if (doHomogeneity == true) {
                double homogeneity = 0.0;
                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        homogeneity += glcm[i][j] / (1.0 + Math.abs(i - j));
                    }
                }
                rt.setValue("Homogeneity", row, homogeneity);
                textureArray[(int) (phi / 45)][5] = homogeneity;
            }
            //===============================================================================================
            //calculate the variance ("variance" in Walker 1995; "Sum of Squares: Variance" in Haralick 1973)

            if (doVariance == true) {
                double variance = 0.0;
                double mean = 0.0;

                mean = (meanx + meany) / 2;

                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        variance += (Math.pow((i - mean), 2) * glcm[i][j]);
                    }
                }
                rt.setValue("Variance", row, variance);
                textureArray[(int) (phi / 45)][6] = variance;
            }

            //===============================================================================================
            //calculate the shade (Walker, et al., 1995; Connors, et al. 1984)
            if (doShade == true) {
                double shade = 0.0;

                // calculate the shade parameter
                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        shade += (Math.pow((i + j - meanx - meany), 3) * glcm[i][j]);
                    }
                }
                rt.setValue("Shade", row, shade);
                textureArray[(int) (phi / 45)][7] = shade;
            }

            //==============================================================================================
            //calculate the prominence (Walker, et al., 1995; Connors, et al. 1984)
            if (doProminence == true) {

                double prominence = 0.0;

                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        prominence += (Math.pow((i + j - meanx - meany), 4) * glcm[i][j]);
                    }
                }
                rt.setValue("Prominence", row, prominence);
                textureArray[(int) (phi / 45)][8] = prominence;
            }

            //===============================================================================================
            //calculate the inertia (Walker, et al., 1995; Connors, et al. 1984)
            if (doInertia == true) {
                double inertia = 0.0;
                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        if (glcm[i][j] != 0) {
                            inertia += (Math.pow((i - j), 2) * glcm[i][j]);
                        }
                    }
                }
                rt.setValue("Inertia", row, inertia);
                textureArray[(int) (phi / 45)][9] = inertia;
            }
            //=====================================================================================================
            //calculate the correlation
            //methods based on Haralick 1973 (and MatLab), Walker 1995 are included below
            //Haralick/Matlab result reported for correlation currently; will give Walker as an option in the future

            if (doCorrelation == true) {
                double correlation = 0.0;

                // calculate the correlation parameter
                for (int i = 0; i < pixelDepth; i++) {
                    for (int j = 0; j < pixelDepth; j++) {
                        //Walker, et al. 1995 (matches Xite)
                        //correlation += ((((i-meanx)*(j-meany))/Math.sqrt(stdevx*stdevy))*glcm[i][j]);
                        //Haralick, et al. 1973 (continued below outside loop; matches original GLCM_Texture)
                        //correlation += (i*j)*glcm[i][j];
                        //matlab's rephrasing of Haralick 1973; produces the same result as Haralick 1973
                        correlation += ((((i - meanx) * (j - meany)) / (stdevx * stdevy)) * glcm[i][j]);
                    }
                }
                //Haralick, et al. 1973, original method continued.
                //correlation = (correlation -(meanx*meany))/(stdevx*stdevy);

                rt.setValue("Correlation", row, correlation);
                textureArray[(int) (phi / 45)][10] = correlation;
            }
        }

        String[] features = {"AngSecMoment", "InvDiffMoment", "Contrast", "Energy", "Entropy",
            "Homogeneity", "Variance", "Shade", "Prominence", "Inertia", "Correlation"};
        for (int j = 0; j < textureArray[0].length; j++) {
            double sumTextureDescriptor = 0;
            for (int i = 0; i < textureArray.length; i++) {
                sumTextureDescriptor = sumTextureDescriptor + textureArray[i][j];
            }
            double meanTextureDescriptor = sumTextureDescriptor / textureArray.length;
            textureDescriptors.put(title + "_" + features[j], meanTextureDescriptor);
        }
        return textureDescriptors;
    }
}
