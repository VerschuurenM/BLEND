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

package segmentationComparison;

import Nucleus.Nucleus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.io.*;
import java.util.ArrayList;

public class RoiListComparison {

    ArrayList<ArrayList<ImagePlus[]>> listRoiMaskGT;
    String outputDir;
    String outputName;
    ArrayList<String> fileNames;
    String method1;
    String method2;
    ArrayList<Nucleus[]> listRoiArray;

    double[] similarityIndexImageArray;
    double[] overlapIndexImageArray;
    double[] extraFractionImageArray;
    double[] hddMaxImageArray;
    double[] hddSumImageArray;
    int impWidth;
    int impHeight;

    public RoiListComparison(ArrayList<ArrayList<ImagePlus[]>> listRoiMaskGT, int impWidth, int impHeight, ArrayList<Nucleus[]> listNuclei, String outputDir, String outputName, ArrayList<String> fileNames, String method1, String method2) {
        this.listRoiMaskGT = listRoiMaskGT;
        this.outputDir = outputDir;
        this.outputName = outputName;
        this.listRoiArray = listNuclei;
        this.method1 = method1;
        this.method2 = method2;
        this.fileNames = fileNames;
        this.impWidth = impWidth;
        this.impHeight = impHeight;
    }

    public void exec() {
        for (int imageIndex = 0; imageIndex < fileNames.size(); imageIndex++) {
            Nucleus[] nuclei = listRoiArray.get(imageIndex);
            //Score when no nuclei detected
            if (nuclei == null) {
                for (int GT = 0; GT < listRoiMaskGT.size(); GT++) {
                    String output = (fileNames.get(imageIndex) + ";" + impWidth + ";" + impHeight + ";" + GT + ";" + method1 + ";" + method2 + ";" + impWidth + ";" + impWidth + ";" + 0 + ";\n");
                    FileWrite(outputDir, outputName, output);
                }
            } else {
                ImagePlus[] maskArray = new ImagePlus[nuclei.length];
                for (int i = 0; i < nuclei.length; i++) {
                    maskArray[i] = createMask(nuclei[i].roiNucleus, impWidth, impHeight);
                }
                //Score no ROI or whenROI = entire image
                if (nuclei.length==0 || nuclei[0].roiNucleus.getBounds().getWidth() == impWidth && nuclei[0].roiNucleus.getBounds().getHeight() == impHeight) {
                    for (int GT = 0; GT < listRoiMaskGT.size(); GT++) {
                        for (int i = 0; i < listRoiMaskGT.get(GT).get(imageIndex).length; i++) {
                            ImagePlus maskGT = listRoiMaskGT.get(GT).get(imageIndex)[i];
                            maskGT.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
                            ThresholdToSelection ThresholdToSelectionObject = new ThresholdToSelection();
                            Roi roi = ThresholdToSelectionObject.convert(maskGT.getProcessor());
                            Rectangle rect = roi.getBounds();
                            int xCoord = rect.x;
                            int yCoord = rect.y;
                            String output = (fileNames.get(imageIndex) + ";" + xCoord + ";" + yCoord + ";" + GT + ";" + method1 + ";" + method2 + ";" + impWidth + ";" + impWidth + ";" + 0 + ";\n");
                            FileWrite(outputDir, outputName, output);
                        }
                    }
                } //Comparison when #detectedRois <= # roisGT
                else if (nuclei.length <= listRoiMaskGT.get(0).get(imageIndex).length) {
                    for (int GT = 0; GT < listRoiMaskGT.size(); GT++) {
                        for (int i = 0; i < listRoiMaskGT.get(GT).get(imageIndex).length; i++) {
                            ImagePlus maskGT = listRoiMaskGT.get(GT).get(imageIndex)[i];
                            int similarRoiIndex = 0;
                            double maxSimilarity = 0;
                            for (int j = 0; j < maskArray.length; j++) {
                                ImagePlus mask = maskArray[j];
                                ImagePlus impIntersection = new ImageCalculator().run("AND create", maskGT, mask);
                                int areaRoiGT = maskGT.getStatistics().histogram[((int) maskGT.getStatistics().histMax)];
                                int areaRoi = maskArray[j].getStatistics().histogram[((int) mask.getStatistics().histMax)];
                                int areaIntersection = impIntersection.getStatistics().histogram[((int) impIntersection.getStatistics().histMax)];
                                double similarity = (double) (2 * areaIntersection) / (double) (areaRoi + areaRoiGT);
                                if (similarity >= maxSimilarity) {
                                    maxSimilarity = similarity;
                                    similarRoiIndex = j;
                                }
                            }
                            ImagePlus mask = maskArray[similarRoiIndex];
                            compare(mask, maskGT, imageIndex, GT);
                        }
                    }
                } //Comparison when #detectedRois > # roisGT
                else {
                    for (int i = 0; i < nuclei.length; i++) {
                        ImagePlus mask = maskArray[i];
                        for (int GT = 0; GT < listRoiMaskGT.size(); GT++) {
                            int similarRoiIndex = 0;
                            double maxSimilarity = 0;
                            for (int j = 0; j < listRoiMaskGT.get(GT).get(imageIndex).length; j++) {
                                ImagePlus maskGT = listRoiMaskGT.get(GT).get(imageIndex)[j];
                                ImagePlus impIntersection = new ImageCalculator().run("AND create", maskGT, mask);
                                int areaRoiGT = maskGT.getStatistics().histogram[((int) maskGT.getStatistics().histMax)];
                                int areaRoi = maskArray[j].getStatistics().histogram[((int) mask.getStatistics().histMax)];
                                int areaIntersection = impIntersection.getStatistics().histogram[((int) impIntersection.getStatistics().histMax)];
                                double similarity = (double) (2 * areaIntersection) / (double) (areaRoi + areaRoiGT);
                                if (similarity >= maxSimilarity) {
                                    maxSimilarity = similarity;
                                    similarRoiIndex = j;
                                }
                            }
                            ImagePlus maskGT = listRoiMaskGT.get(GT).get(imageIndex)[similarRoiIndex];
                            compare(mask, maskGT, imageIndex, GT);
                        }
                    }
                }
            }
        }
    }

