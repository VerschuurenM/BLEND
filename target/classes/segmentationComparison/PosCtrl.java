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

import java.io.*;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.RoiDecoder;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.Arrays;

public class PosCtrl {

    ArrayList<String> GTDirectory;
    String outputDirectory;
    String outputName = "PosCtrl.txt";
    double[] siArray;
    double[] oiArray;
    double[] efArray;
    double[] hdArray;
    double[] ahdArray;
    int impWidth;
    int impHeight;

    public PosCtrl(ArrayList<String> GTDirectory, String outputDirectory, int impWidth, int impHeight) {
        this.GTDirectory = GTDirectory;
        this.outputDirectory = outputDirectory;
        this.impWidth = impWidth;
        this.impHeight = impHeight;
    }

    public void exec() {
        String outputText = ("Image;xCoord;yCoord;GT1;GT2;HausdorffDist;AveragedHausdorffDist;SimilarityIndex;\n");
        FileWrite(outputDirectory, outputName, outputText);

        for (int GT1 = 0; GT1 < GTDirectory.size(); GT1++) {
            String[] fileNames = new File(GTDirectory.get(GT1)).list();
            for (int GT2 = 0; GT2 < GTDirectory.size(); GT2++) {
                IJ.log("GT: "+(GT1+1)+"/"+GTDirectory.size()+" vs GT: "+(GT2+1)+"/"+GTDirectory.size());
                for (int imageIndex = 0; imageIndex < fileNames.length; imageIndex++) {
                    String[] roiSources = new File(GTDirectory.get(GT1)).list();
                    Arrays.sort(roiSources);
                    Roi[] roiArray1 = (GetGTRois(GTDirectory.get(GT1) + roiSources[imageIndex]));
                    ImagePlus[] roiMask1 = new ImagePlus[roiArray1.length];
                    for (int i = 0; i < roiArray1.length; i++) {
                        if (roiArray1[i].getBounds().getWidth() * roiArray1[i].getBounds().getHeight() > 0) {
                            roiMask1[i] = createMask(roiArray1[i], impWidth, impHeight);
                        }
                    }

                    roiSources = new File(GTDirectory.get(GT2)).list();
                    Arrays.sort(roiSources);
                    Roi[] roiArray2 = (GetGTRois(GTDirectory.get(GT2) + roiSources[imageIndex]));
                    ImagePlus[] roiMask2 = new ImagePlus[roiArray2.length];
                    for (int i = 0; i < roiArray2.length; i++) {
                        if (roiArray2[i].getBounds().getWidth() * roiArray2[i].getBounds().getHeight() > 0) {
                            roiMask2[i] = createMask(roiArray2[i], impWidth, impHeight);
                        }
                    }

                    compare(roiMask1, roiMask2, GT1, GT2, imageIndex, fileNames);
                }
            }
        }
    }

    private void compare(ImagePlus[] arrayRoiMaskA, ImagePlus[] arrayRoiMaskB, int GT1, int GT2, int imageIndex, String[] fileNames) {
        HausdorffDistance HDD = new HausdorffDistance();
        DiceCoeficient DC = new DiceCoeficient();
        for (int roiIndex = 0; roiIndex < arrayRoiMaskA.length; roiIndex++) {
            ImagePlus roiMaskA = arrayRoiMaskA[roiIndex];
            int similarRoiIndex = 0;
            double maxSimilarity = 0;
            for (int i = 0; i < arrayRoiMaskB.length; i++) {
                ImagePlus impIntersection = new ImageCalculator().run("AND create", arrayRoiMaskB[i], roiMaskA);
                int areaROI = roiMaskA.getStatistics().histogram[((int) roiMaskA.getStatistics().histMax)];
                int areaRoiGT = arrayRoiMaskB[i].getStatistics().histogram[((int) arrayRoiMaskB[i].getStatistics().histMax)];
                int areaIntersection = impIntersection.getStatistics().histogram[((int) impIntersection.getStatistics().histMax)];
                double similarity = (double) (2 * areaIntersection) / (double) (areaROI + areaRoiGT);
                if (similarity >= maxSimilarity) {
                    maxSimilarity = similarity;
                    similarRoiIndex = i;
                }
            }
            ImagePlus roiMaskB = arrayRoiMaskB[similarRoiIndex];
            DC.exec(roiMaskA, roiMaskB);
            double si = DC.getSimilarityIndex();
            HDD.exec(roiMaskA, roiMaskB);
            double hd = HDD.getHausdorffDistance();
            double ahd = HDD.getAveragedHausdorffDistance();

            roiMaskA.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            ThresholdToSelection ThresholdToSelectionObject = new ThresholdToSelection();
            Roi roi = ThresholdToSelectionObject.convert(roiMaskA.getProcessor());
            Rectangle rect = roi.getBounds();
            int xCoord = rect.x;
            int yCoord = rect.y;

            String output = (fileNames[imageIndex] + ";" + xCoord + ";" + yCoord + ";" + GT1 + ";" + GT2 + ";" + hd + ";" + ahd + ";" + si + ";\n");
            FileWrite(outputDirectory, outputName, output);
        }
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

    private Roi[] GetGTRois(String groundThruthPath) {

        ArrayList<Roi> roiList = new ArrayList<Roi>();
        ZipInputStream in = null;
        ByteArrayOutputStream out;
        //int nRois = 0; 
        try {
            in = new ZipInputStream(new FileInputStream(groundThruthPath));
            byte[] buf = new byte[1024];
            int len;
            ZipEntry entry = in.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                if (name.endsWith(".roi")) {
                    out = new ByteArrayOutputStream();
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.close();
                    byte[] bytes = out.toByteArray();
                    RoiDecoder rd = new RoiDecoder(bytes, name);
                    Roi roi = rd.getRoi();
                    if (roi != null) {
                        roiList.add(roi);
                        //nRois++;
                    }
                }
                entry = in.getNextEntry();
            }
            in.close();
        } catch (IOException e) {
            System.out.println(e.toString());
        }
        Roi[] roiArrayGT = new Roi[roiList.size()];
        roiArrayGT = roiList.toArray(roiArrayGT);
        return roiArrayGT;
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
