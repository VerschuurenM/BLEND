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
package GUI;

import ij.IJ;
import java.util.*;
import java.util.prefs.Preferences;
import ij.Macro;
import ij.plugin.ZProjector;
import ij.process.AutoThresholder;
import ij.plugin.filter.RankFilters;
import java.awt.Font;

public class SettingsGUI {
    int functionality;

    ArrayList<String> GTDirectory = new ArrayList<String>();
    String inputDirectory;
    String outputDirectory;
    String roiListDirectory;

    boolean randomImages;
    boolean showDebugImages;
    boolean showResultImages;
    boolean saveResultImages;
    boolean extractFeatures;
    boolean saveCroppedNuclei;
    boolean scanThresholds;
    boolean posCtrl;

    int impWidth;
    int impHeight;
    double calibration;
    int nChannels;
    int channel;
    int zProjection;

    boolean backgroundSubtraction;
    double sizeRollingBall;
    int indexFilterGUI;
    int indexFilter;
    double radiusFilter;

    double minArea;
    double maxArea;

    boolean twoPass;
    int indexGlobalGUI;
    AutoThresholder.Method globalThresholdMethod;
    int indexLocalGUI;
    AutoThresholder.Method localThresholdMethod;
    boolean refinement;
    double watershedThreshold;

    public void exec() {
        //Define node to store preferences
        Preferences prefs = Preferences.userRoot().node(this.getClass().getName());

        //Options
        String [] channelOptions = {"1","2","3","4"};
        String [] zProjOptions = ZProjector.METHODS;
        String[] thresholds = {"Huang", "IJ_IsoData", "Intermodes", "IsoData", "Li", "MaxEntropy", "Mean",
            "MinError", "Minimum", "Moment", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"};
        String[] thresholdsLocal = {"-", "Huang", "IJ_IsoData", "Intermodes", "IsoData", "Li", "MaxEntropy", "Mean",
            "MinError", "Minimum", "Moment", "Otsu", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"};
        AutoThresholder.Method[] thresholdMethods = {AutoThresholder.Method.Huang, AutoThresholder.Method.IJ_IsoData, AutoThresholder.Method.Intermodes, AutoThresholder.Method.IsoData, AutoThresholder.Method.Li, AutoThresholder.Method.MaxEntropy, AutoThresholder.Method.Mean,
            AutoThresholder.Method.MinError, AutoThresholder.Method.Minimum, AutoThresholder.Method.Moments, AutoThresholder.Method.Otsu, AutoThresholder.Method.Percentile, AutoThresholder.Method.RenyiEntropy, AutoThresholder.Method.Shanbhag, AutoThresholder.Method.Triangle, AutoThresholder.Method.Yen};
        AutoThresholder.Method[] thresholdMethodsLocal = {null, AutoThresholder.Method.Huang, AutoThresholder.Method.IJ_IsoData, AutoThresholder.Method.Intermodes, AutoThresholder.Method.IsoData, AutoThresholder.Method.Li, AutoThresholder.Method.MaxEntropy, AutoThresholder.Method.Mean,
            AutoThresholder.Method.MinError, AutoThresholder.Method.Minimum, AutoThresholder.Method.Moments, AutoThresholder.Method.Otsu, AutoThresholder.Method.Percentile, AutoThresholder.Method.RenyiEntropy, AutoThresholder.Method.Shanbhag, AutoThresholder.Method.Triangle, AutoThresholder.Method.Yen};
        String[] filters = {"-", "Gaussian", "Mean", "Minimum", "Maximum", "Variance", "Median"};
        int[] filterIndex = {-2, -1, RankFilters.MEAN, RankFilters.MIN, RankFilters.MAX, RankFilters.VARIANCE, RankFilters.MEDIAN};
       
        //Select functionality
        GenericDialogPlus gd = new GenericDialogPlus("BLEND");
        String[] functions = {"Segmentation", "Supervised Classification", "Validation", "Compare RoiLists"};
        gd.addRadioButtonGroup("Function", functions, 2, 2, prefs.get("function", "Segmentation"));
        gd.showDialog();
        if (gd.wasCanceled()) {
            System.out.println("PlugIn Cancelled");
            throw new RuntimeException(Macro.MACRO_CANCELED);
        }
        String selFunction = gd.getNextRadioButton();
        prefs.put("function", selFunction);
        functionality = Arrays.asList(functions).indexOf(selFunction);

        int inset = 150;

        if (functionality == 0) {
            gd = new GenericDialogPlus("Segmentation");
            gd.setInsets(0, 0, 0);
            gd.addMessage("General:", new Font(null, Font.BOLD, 12));
            gd.addDirectoryField("Choose Image Folder: ", prefs.get("imageFolder", ""), 12);
            gd.addDirectoryField("Choose Output Folder: ", prefs.get("outputFolder", ""), 12);
            gd.setInsets(0, 0, 0);
            gd.addNumericField("Calibration", prefs.getDouble("calibration", 0.19), 5, 10, "µm/px");
            gd.addChoice("Number Channels",channelOptions, prefs.get("nChannels","1"));
            gd.addChoice("Channel To Analyse", channelOptions, prefs.get("channel", "1"));
            gd.addChoice("Z Projection Method:", zProjOptions, prefs.get("zProjection","Average Intensity"));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Generate Debug Images", prefs.getBoolean("debugImages", true));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Randomize Images", prefs.getBoolean("randomImages", true));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Save Segmented Images", prefs.getBoolean("saveImages", true));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Show Segmented Images", prefs.getBoolean("showImages", true));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Extract Morpho- & Textural Features", prefs.getBoolean("extractFeatures", true));

            gd.setInsets(1, 0, 0);
            gd.addMessage("PreProcessing:", new Font(null, Font.BOLD, 12));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Background Subtraction", prefs.getBoolean("backgroundSubtraction", true));
            gd.addNumericField("Size Rolling Ball", prefs.getDouble("sizeRollingBall", 20), 0, 3, "px");
            gd.addChoice("Filter", filters, prefs.get("filter", "-"));
            gd.addNumericField("Radius Filter", prefs.getDouble("radiusFilter", 1), 0, 3, "px");

            gd.setInsets(1, 0, 0);
            gd.addMessage("Segmentation:", new Font(null, Font.BOLD, 12));
            gd.addNumericField("Min Area", prefs.getDouble("minArea", 45), 0, 3, "µm²");
            gd.addNumericField("Max Area", prefs.getDouble("maxArea", 500), 0, 3, "µm²");
            gd.addChoice("Global Threshold", thresholds, prefs.get("global", "Mean"));
            gd.addChoice("Local Threshold", thresholdsLocal, prefs.get("local", "Li"));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Refinement", prefs.getBoolean("refinement", true));
            gd.setInsets(0, inset, 0);
            gd.addSlider("Watershed", 0, 1, prefs.getDouble("watershedThreshold", 0.75));

            gd.showDialog();
            if (gd.wasCanceled()) {
                System.out.println("PlugIn Cancelled");
                throw new RuntimeException(Macro.MACRO_CANCELED);
            }
            inputDirectory = gd.getNextString();
            if (inputDirectory.charAt(inputDirectory.length() - 1) != '/') {
                inputDirectory = inputDirectory + "/";
            }
            prefs.put("imageFolder", inputDirectory);
            outputDirectory = gd.getNextString();
            if (outputDirectory.charAt(outputDirectory.length() - 1) != '/') {
                outputDirectory = outputDirectory + "/";
            }
            prefs.put("outputFolder", outputDirectory);
            calibration = gd.getNextNumber();
            prefs.putDouble("calibration", calibration);
            nChannels=Integer.parseInt(gd.getNextChoice());
            prefs.put("nChannels", Integer.toString(nChannels));  
            channel=Integer.parseInt(gd.getNextChoice());
            prefs.put("channel", Integer.toString(channel)); 
            String zProjString = gd.getNextChoice();
            zProjection=Arrays.asList(zProjOptions).indexOf(zProjString);
            prefs.put("zProjection", zProjString);
            showDebugImages = gd.getNextBoolean();
            prefs.putBoolean("debugImages", showDebugImages);
            randomImages = gd.getNextBoolean();
            prefs.putBoolean("randomImages", randomImages);
            saveResultImages = gd.getNextBoolean();
            prefs.putBoolean("saveImages", saveResultImages);
            showResultImages = gd.getNextBoolean();
            prefs.putBoolean("showImages", showResultImages);
            extractFeatures = gd.getNextBoolean();
            prefs.putBoolean("extractFeatures", extractFeatures);

            backgroundSubtraction = gd.getNextBoolean();
            prefs.putBoolean("backgroundSubtraction", backgroundSubtraction);
            sizeRollingBall = gd.getNextNumber();
            prefs.putDouble("sizeRollingBall", sizeRollingBall);
            indexFilterGUI = gd.getNextChoiceIndex();
            indexFilter = filterIndex[indexFilterGUI];
            prefs.put("filter", filters[indexFilterGUI]);
            radiusFilter = gd.getNextNumber();
            prefs.putDouble("radiusFilter", radiusFilter);
            minArea = gd.getNextNumber();
            prefs.putDouble("minArea", minArea);
            maxArea = gd.getNextNumber();
            prefs.putDouble("maxArea", maxArea);
            indexGlobalGUI = gd.getNextChoiceIndex();
            globalThresholdMethod = thresholdMethods[indexGlobalGUI];
            prefs.put("global", thresholds[indexGlobalGUI]);
            indexLocalGUI = gd.getNextChoiceIndex();
            localThresholdMethod = thresholdMethodsLocal[indexLocalGUI];
            prefs.put("local", thresholdsLocal[indexLocalGUI]);
            if (thresholdsLocal[indexLocalGUI].equals("-")) {
                twoPass = false;
            } else {
                twoPass = true;
            }
            refinement = gd.getNextBoolean();
            prefs.putBoolean("refinement", refinement);
            watershedThreshold = gd.getNextNumber();
            prefs.putDouble("watershedThreshold", watershedThreshold);
        } else if (functionality == 1) {
            gd = new GenericDialogPlus("Supervised Classification");

            gd.setInsets(0, 0, 0);
            gd.addMessage("General:", new Font(null, Font.BOLD, 12));
            gd.addDirectoryField("Choose Image Folder: ", prefs.get("imageFolder", ""), 12);
            gd.addDirectoryField("Choose Output Folder: ", prefs.get("outputFolder", ""), 12);
            gd.addNumericField("Calibration", prefs.getDouble("calibration", 0.19), 5, 10, "µm/px");
            gd.addChoice("Number Channels",channelOptions, prefs.get("nChannels","1"));
            gd.addChoice("Channel To Analyse", channelOptions, prefs.get("channel", "1"));
            gd.addChoice("Z Projection Method:", zProjOptions, prefs.get("zProjection","Average Intensity"));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Save Cropped Nuclei", prefs.getBoolean("saveCroppedNuclei", true));

            gd.setInsets(20, 0, 0);
            gd.addMessage("PreProcessing:", new Font(null, Font.BOLD, 12));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Background Subtraction", prefs.getBoolean("backgroundSubtraction", true));
            gd.addNumericField("Size Rolling Ball", prefs.getDouble("sizeRollingBall", 20), 0, 3, "px");
            gd.addChoice("Filter", filters, prefs.get("filter", "-"));
            gd.addNumericField("Radius Filter", prefs.getDouble("radiusFilter", 1), 0, 3, "px");

            gd.setInsets(20, 0, 0);
            gd.addMessage("Segmentation:", new Font(null, Font.BOLD, 12));
            gd.addNumericField("Min Area", prefs.getDouble("minArea", 45), 0, 3, "µm²");
            gd.addNumericField("Max Area", prefs.getDouble("maxArea", 500), 0, 3, "µm²");

            gd.addChoice("Global Threshold", thresholds, prefs.get("global", "Mean"));
            gd.addChoice("Local Threshold", thresholdsLocal, prefs.get("local", "Li"));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Refinement", prefs.getBoolean("refinement", true));
            gd.setInsets(0, inset, 0);
            gd.addSlider("Watershed", 0, 1, prefs.getDouble("watershedThreshold", 0.75));

            gd.showDialog();
            if (gd.wasCanceled()) {
                System.out.println("PlugIn Cancelled");
                throw new RuntimeException(Macro.MACRO_CANCELED);
            }
            inputDirectory = gd.getNextString();
            if (inputDirectory.charAt(inputDirectory.length() - 1) != '/') {
                inputDirectory = inputDirectory + "/";
            }
            prefs.put("imageFolder", inputDirectory);
            outputDirectory = gd.getNextString();
            if (outputDirectory.charAt(outputDirectory.length() - 1) != '/') {
                outputDirectory = outputDirectory + "/";
            }
            prefs.put("outputFolder", outputDirectory);
            calibration = gd.getNextNumber();
            prefs.putDouble("calibration", calibration);
            nChannels=Integer.parseInt(gd.getNextChoice());
            prefs.put("nChannels", Integer.toString(nChannels));          
            channel=Integer.parseInt(gd.getNextChoice());
            prefs.put("channel", Integer.toString(channel));  
            String zProjString = gd.getNextChoice();
            zProjection=Arrays.asList(zProjOptions).indexOf(zProjString);
            prefs.put("zProjection", zProjString);
            saveCroppedNuclei = gd.getNextBoolean();
            prefs.putBoolean("saveCroppedNuclei", saveCroppedNuclei);

            backgroundSubtraction = gd.getNextBoolean();
            prefs.putBoolean("backgroundSubtraction", backgroundSubtraction);
            sizeRollingBall = gd.getNextNumber();
            prefs.putDouble("sizeRollingBall", sizeRollingBall);
            indexFilterGUI = gd.getNextChoiceIndex();
            indexFilter = filterIndex[indexFilterGUI];
            prefs.put("filter", filters[indexFilterGUI]);
            radiusFilter = gd.getNextNumber();
            prefs.putDouble("radiusFilter", radiusFilter);

            minArea = gd.getNextNumber();
            prefs.putDouble("minArea", minArea);
            maxArea = gd.getNextNumber();
            prefs.putDouble("maxArea", maxArea);
            indexGlobalGUI = gd.getNextChoiceIndex();
            globalThresholdMethod = thresholdMethods[indexGlobalGUI];
            prefs.put("global", thresholds[indexGlobalGUI]);
            indexLocalGUI= gd.getNextChoiceIndex();
            localThresholdMethod = thresholdMethodsLocal[indexLocalGUI];
            prefs.put("local", thresholdsLocal[indexLocalGUI]);
            if (thresholdsLocal[indexLocalGUI].equals("-")) {
                twoPass = false;
            } else {
                twoPass = true;
            }
            refinement = gd.getNextBoolean();
            prefs.putBoolean("refinement", refinement);
            watershedThreshold = gd.getNextNumber();
            prefs.putDouble("watershedThreshold", watershedThreshold);

        } else if (functionality == 2) {
            gd = new GenericDialogPlus("Validation");

            gd.setInsets(0, 0, 0);
            gd.addMessage("General:", new Font(null, Font.BOLD, 12));
            gd.addDirectoryField("Choose Image Folder: ", prefs.get("imageFolder", ""), 12);
            gd.addDirectoryField("Choose Output Folder: ", prefs.get("outputFolder", ""), 12);
            gd.addNumericField("Calibration", prefs.getDouble("calibration", 0.19), 5, 10, "µm/px");
            gd.addChoice("Number Channels",channelOptions, prefs.get("nChannels","1"));
            gd.addChoice("Channel To Analyse", channelOptions, prefs.get("channel", "1"));
            gd.addChoice("Z Projection Method:", zProjOptions, prefs.get("zProjection","Average Intensity"));
            
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Generate Positive Control", prefs.getBoolean("posCtrl", true));
            gd.addDirectoryField("Ground truth Directory 0", prefs.get("GT0", ""), 12);
            gd.addDirectoryField("Ground truth Directory 1", prefs.get("GT1", ""), 12);
            gd.addDirectoryField("Ground truth Directory 2", prefs.get("GT2", ""), 12);

            gd.setInsets(20, 0, 0);
            gd.addMessage("PreProcessing:", new Font(null, Font.BOLD, 12));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Background Subtraction", prefs.getBoolean("backgroundSubtraction", true));
            gd.addNumericField("Size Rolling Ball", prefs.getDouble("sizeRollingBall", 20), 0, 3, "px");
            gd.addChoice("Filter", filters, prefs.get("filter", "-"));
            gd.addNumericField("Radius Filter", prefs.getDouble("radiusFilter", 1), 0, 3, "px");

            gd.setInsets(20, 0, 0);
            gd.addMessage("Segmentation:", new Font(null, Font.BOLD, 12));
            gd.addNumericField("Min Area", prefs.getDouble("minArea", 45), 0, 3, "µm²");
            gd.addNumericField("Max Area", prefs.getDouble("maxArea", 500), 0, 3, "µm²");

            gd.setInsets(0, inset, 0);   
            gd.addCheckbox("Scan Thresholds", prefs.getBoolean("scanThresholds", false));
            gd.addChoice("Global Threshold", thresholds, prefs.get("global", "Mean"));
            gd.addChoice("Local Threshold", thresholdsLocal, prefs.get("local", "Li"));
            gd.setInsets(0, inset, 0);
            gd.addCheckbox("Refinement", prefs.getBoolean("refinement", true));
            gd.setInsets(0, inset, 0);
            gd.addSlider("Watershed", 0, 1, prefs.getDouble("watershedThreshold", 0.75));
            gd.setInsets(0, inset, 0);
            gd.showDialog();
            if (gd.wasCanceled()) {
                System.out.println("PlugIn Cancelled");
                throw new RuntimeException(Macro.MACRO_CANCELED);
            }

            inputDirectory = gd.getNextString();
            if (inputDirectory.charAt(inputDirectory.length() - 1) != '/') {
                inputDirectory = inputDirectory + "/";
            }
            outputDirectory = gd.getNextString();
            if (outputDirectory.charAt(outputDirectory.length() - 1) != '/') {
                outputDirectory = outputDirectory + "/";
            }
            prefs.put("outputFolder", outputDirectory);
            prefs.put("imageFolder", inputDirectory);
            calibration = gd.getNextNumber();
            prefs.putDouble("calibration", calibration);
            nChannels=Integer.parseInt(gd.getNextChoice());
            prefs.put("nChannels", Integer.toString(nChannels));          
            channel=Integer.parseInt(gd.getNextChoice());
            prefs.put("channel", Integer.toString(channel));   
            String zProjString = gd.getNextChoice();
            zProjection=Arrays.asList(zProjOptions).indexOf(zProjString);
            prefs.put("zProjection", zProjString);
            
            posCtrl = gd.getNextBoolean();
            prefs.putBoolean("posCtrl", posCtrl);

            String selDir = gd.getNextString();
            int i = 0;
            while (selDir.equals("") == false) {
                if (selDir.charAt(selDir.length() - 1) != '/') {
                    selDir = selDir + "/";
                }
                GTDirectory.add(selDir);
                prefs.put("GT" + i, selDir);
                i++;
                if (i < 3) {
                    selDir = gd.getNextString();
                } else {
                    break;
                }
            }

            backgroundSubtraction = gd.getNextBoolean();
            prefs.putBoolean("backgroundSubtraction", backgroundSubtraction);
            sizeRollingBall = gd.getNextNumber();
            prefs.putDouble("sizeRollingBall", sizeRollingBall);
            indexFilterGUI = gd.getNextChoiceIndex();
            indexFilter = filterIndex[indexFilterGUI];
            prefs.put("filter", filters[indexFilterGUI]);
            radiusFilter = gd.getNextNumber();
            prefs.putDouble("radiusFilter", radiusFilter);

            minArea = gd.getNextNumber();
            prefs.putDouble("minArea", minArea);
            maxArea = gd.getNextNumber();
            prefs.putDouble("maxArea", maxArea);
            scanThresholds = gd.getNextBoolean();
            prefs.putBoolean("scanThresholds", scanThresholds);
            indexGlobalGUI = gd.getNextChoiceIndex();
            globalThresholdMethod = thresholdMethods[indexGlobalGUI];
            prefs.put("global", thresholds[indexGlobalGUI]);
            indexLocalGUI = gd.getNextChoiceIndex();
            localThresholdMethod = thresholdMethodsLocal[indexLocalGUI];
            prefs.put("local", thresholdsLocal[indexLocalGUI]);
            if (thresholdsLocal[indexLocalGUI].equals("-")) {
                twoPass = false;
            } else {
                twoPass = true;
            }
            refinement = gd.getNextBoolean();
            prefs.putBoolean("refinement", refinement);
            watershedThreshold = gd.getNextNumber();
            prefs.putDouble("watershedThreshold", watershedThreshold);
        } else if (functionality == 3) {
            gd = new GenericDialogPlus("Compare RoiLists");
            gd.setInsets(0, 0, 0);
            gd.addMessage("General:", new Font(null, Font.BOLD, 12));
            gd.addDirectoryField("Choose Output Folder: ", prefs.get("outputFolder", ""), 12);
            gd.addDirectoryField("Ground truth Directory 0", prefs.get("GT0", ""), 12);
            gd.addDirectoryField("Ground truth Directory 1", prefs.get("GT1", ""), 12);
            gd.addDirectoryField("Ground truth Directory 2", prefs.get("GT2", ""), 12);
            gd.addDirectoryField("Folder with roiLists to compare", prefs.get("roiListFolder", ""), 12);

            gd.addNumericField("Image Width (px)", prefs.getInt("imageWidth", 0), 0, 12, "px");
            gd.addNumericField("Image Height (px)", prefs.getInt("imageHeight", 0), 0, 12, "px");

            gd.showDialog();
            if (gd.wasCanceled()) {
                System.out.println("PlugIn Cancelled");
                throw new RuntimeException(Macro.MACRO_CANCELED);
            }

            outputDirectory = gd.getNextString();
            if (outputDirectory.charAt(outputDirectory.length() - 1) != '/') {
                outputDirectory = outputDirectory + "/";
            }
            prefs.put("outputFolder", outputDirectory);

            String selDir = gd.getNextString();
            int i = 0;
            while (selDir.equals("") == false && i < 3) {
                if (selDir.charAt(selDir.length() - 1) != '/') {
                    selDir = selDir + "/";
                }
                GTDirectory.add(selDir);
                prefs.put("GT" + i, selDir);
                selDir = gd.getNextString();
                i++;
            }
            while (i < 3) {
                selDir = gd.getNextString();
                i++;
            }
            if (selDir.charAt(selDir.length() - 1) != '/') {
                selDir = selDir + "/";
            }
            roiListDirectory = selDir;
            prefs.put("roiListFolder", roiListDirectory);
            impWidth = (int) gd.getNextNumber();
            prefs.putInt("imageWidth", impWidth);
            impHeight = (int) gd.getNextNumber();
            prefs.putInt("imageHeight", impHeight);
        } else if (gd.wasCanceled()) {
            System.out.println("PlugIn Cancelled");
            throw new RuntimeException(Macro.MACRO_CANCELED);
        }
        
        IJ.log("Settings------");
        IJ.log("OutputDir: "+outputDirectory);
        IJ.log("InputDir: "+inputDirectory);
        IJ.log("Calibration: "+calibration+"µm/px");  
        IJ.log("nChannels: "+nChannels);
        IJ.log("Channel: "+channel);
        IJ.log("zProjection: "+zProjOptions[zProjection]);
        IJ.log("Background Correction: "+backgroundSubtraction);
        IJ.log("Size Background Correction: "+ sizeRollingBall);
        IJ.log("Filter: "+filters[indexFilterGUI]);
        IJ.log("Radius Filter: "+radiusFilter);
        IJ.log("Min Area: "+ minArea + "µm2");
        IJ.log("Max Area: "+ maxArea + "µm2");
        IJ.log("Global Threshold: "+ thresholds[indexGlobalGUI]);
        IJ.log("Local Threshold: "+ thresholdsLocal[indexLocalGUI]);
        IJ.log("Contour Refinement: "+ refinement);
        IJ.log("Watershed Threshold "+ watershedThreshold);
        IJ.log("--------------");

    }