    private void compare(ImagePlus mask1, ImagePlus mask2, int imageIndex, int GT) {
        HausdorffDistance HDD = new HausdorffDistance();
        DiceCoeficient DC = new DiceCoeficient();

        mask2.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
        ThresholdToSelection ThresholdToSelectionObject = new ThresholdToSelection();
        Roi roi = ThresholdToSelectionObject.convert(mask2.getProcessor());
        Rectangle rect = roi.getBounds();
        int xCoord = rect.x;
        int yCoord = rect.y;

        DC.exec(mask1, mask2);
        double si = DC.getSimilarityIndex();
        HDD.exec(mask1, mask2);
        double hd = HDD.getHausdorffDistance();
        double ahd = HDD.getAveragedHausdorffDistance();

        String output = (fileNames.get(imageIndex) + ";" + xCoord + ";" + yCoord + ";" + GT + ";" + method1 + ";" + method2 + ";" + hd + ";" + ahd + ";" + si + ";\n");
        FileWrite(outputDir, outputName, output);
    }

    private ImagePlus createMask(Roi roi, int impWidth, int impHeight) {

        ImagePlus impMask = IJ.createImage("Mask", "8-bit Black", impWidth, impHeight, 1);
        ImageProcessor ipMask = impMask.getProcessor();
        ipMask.setRoi(roi);
        ipMask.setValue(255);
        ipMask.fill(ipMask.getMask());
        impMask.updateAndDraw();

        return impMask;

    }

    public void FileWrite(String outputDirectory, String fileName, String text) {
        try {
            // Create file 
            FileWriter fstream = new FileWriter(outputDirectory + fileName, true);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(text);
            //Close the output stream
            out.close();
        } catch (Exception e) {//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
    }

}
