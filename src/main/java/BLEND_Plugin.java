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

import Nucleus.Nucleus;
import classification.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import GUI.*;
import segmentationComparison.*;
import segmentation.*;
import ij.IJ;
import ij.Prefs;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.Overlay;
import ij.gui.Toolbar;
import ij.io.RoiDecoder;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.MacroInstaller;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.plugin.frame.RoiManager;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import ij.process.LUT;
import java.awt.Color;
import java.awt.event.KeyEvent;

public class BLEND_Plugin implements PlugIn {

    ArrayList<String> GTDirectory;
    String inputDirectory;
    double calibration;
    int channel;
    int nChannels;
    int zProjection;
    String outputDirectory;
    String roiListDirectory;

    boolean backgroundSubtraction;
    double sizeRollingBall;
    int indexFilter;
    double radiusFilter;

    double minArea;

    double watershed;
    boolean refinement;
    boolean saveResultImages;
    boolean saveCroppedNuclei;
    boolean showDebugImages;
    boolean showResultImages;
    boolean extractFeatures;
    boolean scanThresholds;
    boolean posCtrl;

    int impWidth;
    int impHeight;

    double dilationMicron = 3.0;
    double rangeEdgeMicron = 2.0;
    double profileWatershedMicron = 3.0;
    boolean twoPass;
    AutoThresholder.Method globalThresholdMethod;
    AutoThresholder.Method localThresholdMethod;
    int functionality;