    public String getInputDirectory() {
        return inputDirectory;
    }
    
    public double getCalibration(){
        return calibration;
    }
    
    public int getNChannels(){
        return nChannels;
    }
    
    public int getChannel(){
        return channel;
    }
    
    public int getZProjection(){
        return zProjection;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public boolean getExtractFeatures() {
        return extractFeatures;
    }

    public AutoThresholder.Method getGlobalThresholdMethod() {
        return globalThresholdMethod;
    }

    public AutoThresholder.Method getLocalThresholdMethod() {
        return localThresholdMethod;
    }

    public int getFunctionality() {
        return functionality;
    }

    public ArrayList<String> getGTDirectory() {
        return GTDirectory;
    }

    public boolean getNumberThreshold() {
        return twoPass;
    }

    public boolean getRefinement() {
        return refinement;
    }

    public double getWatershed() {
        return watershedThreshold;
    }

    public boolean getRandomImages() {
        return randomImages;
    }
    
    public boolean getShowDebugImages() {
        return showDebugImages;
    }
    
    public boolean getSaveResultImages() {
        return saveResultImages;
    }

    public boolean getScanThresholds() {
        return scanThresholds;
    }
    

    public int getWidth() {
        return impWidth;
    }

    public int getHeight() {
        return impHeight;
    }

    public double getMinArea() {
        return minArea;
    }
    
    public double getMaxArea() {
        return maxArea;
    }

    public boolean getBackgroundSubstraction() {
        return backgroundSubtraction;
    }

    public double getSizeRollingBall() {
        return sizeRollingBall;
    }
    
    public int getIndexFilter(){
        return indexFilter;
    }
    
    public double getRadiusFitler(){
        return radiusFilter;
    }

    public String getRoiListDirectory() {
        return roiListDirectory;
    }

    public boolean getShowResultImages() {
        return showResultImages;
    }

    public boolean getSaveCroppedNuclei() {
        return saveCroppedNuclei;
    }

    public boolean getPosCtrl() {
        return posCtrl;
    }
}