    AutoThresholder.Method[] thresholdMethods = {AutoThresholder.Method.Huang, AutoThresholder.Method.IJ_IsoData, AutoThresholder.Method.Intermodes,
        AutoThresholder.Method.IsoData, AutoThresholder.Method.Li, AutoThresholder.Method.MaxEntropy, AutoThresholder.Method.Mean,
        AutoThresholder.Method.MinError, AutoThresholder.Method.Minimum, AutoThresholder.Method.Moments, AutoThresholder.Method.Otsu,
        AutoThresholder.Method.Percentile, AutoThresholder.Method.RenyiEntropy, AutoThresholder.Method.Shanbhag,
        AutoThresholder.Method.Triangle, AutoThresholder.Method.Yen};
    String[] thresholds = {"Huang", "IJ_IsoData", "Intermodes", "IsoData", "Li", "MaxEntropy", "Mean",
        "MinError", "Minimum", "Moment", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"};

    public void run(String arg) {
        Prefs.blackBackground=true;

        //Get all configuration-parameters from config-file
        SettingsGUI ICN = new SettingsGUI();
        getAll(ICN);

        //Functionality = 0: Segmentation
        if (functionality == 0) {
            IJ.log("-----------------------------------");
            IJ.log("BLEND - Functionality: Segmentation - START");
            IJ.log("-----------------------------------");
            String newFolder = createFolder();
            //New SegmentationObject
            Segmentation SegmentationObject = new Segmentation(showDebugImages, backgroundSubtraction, sizeRollingBall, indexFilter, radiusFilter, twoPass, globalThresholdMethod, localThresholdMethod, refinement, watershed, minArea);
            //Array with all the names of the image-files in the inputdirectory
            ArrayList<String> imageArray = getImageArray(inputDirectory);
            //Loop over every image -> ImagePlus (Imageprocessor + metadata) -> Segmentation -> RoiArray -> RoiManager
            for (int imagesInArray = 0; imagesInArray < imageArray.size(); imagesInArray++) {
                IJ.log("Segmenting image: " + (imagesInArray + 1) + "/" + imageArray.size() + " ...");
                ImagePlus impStack = IJ.openImage(inputDirectory + imageArray.get(imagesInArray));
                int[] dim = impStack.getDimensions();
                impWidth = dim[0];
                impHeight = dim[1];
                Duplicator dup = new Duplicator();
                ImagePlus imp = new ImagePlus();

                if (!impStack.isHyperStack()) {
                    if (nChannels != impStack.getDimensions()[2]) {
                        imp = dup.run(impStack, 1, 1, channel, channel, 1, 1);
                    } else {
                        imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                    }
                } else {
                    imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                    ZProjector zproj = new ZProjector(imp);
                    zproj.setMethod(zProjection);
                    zproj.doProjection();
                    imp = zproj.getProjection();
                }

                Calibration cal = new Calibration(imp);
                cal.pixelWidth = calibration;
                cal.pixelHeight = calibration;
                cal.pixelDepth = calibration;
                cal.setUnit("µm");
                imp.setCalibration(cal);
                imp.setLut(LUT.createLutFromColor(Color.lightGray));
                imp.setTitle(impStack.getTitle());

                SegmentationObject.exec(imp, dilationMicron, rangeEdgeMicron, profileWatershedMicron);
                Nucleus[] nuclei = SegmentationObject.getNuclei();
                if (nuclei != null) {
                    RoiManager rm = new RoiManager(false);
                    for (int i = 0; i < nuclei.length; i++) {
                        rm.addRoi(nuclei[i].roiNucleus);
                        rm.runCommand("Save", newFolder + imageArray.get(imagesInArray) + ".zip");
                    }
                    if (extractFeatures) {
                        extractFeatures(nuclei, imp, newFolder);
                    }
                    if (showResultImages == true || saveResultImages == true) {
                        Overlay overlay = new Overlay();
                        for (int i = 0; i < nuclei.length; i++) {
                            overlay.add(nuclei[i].roiNucleus);
                        }
                        if (showResultImages == true) {
                            imp.show();
                            imp.setOverlay(overlay);
                        }
                        if (saveResultImages == true) {
                            imp.setOverlay(overlay);
                            IJ.saveAs(imp, "tiff", newFolder + "RESULT_" + imp.getTitle());
                        }
                    }
                }
            }
            IJ.log("-----------------------------------");
            IJ.log("BLEND - Functionality: Segmentation - END");
            IJ.log("-----------------------------------");
        } //Functionality = 1: Classification
        else if (functionality == 1) {
            IJ.log("-----------------------------------");
            IJ.log("BLEND - Functionality: Classification - START");
            IJ.log("-----------------------------------");
            String folder = createFolder();
            Segmentation SegmentationObject = new Segmentation(false,backgroundSubtraction, sizeRollingBall, indexFilter, radiusFilter, twoPass, globalThresholdMethod, localThresholdMethod, refinement, watershed, minArea);
            SupervisedClassification SC = new SupervisedClassification(SegmentationObject, calibration, nChannels, channel, zProjection, dilationMicron, rangeEdgeMicron, profileWatershedMicron, inputDirectory, folder, saveCroppedNuclei);
            SC.exec();
            IJ.log("-----------------------------------");
            IJ.log("BLEND - Functionality: Segmentation - END");
            IJ.log("-----------------------------------");
        } //Functionality = 2: Validation
        else if (functionality
                == 2) {
            IJ.log("-----------------------------------");
            IJ.log("BLEND - Functionality: Validation - START");
            IJ.log("-----------------------------------");
            String outputName = "Validation.txt";
            String outputText = ("Image;xCoord;yCoord;GT;Global;Local;HausdorffDist;AveragedHausdorffDist;SimilarityIndex;\n");
            FileWrite(outputDirectory, outputName, outputText);

            ArrayList<String> imageArray = getImageArray(inputDirectory);
            impWidth = IJ.openImage(inputDirectory + imageArray.get(0)).getWidth();
            impHeight = IJ.openImage(inputDirectory + imageArray.get(0)).getHeight();

            //PosCtrl
            if (posCtrl) {
                IJ.log("Positive Control");
                PosCtrl PC = new PosCtrl(GTDirectory, outputDirectory, impWidth, impHeight);
                PC.exec();
            }

            //Validation
            ArrayList<ArrayList<ImagePlus[]>> listRoiMaskGT = getMaskGT();
            if (scanThresholds == true) {
                if (twoPass) {
                    for (int indexGlobal = 0; indexGlobal < thresholdMethods.length; indexGlobal++) {
                        globalThresholdMethod = thresholdMethods[indexGlobal];
                        for (int indexLocal = 0; indexLocal < thresholdMethods.length; indexLocal++) {
                            ArrayList<Nucleus[]> listNuclei = new ArrayList<Nucleus[]>();
                            localThresholdMethod = thresholdMethods[indexLocal];
                            String newFolder = createFolder();
                            IJ.log("Global: " + globalThresholdMethod + " - Local: " + localThresholdMethod);
                            Segmentation SegmentationObject = new Segmentation(false, backgroundSubtraction, sizeRollingBall, indexFilter, radiusFilter, twoPass, globalThresholdMethod, localThresholdMethod, refinement, watershed, minArea);
                            for (int imagesInArray = 0; imagesInArray < imageArray.size(); imagesInArray++) {

                                ImagePlus impStack = IJ.openImage(inputDirectory + imageArray.get(imagesInArray));
                                int[] dim = impStack.getDimensions();
                                impWidth = dim[0];
                                impHeight = dim[1];
                                Duplicator dup = new Duplicator();
                                ImagePlus imp = new ImagePlus();
                                if (!impStack.isHyperStack()) {
                                    if (nChannels != impStack.getDimensions()[2]) {
                                        imp = dup.run(impStack, 1, 1, channel, channel, 1, 1);
                                    } else {
                                        imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                                    }
                                } else {
                                    imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                                    ZProjector zproj = new ZProjector(imp);
                                    zproj.setMethod(zProjection);
                                    zproj.doProjection();
                                    imp = zproj.getProjection();
                                }
                                Calibration cal = new Calibration(imp);
                                cal.pixelWidth = calibration;
                                cal.pixelHeight = calibration;
                                cal.pixelDepth = calibration;
                                cal.setUnit("µm");
                                imp.setCalibration(cal);
                                imp.setLut(LUT.createLutFromColor(Color.lightGray));
                                imp.setTitle(impStack.getTitle());

                                SegmentationObject.exec(imp, dilationMicron, rangeEdgeMicron, profileWatershedMicron);
                                Nucleus[] nuclei = SegmentationObject.getNuclei();
                                if (nuclei != null) {
                                    RoiManager rm = new RoiManager(false);
                                    for (int i = 0; i < nuclei.length; i++) {
                                        rm.addRoi(nuclei[i].roiNucleus);
                                        rm.runCommand("Save", newFolder + imageArray.get(imagesInArray) + ".zip");
                                    }
                                }
                                listNuclei.add(nuclei);
                            }
                            RoiListComparison RLC = new RoiListComparison(listRoiMaskGT,  impWidth, impHeight, listNuclei, outputDirectory, outputName, imageArray, thresholds[indexGlobal], thresholds[indexLocal]);
                            RLC.exec();
                        }
                    }
                } else if (!twoPass) {
                    for (int indexGlobal = 0; indexGlobal < thresholdMethods.length; indexGlobal++) {
                        globalThresholdMethod = thresholdMethods[indexGlobal];
                        localThresholdMethod = null;
                        String newFolder = createFolder();
                        ArrayList<Nucleus[]> listNuclei = new ArrayList<Nucleus[]>();
                        IJ.log("Global: " + globalThresholdMethod + " - Local: " + localThresholdMethod);
                        Segmentation SegmentationObject = new Segmentation( false, backgroundSubtraction, sizeRollingBall, indexFilter, radiusFilter, twoPass, globalThresholdMethod, localThresholdMethod, refinement, watershed, minArea);
                        for (int imagesInArray = 0; imagesInArray < imageArray.size(); imagesInArray++) {
                            ImagePlus impStack = IJ.openImage(inputDirectory + imageArray.get(imagesInArray));
                            int[] dim = impStack.getDimensions();
                            impWidth = dim[0];
                            impHeight = dim[1];
                            Duplicator dup = new Duplicator();
                            ImagePlus imp = new ImagePlus();
                            if (!impStack.isHyperStack()) {
                                if (nChannels != impStack.getDimensions()[2]) {
                                    imp = dup.run(impStack, 1, 1, channel, channel, 1, 1);
                                } else {
                                    imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                                }
                            } else {
                                imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                                ZProjector zproj = new ZProjector(imp);
                                zproj.setMethod(zProjection);
                                zproj.doProjection();
                                imp = zproj.getProjection();
                            }
                            Calibration cal = new Calibration(imp);
                            cal.pixelWidth = calibration;
                            cal.pixelHeight = calibration;
                            cal.pixelDepth = calibration;
                            cal.setUnit("µm");
                            imp.setCalibration(cal);
                            SegmentationObject.exec(imp, dilationMicron, rangeEdgeMicron, profileWatershedMicron);
                            Nucleus[] nuclei = SegmentationObject.getNuclei();
                            if (nuclei != null) {
                                RoiManager rm = new RoiManager(false);
                                for (int i = 0; i < nuclei.length; i++) {
                                    rm.addRoi(nuclei[i].roiNucleus);
                                    rm.runCommand("Save", newFolder + imageArray.get(imagesInArray) + ".zip");
                                }
                            }
                            listNuclei.add(nuclei);
                        }
                        RoiListComparison RLC = new RoiListComparison(listRoiMaskGT,  impWidth, impHeight, listNuclei, outputDirectory, outputName, imageArray, thresholds[indexGlobal], "");
                        RLC.exec();
                    }
                }
            } else if (scanThresholds == false) {
                if (twoPass) {
                    IJ.log("Global: " + globalThresholdMethod + " - Local: " + localThresholdMethod);
                    String newFolder = createFolder();
                    ArrayList<Nucleus[]> listRoiArray = new ArrayList<Nucleus[]>();
                    Segmentation SegmentationObject = new Segmentation( false, backgroundSubtraction, sizeRollingBall, indexFilter, radiusFilter, twoPass, globalThresholdMethod, localThresholdMethod, refinement, watershed, minArea);
                    for (int imagesInArray = 0; imagesInArray < imageArray.size(); imagesInArray++) {
                        ImagePlus impStack = IJ.openImage(inputDirectory + imageArray.get(imagesInArray));
                        int[] dim = impStack.getDimensions();
                        impWidth = dim[0];
                        impHeight = dim[1];
                        Duplicator dup = new Duplicator();
                        ImagePlus imp = new ImagePlus();
                        if (!impStack.isHyperStack()) {
                            if (nChannels != impStack.getDimensions()[2]) {
                                imp = dup.run(impStack, 1, 1, channel, channel, 1, 1);
                            } else {
                                imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                            }
                        } else {
                            imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                            ZProjector zproj = new ZProjector(imp);
                            zproj.setMethod(zProjection);
                            zproj.doProjection();
                            imp = zproj.getProjection();
                        }
                        Calibration cal = new Calibration(imp);
                        cal.pixelWidth = calibration;
                        cal.pixelHeight = calibration;
                        cal.pixelDepth = calibration;
                        cal.setUnit("µm");
                        imp.setCalibration(cal);
                        imp.setLut(LUT.createLutFromColor(Color.lightGray));
                        imp.setTitle(impStack.getTitle());
                        SegmentationObject.exec(imp, dilationMicron, rangeEdgeMicron, profileWatershedMicron);
                        Nucleus[] nuclei = SegmentationObject.getNuclei();
                        if (nuclei != null) {
                            RoiManager rm = new RoiManager(false);
                            for (int i = 0; i < nuclei.length; i++) {
                                rm.addRoi(nuclei[i].roiNucleus);
                                rm.runCommand("Save", newFolder + imageArray.get(imagesInArray) + ".zip");
                            }
                        }
                        listRoiArray.add(nuclei);
                    }
                    RoiListComparison RLC = new RoiListComparison(listRoiMaskGT,  impWidth, impHeight, listRoiArray, outputDirectory, outputName, imageArray, thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(globalThresholdMethod)], thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(localThresholdMethod)]);
                    RLC.exec();
                } else if (!twoPass) {
                    IJ.log("Global: " + globalThresholdMethod);
                    localThresholdMethod = null;
                    String newFolder = createFolder();
                    ArrayList<Nucleus[]> listNuclei = new ArrayList<Nucleus[]>();
                    Segmentation SegmentationObject = new Segmentation( false,backgroundSubtraction, sizeRollingBall, indexFilter, radiusFilter, twoPass, globalThresholdMethod, localThresholdMethod, refinement, watershed, minArea);
                    for (int imagesInArray = 0; imagesInArray < imageArray.size(); imagesInArray++) {
                        ImagePlus impStack = IJ.openImage(inputDirectory + imageArray.get(imagesInArray));
                        int[] dim = impStack.getDimensions();
                        impWidth = dim[0];
                        impHeight = dim[1];
                        Duplicator dup = new Duplicator();
                        ImagePlus imp = new ImagePlus();
                        if (!impStack.isHyperStack()) {
                            if (nChannels != impStack.getDimensions()[2]) {
                                imp = dup.run(impStack, 1, 1, channel, channel, 1, 1);
                            } else {
                                imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                            }
                        } else {
                            imp = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                            ZProjector zproj = new ZProjector(imp);
                            zproj.setMethod(zProjection);
                            zproj.doProjection();
                            imp = zproj.getProjection();
                        }
                        Calibration cal = new Calibration(imp);
                        cal.pixelWidth = calibration;
                        cal.pixelHeight = calibration;
                        cal.pixelDepth = calibration;
                        cal.setUnit("µm");
                        imp.setCalibration(cal);
                        imp.setLut(LUT.createLutFromColor(Color.lightGray));
                        imp.setTitle(impStack.getTitle());
                        SegmentationObject.exec(imp, dilationMicron, rangeEdgeMicron, profileWatershedMicron);
                        Nucleus[] nuclei = SegmentationObject.getNuclei();
                        if (nuclei != null) {
                            RoiManager rm = new RoiManager(false);
                            for (int i = 0; i < nuclei.length; i++) {
                                rm.addRoi(nuclei[i].roiNucleus);
                                rm.runCommand("Save", newFolder + imageArray.get(imagesInArray) + ".zip");
                            }
                        }
                        listNuclei.add(nuclei);
                    }
                    RoiListComparison RLC = new RoiListComparison(listRoiMaskGT,  impWidth, impHeight, listNuclei, outputDirectory, outputName, imageArray, thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(globalThresholdMethod)], "");
                    RLC.exec();
                }
            }
            IJ.log("-----------------------------------");
            IJ.log("BLEND - Functionality: Validation - END");
            IJ.log("-----------------------------------");
        } //Functionality = 4: RoiList comparison
        else if (functionality
                == 3) {
            IJ.log("-----------------------------------");
            IJ.log("BLEND - Functionality: Comparison - START");
            IJ.log("-----------------------------------");

            String outputName = "ResultsComparison.txt";
            String outputText = ("Image;xCoord;yCoord;GT;;;HausdorffDist;AveragedHausdorffDist;SimilarityIndex;\n");
            FileWrite(outputDirectory, outputName, outputText);
            ArrayList<ArrayList<ImagePlus[]>> listRoiMaskGT = getMaskGT();
            ArrayList<Roi[]> listRoiArray = new ArrayList<Roi[]>();
            ArrayList<Nucleus[]> listNuclei = new ArrayList<Nucleus[]>();
            String[] roiSources = new File(roiListDirectory).list();
            ArrayList<String> fileNames = new ArrayList<String>();
            Arrays.sort(roiSources);
            for (int i = 0; i < roiSources.length; i++) {
                fileNames.add(roiSources[i]);
                Roi[] roiArray = getRoiArray(roiListDirectory + roiSources[i]);
                Nucleus[] temp = new Nucleus[roiArray.length];
                for (int j = 0; j < roiArray.length; j++) {
                    temp[j] = new Nucleus(fileNames.get(i), roiArray[j], j);
                }
                listNuclei.add(temp);
            }
            RoiListComparison RLC = new RoiListComparison(listRoiMaskGT,  impWidth, impHeight, listNuclei, outputDirectory, outputName, fileNames, "", "");
            RLC.exec();
            IJ.log("-----------------------------------");
            IJ.log("BLEND - Functionality: Comparison - END");
            IJ.log("-----------------------------------");
        }
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

    private void extractFeatures(Nucleus[] nuclei, ImagePlus imp, String newFolder) {
        IJ.log("Extracting features ...");
        for (int i = 0; i < nuclei.length; i++) {
            Nucleus nucleus = nuclei[i];
            Roi roi = nucleus.roiNucleus;
            shapeDescriptors.ShapeDescriptors morpho = new shapeDescriptors.ShapeDescriptors();
            nucleus.morpho = morpho.exec(imp, roi, "Morph");
            textureDiscriptors.GLCMTexture textural = new textureDiscriptors.GLCMTexture();
            nucleus.textural = textural.exec(imp, roi, "Text");
        }
        String csvFile = newFolder + "Features.csv";
        File f = new File(csvFile);
        String COMMA_DELIMITER = ",";
        String NEW_LINE_SEPARATOR = "\n";
        String FILE_HEADER = "";

        //Create FileHeader
        if (!f.exists() & nuclei.length > 0) {
            Set keys = nuclei[0].index.entrySet();
            Iterator i = keys.iterator();
            while (i.hasNext()) {
                Map.Entry me = (Map.Entry) i.next();
                FILE_HEADER = FILE_HEADER.concat((String) me.getKey());
                FILE_HEADER = FILE_HEADER.concat(COMMA_DELIMITER);
            }
            keys = nuclei[0].morpho.entrySet();
            i = keys.iterator();
            while (i.hasNext()) {
                Map.Entry me = (Map.Entry) i.next();
                FILE_HEADER = FILE_HEADER.concat((String) me.getKey());
                FILE_HEADER = FILE_HEADER.concat(COMMA_DELIMITER);
            }
            keys = nuclei[0].textural.entrySet();
            i = keys.iterator();
            while (i.hasNext()) {
                Map.Entry me = (Map.Entry) i.next();
                FILE_HEADER = FILE_HEADER.concat((String) me.getKey());
                FILE_HEADER = FILE_HEADER.concat(COMMA_DELIMITER);
            }
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(csvFile);
                fileWriter.append(FILE_HEADER);
                fileWriter.append(NEW_LINE_SEPARATOR);
            } catch (Exception e) {
                System.out.println("Error in CsvFileWriter !!!");
                e.printStackTrace();
            }
            try {
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException e) {
                System.out.println("Error while flushing/closing fileWriter !!!");
                e.printStackTrace();
            }
        }
        //Write results
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(csvFile, true);
        } catch (Exception e) {
            System.out.println("Error in CsvFileWriter !!!");
            e.printStackTrace();
        }
        String resultLine = "";
        for (int nuc = 0; nuc < nuclei.length; nuc++) {
            resultLine = "";
            Set keys = nuclei[nuc].index.entrySet();
            Iterator i = keys.iterator();
            while (i.hasNext()) {
                Map.Entry me = (Map.Entry) i.next();
                resultLine = resultLine.concat(me.getValue().toString());
                resultLine = resultLine.concat(COMMA_DELIMITER);
            }
            keys = nuclei[nuc].morpho.entrySet();
            i = keys.iterator();
            while (i.hasNext()) {
                Map.Entry me = (Map.Entry) i.next();
                resultLine = resultLine.concat((String) me.getValue().toString());
                resultLine = resultLine.concat(COMMA_DELIMITER);
            }
            keys = nuclei[nuc].textural.entrySet();
            i = keys.iterator();
            while (i.hasNext()) {
                Map.Entry me = (Map.Entry) i.next();
                resultLine = resultLine.concat((String) me.getValue().toString());
                resultLine = resultLine.concat(COMMA_DELIMITER);
            }
            try {
                fileWriter.append(resultLine);
                fileWriter.append(NEW_LINE_SEPARATOR);
            } catch (Exception e) {
                System.out.println("Error in CsvFileWriter !!!");
                e.printStackTrace();
            }
        }
        try {
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            System.out.println("Error while flushing/closing fileWriter !!!");
            e.printStackTrace();
        }
    }

    private void getAll(SettingsGUI GUI) {
        GUI.exec();
        globalThresholdMethod = GUI.getGlobalThresholdMethod();
        inputDirectory = GUI.getInputDirectory();
        calibration = GUI.getCalibration();
        channel = GUI.getChannel();
        nChannels = GUI.getNChannels();
        zProjection = GUI.getZProjection();
        localThresholdMethod = GUI.getLocalThresholdMethod();
        outputDirectory = GUI.getOutputDirectory();
        functionality = GUI.getFunctionality();
        GTDirectory = GUI.getGTDirectory();
        twoPass = GUI.getNumberThreshold();
        watershed = GUI.getWatershed();
        refinement = GUI.getRefinement();
        scanThresholds = GUI.getScanThresholds();
        saveResultImages = GUI.getSaveResultImages();
        saveCroppedNuclei = GUI.getSaveCroppedNuclei();
        impWidth = GUI.getWidth();
        impHeight = GUI.getHeight();
        roiListDirectory = GUI.getRoiListDirectory();
        showDebugImages = GUI.getShowDebugImages();
        showResultImages = GUI.getShowResultImages();
        extractFeatures = GUI.getExtractFeatures();
        posCtrl = GUI.getPosCtrl();
        minArea = GUI.getMinArea();
        backgroundSubtraction = GUI.getBackgroundSubstraction();
        sizeRollingBall = GUI.getSizeRollingBall();
        indexFilter = GUI.getIndexFilter();
        radiusFilter = GUI.getRadiusFitler();
    }

    private ArrayList<String> getImageArray(String directoryName) {
        //Put all the names of the image-files in String[] + sort
        String[] fileArray = new File(directoryName).list();
        Arrays.sort(fileArray);
        //Check the extensions of the files in fileArray and put the file-names in imageArray if correct
        ArrayList<String> imageArray = new ArrayList<String>();
        for (int i = 0; i < fileArray.length; i++) {
            if (fileArray[i].indexOf(".tif") > 0 || fileArray[i].indexOf(".dcm") > 0 || fileArray[i].indexOf(".fits") > 0
                    || fileArray[i].indexOf(".fit") > 0 || fileArray[i].indexOf(".fts") > 0 || fileArray[i].indexOf(".pgm") > 0
                    || fileArray[i].indexOf(".jpg") > 0 || fileArray[i].indexOf(".jpeg") > 0 || fileArray[i].indexOf(".bmp") > 0 || fileArray[i].indexOf(".png") > 0) {
                imageArray.add(fileArray[i]);
            }
        }
        return imageArray;
    }

    private String createFolder() {
        String newFolder;
        if (twoPass) {
            if (refinement == true) {
                newFolder = outputDirectory + thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(globalThresholdMethod)] + "_" + thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(localThresholdMethod)] + "_CR/";
                (new File(newFolder)).mkdirs();
            } else {
                newFolder = outputDirectory + thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(globalThresholdMethod)] + "_" + thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(localThresholdMethod)] + "/";
                (new File(newFolder)).mkdirs();
            }
        } else {
            if (refinement == true) {
                newFolder = outputDirectory + thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(globalThresholdMethod)] + "_CR/";
                (new File(newFolder)).mkdirs();
            } else {
                newFolder = outputDirectory + thresholds[java.util.Arrays.asList(thresholdMethods).indexOf(globalThresholdMethod)] + "/";
                (new File(newFolder)).mkdirs();

            }
        }
        return newFolder;

    }

    private ArrayList<ArrayList<ImagePlus[]>> getMaskGT() {
        ArrayList<ArrayList<Roi[]>> listRoiArrayGT = new ArrayList<ArrayList<Roi[]>>();
        for (int i = 0; i < GTDirectory.size(); i++) {
            String[] roiSources = new File(GTDirectory.get(i)).list();
            Arrays.sort(roiSources);
            ArrayList<Roi[]> temp = new ArrayList<Roi[]>();
            for (int j = 0; j < roiSources.length; j++) {
                temp.add(GetGTRois(GTDirectory.get(i) + roiSources[j]));
            }
            listRoiArrayGT.add(i, temp);
        }
        ArrayList<ArrayList<ImagePlus[]>> listRoiMaskGT = new ArrayList<ArrayList<ImagePlus[]>>();
        for (int groundTruth = 0; groundTruth < GTDirectory.size(); groundTruth++) {
            ArrayList<Roi[]> listRoiArrayTemp = listRoiArrayGT.get(groundTruth);
            ArrayList<ImagePlus[]> listRoiMaskTemp = new ArrayList<ImagePlus[]>();
            for (int i = 0; i < listRoiArrayTemp.size(); i++) {
                Roi[] roiArrayTemp = listRoiArrayTemp.get(i);
                ImagePlus[] maskArrayTemp = new ImagePlus[roiArrayTemp.length];
                for (int j = 0; j < roiArrayTemp.length; j++) {
                    if (roiArrayTemp[j].getBounds().getWidth() * roiArrayTemp[j].getBounds().getHeight() > 0) {
                        maskArrayTemp[j] = createMask(roiArrayTemp[j], impWidth, impHeight);
                    }
                }
                listRoiMaskTemp.add(maskArrayTemp);
            }
            listRoiMaskGT.add(listRoiMaskTemp);
        }
        return listRoiMaskGT;
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

    private ImagePlus createMask(Roi roi, int impWidth, int impHeight) {
        ImagePlus impMask = IJ.createImage("Mask", "8-bit Black", impWidth, impHeight, 1);
        ImageProcessor ipMask = impMask.getProcessor();
        ipMask.setRoi(roi);
        ipMask.setValue(255);
        ipMask.fill(ipMask.getMask());
        impMask.updateAndDraw();
        return impMask;
    }

    private Roi[] getRoiArray(String path) {
        ArrayList<Roi> roiList = new ArrayList<Roi>();
        ZipInputStream in = null;
        ByteArrayOutputStream out;
        //int nRois = 0; 
        try {
            in = new ZipInputStream(new FileInputStream(path));
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
        Roi[] roiArray = new Roi[roiList.size()];
        roiArray = roiList.toArray(roiArray);
        return roiArray;
    }

    private void Stop() {
        System.exit(0);
    }

    //*/
    public static void main(final String... args) {
        new ij.ImageJ();
        new BLEND_Plugin().run("");
    }
    //*/
}
