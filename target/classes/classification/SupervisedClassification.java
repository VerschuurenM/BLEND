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
package classification;

import GUI.GenericDialogPlus;
import Nucleus.Nucleus;
import com.sun.java.swing.plaf.windows.WindowsDesktopManager;
import ij.IJ;
import ij.gui.*;
import ij.gui.GenericDialog;
import ij.ImagePlus;
import ij.Macro;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;
import ij.io.OpenDialog;
import ij.WindowManager;
import ij.measure.Measurements;
import ij.plugin.ContrastEnhancer;
import ij.plugin.Duplicator;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;
import java.awt.*;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import weka.classifiers.trees.RandomForest;
import segmentation.Segmentation;
import weka.classifiers.functions.SMO;
import weka.classifiers.meta.FilteredClassifier;
import weka.filters.unsupervised.attribute.Remove;
import weka.classifiers.functions.supportVector.*;
import weka.core.Statistics;

public class SupervisedClassification {

    //General
    String inputDirectory;
    double calibration;
    int nChannels;
    int channel;
    int zProjection;
    String folder;
    String[] imageArray;
    boolean saveCroppedNuclei;
    ImagePlus impGUI;
    Overlay overlay;
    String sep = ",";

    //Segmentation
    Segmentation SegmentationObject;
    Nucleus[] nuclei;
    double rangeEdgeMicron;
    double dilationMicron;
    double profileWatershedMicron;

    //Feature Extraction
    ArrayList<String> textList = new ArrayList<String>();
    String fileName = "ResultsClassification.csv";
    String pathFeatures;

    //GUI
    JButton trainButton;
    JButton applyButton;
    JButton addClassButton;
    JButton saveClassifierButton;
    JButton loadClassifierButton;
    JButton[] labelButtons;
    JLabel predictedClassLabel;
    JButton correctButton;
    JButton redefineButton;
    JButton deleteButton;
    JButton previousButton;
    JPanel annotationsPanel = new JPanel();
    JPanel leftPanel = new JPanel();
    JPanel actionJPanel = new JPanel();
    JPanel optionsJPanel = new JPanel();
    Panel all = new Panel();
    GridBagLayout boxAnnotation = new GridBagLayout();
    GridBagConstraints annotationsConstraints = new GridBagConstraints();
    ExecutorService exec = Executors.newFixedThreadPool(1);
    Color[] colors = new Color[]{new Color(243, 119, 53), new Color(9, 0, 255), new Color(162, 0, 255), new Color(0, 199, 204), new Color(255, 0, 151)};
    String[] classLabels = new String[]{"New Class", "New Class", "New Class", "New Class", "New Class"};

    //Supervised Classification
    int numberOfClasses = 0;
    int indexClass;
    int beginImageIndex;
    int beginRoiIndex;
    int imageIndex;
    int nucleusIndex;
    boolean backwards;
    boolean waitForUserInput;
    boolean refinement;
    boolean classifierLoaded;
    boolean applyClassifier;
    String predictedClass;
    boolean rfUsed = false;
    RandomForest rf = new RandomForest();
    SMO svm = new SMO();
    FilteredClassifier fc = new FilteredClassifier();

    public SupervisedClassification(Segmentation SegmentationObject, double calibration, int nChannels, int channel, int zProjection, double dilationMicron, double rangeEdgeMicron, double profileWatershedMicron, String inputDirectory, String folder, boolean saveCroppedNuclei) {
        this.SegmentationObject = SegmentationObject;
        this.calibration = calibration;
        this.nChannels = nChannels;
        this.channel = channel;
        this.zProjection = zProjection;
        this.dilationMicron = dilationMicron;
        this.rangeEdgeMicron = rangeEdgeMicron;
        this.profileWatershedMicron = profileWatershedMicron;
        this.inputDirectory = inputDirectory;
        this.folder = folder;
        this.saveCroppedNuclei = saveCroppedNuclei;
        this.pathFeatures = folder + fileName;
    }

    public void exec() {
        //Set up classifier
        Remove rem = new Remove();
        rem.setAttributeIndices("1");
        fc.setFilter(rem);

        //Set-up GUI
        JFrame GUI = GUI();
        GUI.pack();
        Dimension screenSize = new Dimension(Toolkit.getDefaultToolkit().getScreenSize());
        GUI.setLocation(screenSize.width*2/3, 0);
        GUI.setVisible(true);

        //Get Input Images and loadProgress
        ArrayList<String> imageList = getImageArray(inputDirectory);
        Collections.shuffle(imageList, new Random(5));
        imageArray = new String[imageList.size()];
        imageList.toArray(imageArray);
        if (imageArray == null) {
            return;
        }
        LoadProgress();
        if (beginImageIndex < 0) {
            beginImageIndex = 0;
        }
        if (beginRoiIndex < 0) {
            beginRoiIndex = 0;
        }

        //Declare booleans and Images
        backwards = false;
        waitForUserInput = true;
        refinement = false;
        classifierLoaded = false;
        impGUI = null;
        ImagePlus impCrop = null;
        overlay = null;
//        if (IJ.getLog() != null) {
//            IJ.selectWindow("Log");
//            IJ.run("Close");
//        }
        if (textList.size() > 1) {
            previousButton.setEnabled(true);
        }

        //Declare cal
        Calibration cal = null;
        int dim = 0;

        //Loop over Images to assign Labels
        for (imageIndex = beginImageIndex; imageIndex < imageArray.length; imageIndex++) {
            //Segment Image and save roiList
            IJ.log("Segmenting Image: " + (imageIndex + 1) + "/" + imageArray.length + " ...");

            ImagePlus impStack = IJ.openImage(inputDirectory + imageArray[imageIndex]);
            Duplicator dup = new Duplicator();
            impGUI = new ImagePlus();
            if (!impStack.isHyperStack()) {
                if (nChannels != impStack.getDimensions()[2]) {
                    impGUI = dup.run(impStack, 1, 1, channel, channel, 1, 1);
                } else {
                    impGUI = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                }
            } else {
                impGUI = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                ZProjector zproj = new ZProjector(impGUI);
                zproj.setMethod(zProjection);
                zproj.doProjection();
                impGUI = zproj.getProjection();
            }
            cal = new Calibration(impGUI);
            cal.pixelWidth = calibration;
            cal.pixelHeight = calibration;
            cal.pixelDepth = calibration;
            cal.setUnit("µm");
            impGUI.setCalibration(cal);
            impGUI.setLut(LUT.createLutFromColor(Color.lightGray));
            impGUI.setTitle(impStack.getTitle());

            dim = (int) (50.0 / cal.pixelWidth);

            SegmentationObject.exec(impGUI, dilationMicron, rangeEdgeMicron, profileWatershedMicron);
            String impTitle = impGUI.getTitle();
            nuclei = SegmentationObject.getNuclei();

            overlay = getCurrentOverlay();
            if (nuclei.length != 0) {
                RoiManager rm = new RoiManager(false);
                for (int i = 0; i < nuclei.length; i++) {
                    rm.addRoi(nuclei[i].roiNucleus);
                    rm.runCommand("Save", folder + imageArray[imageIndex] + ".zip");
                }
            }

            //Show Image
            ImageWindow.setNextLocation(0, screenSize.height *1/3);
            impGUI.setOverlay(overlay);
            impGUI.show();

            //Loop over all detected Nuclei + assign label
            for (nucleusIndex = beginRoiIndex; nucleusIndex < nuclei.length; nucleusIndex++) {
                IJ.log("Classify Roi: " + (nucleusIndex + 1) + "/" + nuclei.length + " ...");
                //Classify nucleus
                impGUI.setRoi(nuclei[nucleusIndex].roiNucleus, true);
                
                                impCrop = IJ.createImage("Crop", dim, dim, 1, impGUI.getBitDepth());
                impCrop.setCalibration(cal);
                Rectangle r = new Rectangle(nuclei[nucleusIndex].roiNucleus.getBounds());
                ShapeRoi dupRoi = new ShapeRoi(nuclei[nucleusIndex].roiNucleus);
                int newI = (dim - r.width) / 2;
                int newJ = (dim - r.height) / 2;
                for (int i = r.x; i < (r.x + r.width); i++) {
                    for (int j = r.y; j < (r.y + r.height); j++) {
                        int pix = impGUI.getProcessor().getPixel(i, j);
                        impCrop.getProcessor().putPixel(newI, newJ, pix);
                        ImageWindow.setNextLocation(screenSize.width*2/3, screenSize.height*2/3);
                        impCrop.show();
                        newJ++;
                    }
                    newJ = (dim - r.height) / 2;
                    newI++;
                }
                impCrop.deleteRoi();
                dupRoi.setLocation((dim - r.width) / 2, (dim - r.height) / 2);
                impCrop.getProcessor().fillOutside(dupRoi);
                IJ.run(impCrop, "Enhance Contrast", "saturated=0.5");     
                impCrop.setRoi(dupRoi);
                ImageWindow.setNextLocation(screenSize.width*2/3, screenSize.height*2/3);
                impCrop.show();              

                //Enable training after 10 classified nuclei
                if (textList.size() > 10) {
                    trainButton.setEnabled(true);
                } else {
                    trainButton.setEnabled(false);
                }

                //WaitForUserInput
                while (waitForUserInput == true) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        System.out.println("Error! :(");
                    }
                }

                //Break out of loop when classifier is trained or loaded
                if (refinement || classifierLoaded) {
                    break;
                }

                //Reset
                waitForUserInput = true;

                //Close cropped image
                impCrop.changes = false;
                impCrop.close();
            }

            //Unable previous button when opening new image
            previousButton.setEnabled(false);
            beginRoiIndex = 0;

            //Break out of loop when classifier is trained
            if (refinement) {
                break;
            } //Break out of loop and reset when classifier is loaded
            else if (classifierLoaded) {
                imageIndex = 0;
                beginImageIndex = 0;
                nucleusIndex = 0;
                beginRoiIndex = 0;
                waitForUserInput = true;
                overlay = null;
                break;
            }
            //Save image with overlay
            if (!classifierLoaded) {
                IJ.saveAsTiff(impGUI, folder + imageArray[imageIndex] + "_Class.tiff");
                impGUI.close();
                overlay = null;
            }
        }

        //Reset Images and Results when new classifier is loaded
        if (classifierLoaded) {
            if (impGUI != null) {
                impGUI.changes = false;
                impGUI.close();
            }
            try {
                Path pathFile = FileSystems.getDefault().getPath(pathFeatures);
                Files.deleteIfExists(pathFile);
            } catch (IOException x) {
                System.err.println(x);
            }
        }

        //Close cropped image
        if (impCrop != null) {
            impCrop.changes = false;
            impCrop.close();
        }

        //Loop in refinement mode 
        waitForUserInput = true;
        beginImageIndex = imageIndex;
        beginRoiIndex = nucleusIndex;
        for (imageIndex = beginImageIndex; imageIndex < imageArray.length; imageIndex++) {
            IJ.log("Image: " + (imageIndex + 1) + "/" + imageArray.length);
            //Only open image and segment if it is not already open
            if (classifierLoaded || imageIndex != beginImageIndex) {
                ImagePlus impStack = IJ.openImage(inputDirectory + imageArray[imageIndex]);
                Duplicator dup = new Duplicator();
                impGUI = new ImagePlus();
                if (!impStack.isHyperStack()) {
                    if (nChannels != impStack.getDimensions()[2]) {
                        impGUI = dup.run(impStack, 1, 1, channel, channel, 1, 1);
                    } else {
                        impGUI = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                    }
                } else {
                    impGUI = dup.run(impStack, channel, channel, 1, impStack.getNSlices(), 1, 1);
                    ZProjector zproj = new ZProjector(impGUI);
                    zproj.setMethod(zProjection);
                    zproj.doProjection();
                    impGUI = zproj.getProjection();
                }
                cal = new Calibration(impGUI);
                cal.pixelWidth = calibration;
                cal.pixelHeight = calibration;
                cal.pixelDepth = calibration;
                cal.setUnit("µm");
                impGUI.setCalibration(cal);
                impGUI.setLut(LUT.createLutFromColor(Color.lightGray));
                impGUI.setTitle(impStack.getTitle());

                SegmentationObject.exec(impGUI, dilationMicron, rangeEdgeMicron, profileWatershedMicron);
                nuclei = SegmentationObject.getNuclei();
                if (nuclei.length != 0) {
                    RoiManager rm = new RoiManager(false);
                    for (int i = 0; i < nuclei.length; i++) {
                        rm.addRoi(nuclei[i].roiNucleus);
                        rm.runCommand("Save", folder + imageArray[imageIndex] + ".zip");
                    }
                }
                ImageWindow.setNextLocation(0, screenSize.height*1/3);
                impGUI.show();
                //Reset
                classifierLoaded = false;
            } else {

            }

            //Set current overlay
            overlay = getCurrentOverlay();
            impGUI.setOverlay(overlay);

            //If nucleusIndex = 0; disable previous button
            if (nucleusIndex < 0) {
                nucleusIndex = 0;
            }
            if (beginRoiIndex == 0) {
                previousButton.setEnabled(false);
            }

            //Loop over Nuclei in image
            for (nucleusIndex = beginRoiIndex; nucleusIndex < nuclei.length; nucleusIndex++) {
                IJ.log("Roi: " + (nucleusIndex + 1) + "/" + nuclei.length);

                //SetButtons
                correctButton.setEnabled(true);
                redefineButton.setEnabled(true);
                for (int i = 0; i < classLabels.length; i++) {
                    labelButtons[i].setEnabled(false);
                }
                trainButton.setText("Re-train classifier");
                addClassButton.setEnabled(false);
                if (textList.size() > 10) {
                    trainButton.setEnabled(true);
                } else {
                    trainButton.setEnabled(false);
                }

                //Predict Class
                PredictClass PC = new PredictClass(folder, inputDirectory);
                //predictedClass = PC.exec(fc, imageArray, imageIndex, nuclei[nucleusIndex], classLabels);
                predictedClass = PC.exec(fc, impGUI, nuclei[nucleusIndex], classLabels);
                indexClass = Arrays.asList(classLabels).indexOf(predictedClass);
                predictedClassLabel.setText(predictedClass);
                predictedClassLabel.setForeground(colors[indexClass]);
                predictedClassLabel.setEnabled(true);
                nuclei[nucleusIndex].roiNucleus.setStrokeColor(colors[indexClass]);

                //Classify nucleus
                impGUI.setRoi(nuclei[nucleusIndex].roiNucleus, true);
                
                impCrop = IJ.createImage("Crop", dim, dim, 1, impGUI.getBitDepth());
                impCrop.setCalibration(cal);
                Rectangle r = new Rectangle(nuclei[nucleusIndex].roiNucleus.getBounds());
                ShapeRoi dupRoi = new ShapeRoi(nuclei[nucleusIndex].roiNucleus);
                int newI = (dim - r.width) / 2;
                int newJ = (dim - r.height) / 2;
                for (int i = r.x; i < (r.x + r.width); i++) {
                    for (int j = r.y; j < (r.y + r.height); j++) {
                        int pix = impGUI.getProcessor().getPixel(i, j);
                        impCrop.getProcessor().putPixel(newI, newJ, pix);
                        impCrop.show();
                        newJ++;
                    }
                    newJ = (dim - r.height) / 2;
                    newI++;
                }
                impCrop.deleteRoi();
                dupRoi.setLocation((dim - r.width) / 2, (dim - r.height) / 2);
                impCrop.getProcessor().fillOutside(dupRoi);
                IJ.run(impCrop, "Enhance Contrast", "saturated=0.5");     
                dupRoi.setStrokeColor(colors[indexClass]);
                impCrop.setRoi(dupRoi);
                ImageWindow.setNextLocation(screenSize.width*2/3, screenSize.height*2/3);
                impCrop.show();
                
                while (waitForUserInput == true) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        System.out.println("Error! :(");
                    }
                }

                //If classifier is loaded, restart classification process
                if (classifierLoaded) {
                    overlay = null;
                    imageIndex = -1;
                    beginImageIndex = -1;
                    nucleusIndex = -1;
                    beginRoiIndex = -1;
                    waitForUserInput = true;
                    break;
                } else if (applyClassifier) {
                    AssignClass AC = new AssignClass(folder, saveCroppedNuclei, inputDirectory);
                    //textList = AC.exec(imageArray, nuclei, imageIndex, nucleusIndex, textList, classLabels[indexClass]);
                    impGUI.deleteRoi();
                    textList = AC.exec(impGUI.duplicate(), nuclei, nucleusIndex, textList, classLabels[indexClass]);
                    nuclei[nucleusIndex].roiNucleus.setStrokeColor(colors[indexClass]);
                    overlay.add(nuclei[nucleusIndex].roiNucleus);
                    impGUI.setOverlay(overlay);
                }

                //Reset only if classifier is not applied on whole dataset
                if (!applyClassifier) {
                    waitForUserInput = true;
                }

                //Close cropped image
                impCrop.changes = false;
                impCrop.close();
            }

            //Unable previous button when opening new image
            previousButton.setEnabled(false);
            beginRoiIndex = 0;

            //Save image with overlay
            if (!classifierLoaded) {
                IJ.saveAsTiff(impGUI, folder + imageArray[imageIndex] + "_Class.tiff");
                impGUI.close();
            }
        }

        //If all nuclei are classified; ask to train classifier
        if (!applyClassifier) {
            if (new YesNoCancelDialog(new Frame(), "BLEND", "All nuclei are labeled. Would you like to train the classifier?").yesPressed()) {
                boolean GUICanceled = guiTypeClassifier();
                if (rfUsed) {
                    GUICanceled = guiRf();
                    IJ.log("TEST: " + String.valueOf(GUICanceled));
                } else {
                    GUICanceled = guiSvm();
                }
                if (!GUICanceled) {
                    try {
                        System.out.println("Train");
                        TrainClassifier TC = new TrainClassifier(fc, pathFeatures);
                        fc = TC.exec();
                        waitForUserInput = false;
                        saveClassifierButton.setEnabled(true);
                        applyButton.setEnabled(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                trainButton.setEnabled(true);
            }
        }
    }

    public JFrame GUI() {
        //Setup Buttons
        addClassButton = new JButton("Create New Class");
        addClassButton.setToolTipText("Add one more label to mark different areas");
        labelButtons = new JButton[classLabels.length];
        trainButton = new JButton("Train Classifier");
        trainButton.setToolTipText("Start training the classifier");
        trainButton.setEnabled(false);
        applyButton = new JButton("Apply Classifier ");
        applyButton.setToolTipText("Apply classifier on full image set");
        applyButton.setEnabled(false);
        saveClassifierButton = new JButton("Save Classifier");
        saveClassifierButton.setToolTipText("Save Current Classifier");
        saveClassifierButton.setEnabled(false);
        loadClassifierButton = new JButton("Load Classifier");
        loadClassifierButton.setToolTipText("Load Classifier");
        correctButton = new JButton("Correct");
        correctButton.setToolTipText("Correct Classified Nucleus");
        correctButton.setForeground(new Color(0, 177, 89));
        correctButton.setEnabled(false);
        redefineButton = new JButton("Redefine");
        redefineButton.setToolTipText("Redefine Class");
        redefineButton.setForeground(new Color(209, 17, 65));
        redefineButton.setEnabled(false);
        deleteButton = new JButton("Delete");
        deleteButton.setToolTipText("Delete Nucleus");
        previousButton = new JButton("Previous");
        previousButton.setToolTipText("Previous Nuclei");
        previousButton.setEnabled(false);

        //Setup Label
        predictedClassLabel = new JLabel("", SwingConstants.CENTER);
        predictedClassLabel.setEnabled(false);

        for (int i = 0; i < classLabels.length; i++) {
            labelButtons[i] = new JButton(classLabels[i]);
            labelButtons[i].setToolTipText("Add markings of label '" + classLabels[i] + "'");
            labelButtons[i].setEnabled(false);
        }

        //Initialize JFrame
        JFrame win = new JFrame("BLEND");

        // Add listeners
        addClassButton.addActionListener(listener);
        for (int i = 0; i < classLabels.length; i++) {
            labelButtons[i].addActionListener(listener);
        }
        trainButton.addActionListener(listener);
        applyButton.addActionListener(listener);
        saveClassifierButton.addActionListener(listener);
        loadClassifierButton.addActionListener(listener);
        correctButton.addActionListener(listener);
        redefineButton.addActionListener(listener);
        deleteButton.addActionListener(listener);
        previousButton.addActionListener(listener);

        // Annotations panel
        annotationsConstraints.anchor = GridBagConstraints.NORTHWEST;
        annotationsConstraints.fill = GridBagConstraints.HORIZONTAL;
        annotationsConstraints.insets = new Insets(5, 5, 6, 6);
        annotationsConstraints.gridwidth = 1;
        annotationsConstraints.gridheight = 1;
        annotationsConstraints.gridx = 0;
        annotationsConstraints.gridy = 0;
        annotationsPanel.setBorder(BorderFactory.createTitledBorder("Labels"));
        annotationsPanel.setLayout(boxAnnotation);
        for (int i = 0; i < classLabels.length; i++) {

            annotationsPanel.add(labelButtons[i], annotationsConstraints);
            annotationsConstraints.gridy++;
        }
        annotationsConstraints.gridy = 0;
        annotationsConstraints.gridx++;
        annotationsPanel.add(previousButton, annotationsConstraints);
        annotationsConstraints.gridy++;
        annotationsPanel.add(deleteButton, annotationsConstraints);
        annotationsConstraints.gridy++;
        annotationsConstraints.insets = new Insets(9, 0, 0, 0);
        annotationsPanel.add(predictedClassLabel, annotationsConstraints);
        annotationsConstraints.insets = new Insets(5, 5, 6, 6);
        annotationsConstraints.gridy++;
        annotationsPanel.add(correctButton, annotationsConstraints);
        annotationsConstraints.gridy++;
        annotationsPanel.add(redefineButton, annotationsConstraints);

        // Action panel (left side of the GUI)
        actionJPanel.setBorder(BorderFactory.createTitledBorder("Action"));
        GridBagLayout actionLayout = new GridBagLayout();
        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.anchor = GridBagConstraints.NORTHWEST;
        actionConstraints.fill = GridBagConstraints.HORIZONTAL;
        actionConstraints.insets = new Insets(5, 5, 6, 6);
        actionConstraints.gridwidth = 1;
        actionConstraints.gridheight = 1;
        actionConstraints.gridx = 0;
        actionConstraints.gridy = 0;
        actionJPanel.setLayout(actionLayout);
        actionJPanel.add(trainButton, actionConstraints);
        actionConstraints.gridy++;
        actionJPanel.add(applyButton, actionConstraints);
        actionConstraints.gridy++;

        // Options panel
        optionsJPanel.setBorder(BorderFactory.createTitledBorder("Options"));
        GridBagLayout optionsLayout = new GridBagLayout();
        GridBagConstraints optionsConstraints = new GridBagConstraints();
        optionsConstraints.anchor = GridBagConstraints.NORTHWEST;
        optionsConstraints.fill = GridBagConstraints.HORIZONTAL;
        optionsConstraints.insets = new Insets(5, 5, 6, 6);
        optionsConstraints.gridwidth = 1;
        optionsConstraints.gridheight = 1;
        optionsConstraints.gridx = 0;
        optionsConstraints.gridy = 0;
        optionsJPanel.setLayout(optionsLayout);
        optionsJPanel.add(addClassButton, optionsConstraints);
        optionsConstraints.gridy++;
        optionsJPanel.add(saveClassifierButton, optionsConstraints);
        optionsConstraints.gridy++;
        optionsJPanel.add(loadClassifierButton, optionsConstraints);
        optionsConstraints.gridy++;

        // Buttons panel (including training and options)
        GridBagLayout leftPanelLayout = new GridBagLayout();
        GridBagConstraints leftPanelConstraints = new GridBagConstraints();
        leftPanel.setLayout(leftPanelLayout);
        leftPanelConstraints.anchor = GridBagConstraints.NORTHWEST;
        leftPanelConstraints.fill = GridBagConstraints.HORIZONTAL;
        leftPanelConstraints.gridwidth = 1;
        leftPanelConstraints.gridheight = 1;
        leftPanelConstraints.gridx = 0;
        leftPanelConstraints.gridy = 0;
        leftPanel.add(actionJPanel, leftPanelConstraints);
        leftPanelConstraints.gridy++;
        leftPanel.add(optionsJPanel, leftPanelConstraints);
        leftPanelConstraints.gridy++;
        leftPanelConstraints.insets = new Insets(5, 5, 6, 6);

        GridBagLayout layout = new GridBagLayout();
        GridBagConstraints allConstraints = new GridBagConstraints();
        all.setLayout(layout);

        allConstraints.anchor = GridBagConstraints.NORTHWEST;
        allConstraints.fill = GridBagConstraints.BOTH;
        allConstraints.gridwidth = 1;
        allConstraints.gridheight = 1;
        allConstraints.gridx = 0;
        allConstraints.gridy = 0;
        allConstraints.weightx = 0;
        allConstraints.weighty = 0;
        all.add(leftPanel, allConstraints);
        allConstraints.gridx++;
        allConstraints.weightx = 1;
        allConstraints.weighty = 1;
        allConstraints.gridx++;
        allConstraints.anchor = GridBagConstraints.NORTHEAST;
        allConstraints.weightx = 0;
        allConstraints.weighty = 0;
        all.add(annotationsPanel, allConstraints);
        GridBagLayout wingb = new GridBagLayout();
        GridBagConstraints winc = new GridBagConstraints();
        winc.anchor = GridBagConstraints.NORTHWEST;
        winc.fill = GridBagConstraints.BOTH;
        winc.weightx = 1;
        winc.weighty = 1;
        win.setLayout(wingb);
        win.add(all, winc);

        win.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                //IJ.log("closing window");
                // cleanup
                exec.shutdownNow();
                addClassButton.removeActionListener(listener);
                for (int i = 0; i < classLabels.length; i++) {
                    labelButtons[i].removeActionListener(listener);
                }
                trainButton.removeActionListener(listener);
                applyButton.removeActionListener(listener);
                saveClassifierButton.removeActionListener(listener);
                loadClassifierButton.removeActionListener(listener);
                correctButton.removeActionListener(listener);
                redefineButton.removeActionListener(listener);
                previousButton.removeActionListener(listener);
                deleteButton.removeActionListener(listener);
            }
        });
        return win;
    }
    protected ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
            // listen to the buttons
            exec.submit(new Runnable() {
                public void run() {
                    if (e.getSource() == trainButton) {
                        //Open subsequent GUI for Classifier settings
                        boolean GUICanceled = guiTypeClassifier();
                        if (rfUsed) {
                            GUICanceled = guiRf();
                        } else {
                            GUICanceled = guiSvm();
                        }
                        if (!GUICanceled) {
                            try {
                                System.out.println("Train");
                                TrainClassifier TC = new TrainClassifier(fc, pathFeatures);
                                fc = TC.exec();
                                waitForUserInput = false;
                                if (refinement) {
                                    nucleusIndex = nucleusIndex - 1;
                                }
                                saveClassifierButton.setEnabled(true);
                                applyButton.setEnabled(true);
                                refinement = true;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (e.getSource() == applyButton) {
                        applyClassifier = true;
                        waitForUserInput = false;
                    } else if (e.getSource() == saveClassifierButton) {
                        String file = saveClassifier();
                        IJ.log("-----------------------------------------------");
                        IJ.log("Saved: " + file);
                        IJ.log(fc.toString());
                        IJ.log("-----------------------------------------------");
                    } else if (e.getSource() == loadClassifierButton) {
                        boolean canceled = false;
                        boolean proceed = false;
                        while (!proceed) {
                            canceled = loadClassifier();
                            if (!canceled) {
                                canceled = guiDefineClasses();
                            }
                            if (!canceled) {
                                //Test classes
                                PredictClass PCTest = new PredictClass(folder, inputDirectory);
                                //String test = PCTest.exec(fc, imageArray, imageIndex, nuclei[nucleusIndex], classLabels);  
                                String test = PCTest.exec(fc, impGUI, nuclei[nucleusIndex], classLabels);

                                boolean errorClasses = PCTest.getErrorPredict();
                                if (errorClasses) {
                                    MessageDialog MD = new MessageDialog(new Frame(), "BLEND", "Defined classes do not correlate with classes used for training the classifier");
                                } else {
                                    proceed = true;
                                    IJ.log("-----------------------------------------------");
                                    IJ.log("Loaded: ");
                                    IJ.log(fc.toString());
                                    IJ.log("-----------------------------------------------");
                                }
                            } else {
                                break;
                            }
                        }
                        if (!canceled) {
                            applyButton.setEnabled(true);
                            classifierLoaded = true;
                            waitForUserInput = false;
                        }
                    } else if (e.getSource() == addClassButton) {
                        addNewClass();
                    } else if (e.getSource() == previousButton) {
                        //Find previous nucleusIndex
                        int tempRoiIndex = nucleusIndex;
                        String temp = textList.get(textList.size() - 1);
                        int position = temp.indexOf(sep);
                        String tempImage = temp.substring(0, position);
                        if (tempImage.equals(imageArray[imageIndex])) {
                            int position2 = temp.indexOf(sep, position + 1);
                            tempRoiIndex = Integer.parseInt(temp.substring(position + 1, position2));
                        }

                        //Remove previous result line and cropped image
                        textList.remove(textList.size() - 1);
                        try {
                            FileWriter fstreamAdd = new FileWriter(pathFeatures, false);
                            BufferedWriter outAdd = new BufferedWriter(fstreamAdd);
                            for (int i = 0; i < textList.size(); i++) {
                                outAdd.write(textList.get(i));
                            }
                            outAdd.close();
                        } catch (IOException e1) {
                            System.out.println(e1.toString());
                        }
                        if (saveCroppedNuclei) {
                            nucleusIndex = tempRoiIndex;
                            deleteCropped();
                        }

                        //Remove overlay previous nuclei
                        overlay.remove(nuclei[tempRoiIndex].roiNucleus);
                        impGUI.setOverlay(overlay);
                        nuclei[tempRoiIndex].roiNucleus.setStrokeColor(Color.yellow);
                        nucleusIndex = tempRoiIndex - 1;
                        waitForUserInput = false;
                    } else if (e.getSource() == deleteButton) {
                        boolean delete = false;

                        if (textList.size() != 0) {
                            //Check if result was in resultfile and delete
                            String temp = textList.get(textList.size() - 1);
                            int position = temp.indexOf(sep);
                            String tempImage = temp.substring(0, position);
                            if (tempImage.equals(imageArray[imageIndex])) {
                                int position2 = temp.indexOf(sep, position + 1);
                                int tempRoiIndex = Integer.parseInt(temp.substring(position + 1, position2));
                                if (tempRoiIndex == nucleusIndex) {
                                    delete = true;
                                }
                            }
                        } else {

                        }
                        if (delete == true) {
                            textList.remove(nucleusIndex);
                            try {
                                FileWriter fstreamAdd = new FileWriter(pathFeatures, false);
                                BufferedWriter outAdd = new BufferedWriter(fstreamAdd);
                                for (int i = 0; i < textList.size(); i++) {
                                    outAdd.write(textList.get(i));
                                }
                                outAdd.close();
                            } catch (IOException e1) {
                                System.out.println(e1.toString());
                            }
                            overlay.remove(nuclei[nucleusIndex].roiNucleus);
                        }

                        //Delete cropped Nucleus
                        if (saveCroppedNuclei) {
                            deleteCropped();
                        }
                        delete = false;
                        previousButton.setEnabled(true);
                        waitForUserInput = false;
                    } else if (e.getSource() == correctButton) {
                        //Assign predicted class
                        AssignClass AC = new AssignClass(folder, saveCroppedNuclei, inputDirectory);
                        //textList = AC.exec(imageArray, nuclei, imageIndex, nucleusIndex, textList, classLabels[indexClass]);
                        impGUI.deleteRoi();
                        textList = AC.exec(impGUI.duplicate(), nuclei, nucleusIndex, textList, classLabels[indexClass]);
                        nuclei[nucleusIndex].roiNucleus.setStrokeColor(colors[indexClass]);
                        overlay.add(nuclei[nucleusIndex].roiNucleus);
                        impGUI.setOverlay(overlay);
                        previousButton.setEnabled(true);
                        waitForUserInput = false;
                    } else if (e.getSource() == redefineButton) {
                        for (int i = 0; i < classLabels.length; i++) {
                            if (i < numberOfClasses) {
                                labelButtons[i].setEnabled(true);
                            }
                        }
                        addClassButton.setEnabled(true);
                        predictedClassLabel.setText("");
                        predictedClassLabel.setEnabled(false);
                        correctButton.setEnabled(false);
                        redefineButton.setEnabled(false);
                    } else {
                        for (int i = 0; i < classLabels.length; i++) {
                            if (e.getSource() == labelButtons[i]) {
                                indexClass = i;
                                AssignClass AC = new AssignClass(folder, saveCroppedNuclei, inputDirectory);
                                //textList = AC.exec(imageArray, nuclei, imageIndex, nucleusIndex, textList, classLabels[indexClass]);
                                impGUI.deleteRoi();
                                textList = AC.exec(impGUI.duplicate(), nuclei, nucleusIndex, textList, classLabels[indexClass]);
                                waitForUserInput = false;
                                previousButton.setEnabled(true);
                                nuclei[nucleusIndex].roiNucleus.setStrokeColor(colors[indexClass]);
                                overlay.add(nuclei[nucleusIndex].roiNucleus);
                                impGUI.setOverlay(overlay);
                                break;
                            }
                        }
                    }

                }
            }
            );
        }

    };

    private ArrayList<String> getImageArray(String directoryName) {
        //Put all the names of the image-files in String[] + sort
        String[] fileArray = new File(directoryName).list();
        Arrays.sort(fileArray);
        //Check the extensions of the files in fileArray and put the file-names in files if correct
        ArrayList<String> imageArray = new ArrayList<String>();
        for (int i = 0; i < fileArray.length; i++) {
            if (fileArray[i].indexOf(".tif") > 0 || fileArray[i].indexOf(".dcm") > 0 || fileArray[i].indexOf(".fits") > 0
                    || fileArray[i].indexOf(".fit") > 0 || fileArray[i].indexOf(".fts") > 0 || fileArray[i].indexOf(".pgm") > 0
                    || fileArray[i].indexOf(".jpg") > 0 || fileArray[i].indexOf(".jpeg") > 0 || fileArray[i].indexOf(".bmp") > 0) {
                imageArray.add(fileArray[i]);
            }
        }
        return imageArray;
    }

    private Overlay getCurrentOverlay() {
        Overlay overlay = new Overlay();
        String currentImage = imageArray[imageIndex];
        ArrayList<String> assignedClasses = new ArrayList<String>();
        ArrayList<Integer> assignedIndexes = new ArrayList<Integer>();
        for (int i = 1; i < textList.size(); i++) {
            String temp = textList.get(i);
            int position = temp.indexOf(sep);
            String tempImage = temp.substring(0, position);
            if (tempImage.equals(currentImage)) {
                int position2 = temp.indexOf(sep, position + 1);
                int roiIndex = Integer.parseInt(temp.substring(position + 1, position2));
                assignedIndexes.add(roiIndex);
                int positionLast = temp.lastIndexOf(sep);
                String newLabel = temp.substring(positionLast + 1, temp.length() - 1);
                assignedClasses.add(newLabel);
            }

        }
        for (int i = 0; i < assignedClasses.size(); i++) {
            int roiIndex = assignedIndexes.get(i);
            indexClass = Arrays.asList(classLabels).indexOf(assignedClasses.get(i));
            nuclei[roiIndex].roiNucleus.setStrokeColor(colors[indexClass]);
            overlay.add(nuclei[roiIndex].roiNucleus);
        }
        return overlay;
    }

    private void LoadProgress() {

        beginRoiIndex = -1;
        beginImageIndex = -1;
        String previousImage = "";
        String currentImage;
        try {
            FileInputStream fstream = new FileInputStream(pathFeatures);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            //String strLine = br.readLine();
            String strLine = br.readLine();
            textList.add(strLine + "\n");
            while ((strLine = br.readLine()) != null) {
                int position = strLine.indexOf(sep);
                currentImage = strLine.substring(0, position);
                beginRoiIndex = Integer.parseInt(strLine.substring(position + 1, strLine.indexOf(sep, position + 1)));
                beginRoiIndex = beginRoiIndex + 1;
                textList.add(strLine + "\n");
                if (previousImage.indexOf(currentImage) < 0) {
                    beginImageIndex = beginImageIndex + 1;
                }
                previousImage = currentImage;

                position = strLine.lastIndexOf(sep);
                String newLabel = strLine.substring(position + 1, strLine.length());
                boolean alreadyDefined = Arrays.asList(classLabels).contains(newLabel);
                if (!alreadyDefined) {
                    classLabels[numberOfClasses] = newLabel;
                    labelButtons[numberOfClasses].setText(newLabel);
                    labelButtons[numberOfClasses].setOpaque(true);
                    labelButtons[numberOfClasses].setForeground(colors[numberOfClasses]);
                    labelButtons[numberOfClasses].setEnabled(true);
                    numberOfClasses++;
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        int k = 0;
        for (int i = 0; i < imageArray.length; i++) {
            if (imageArray[i].indexOf(".tif") > 0 || imageArray[i].indexOf(".dcm") > 0 || imageArray[i].indexOf(".fits") > 0
                    || imageArray[i].indexOf(".fit") > 0 || imageArray[i].indexOf(".fts") > 0 || imageArray[i].indexOf(".pgm") > 0
                    || imageArray[i].indexOf(".jpg") > 0 || imageArray[i].indexOf(".jpeg") > 0 || imageArray[i].indexOf(".bmp") > 0) {
                k = k + 1;
            } else {
                imageArray[i] = "";
            }
        }
        int j = 0;
        String[] imageArrayReturn = new String[k];
        for (int i = 0; i < imageArray.length; i++) {
            if (imageArray[i] != "") {
                imageArrayReturn[i - j] = imageArray[i];
            } else {
                j = j + 1;
            }
        }
        imageArray = imageArrayReturn;
    }

    private void addNewClass() {
        if (numberOfClasses >= classLabels.length) {
            IJ.showMessage("Supervised Classification ", "Sorry, maximum number of classes has been reached");
            return;
        }
        String inputName = JOptionPane.showInputDialog("Please input a new label name");
        if (null == inputName) {
            return;
        }
        if (null == inputName || 0 == inputName.length()) {
            IJ.error("Invalid name for class");
            return;
        }
        inputName = inputName.trim();
        if (0 == inputName.toLowerCase().indexOf("add to ")) {
            inputName = inputName.substring(7);
        }
        // Add new name to the list of labels
        classLabels[numberOfClasses] = inputName;
        labelButtons[numberOfClasses].setText(inputName);
        labelButtons[numberOfClasses].setForeground(colors[numberOfClasses]);
        labelButtons[numberOfClasses].setEnabled(true);
        numberOfClasses++;
    }

    public String saveClassifier() {
        String date = new SimpleDateFormat("yyyy-MM-dd hh-mm-ss").format(new Date());
        try {
            String path = folder + "RandomForest_" + date + ".model";
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path));
            oos.writeObject(fc);
            oos.flush();
            oos.close();
            return path;
        } catch (IOException e) {
            System.out.println(e.toString());
            return new String("Could not save the classifier");
        }
    }

    public boolean loadClassifier() {
        boolean cancel = false;
        boolean fileLoaded = false;
        while (!fileLoaded) {
            boolean error = false;
            OpenDialog od = new OpenDialog("Choose randomForest~.model file", folder, "");
            if (od.getFileName() == null) {
                cancel = true;
                break;
            }
            String file = od.getDirectory() + od.getFileName();
            File selected = new File(file);
            try {
                InputStream is = new FileInputStream(selected);
                try {
                    ObjectInputStream objectInputStream = new ObjectInputStream(is);
                    try {
                        fc = (FilteredClassifier) (objectInputStream.readObject());
                    } catch (ClassNotFoundException e) {
                        System.out.println(e.toString());
                        error = true;
                    }
                } catch (IOException e) {
                    System.out.println(e.toString());
                    error = true;
                    MessageDialog md = new MessageDialog(new Frame(), "BLEND", "Invalid file: Choose ~.model file.");
                }
            } catch (FileNotFoundException e) {
                System.out.println(e.toString());
                error = true;
            }
            if (error == true) {
                fileLoaded = false;
            } else {
                fileLoaded = true;
            }
        }
        return cancel;
    }

    public boolean guiTypeClassifier() {
        boolean cancel = false;
        GenericDialog gd = new GenericDialog("Classifier:");
        String types[] = {"Random Forest", "Support Vector Machine"};
        gd.addRadioButtonGroup("Classifier", types, 1, 2, "Random Forest");
        gd.showDialog();
        String classifier = gd.getNextRadioButton();
        if (gd.wasCanceled()) {
            System.out.println("PlugIn Cancelled");
            cancel = true;
        } else if (classifier == types[0]) {
            rfUsed = true;
        } else if (classifier == types[1]) {
            rfUsed = false;
        }
        return cancel;
    }

    public boolean guiRf() {
        boolean cancel = false;
        GenericDialog gd = new GenericDialog("Settings Random Forest");
        gd.addNumericField("Number of features", rf.getNumFeatures(), 0);
        gd.showDialog();
        if (gd.wasCanceled()) {
            System.out.println("PlugIn Cancelled");
            cancel = true;
        } else {
            rf.setNumFeatures((int) gd.getNextNumber());
            fc.setClassifier(rf);
        }
        return cancel;
    }

    public boolean guiSvm() {
        boolean cancel = false;
        GenericDialog gd = new GenericDialog("Settings SVM");
        String kernels[] = {"RbfKernel", "Normalized PolyKernel", "PolyKernel", "Puk"};
        gd.addChoice("Kernel", kernels, "RbfKernel");
        gd.showDialog();
        String chosenKernel = gd.getNextChoice();
        if (gd.wasCanceled()) {
            System.out.println("PlugIn Cancelled");
            cancel = true;
        } else if (chosenKernel == kernels[0]) {
            RBFKernel kernel = new RBFKernel();
            svm.setKernel(kernel);
        } else if (chosenKernel == kernels[1]) {
            NormalizedPolyKernel kernel = new NormalizedPolyKernel();
            svm.setKernel(kernel);
        } else if (chosenKernel == kernels[2]) {
            PolyKernel kernel = new PolyKernel();
            svm.setKernel(kernel);
        } else if (chosenKernel == kernels[3]) {
            Puk kernel = new Puk();
            svm.setKernel(kernel);
        }
        fc.setClassifier(svm);
        return cancel;
    }

    public boolean guiDefineClasses() {
        boolean cancel = false;
        GenericDialog gd = new GenericDialog("Classes Random Forest");
        for (int i = 0; i < classLabels.length; i++) {
            gd.addStringField("Class " + i, classLabels[i]);
        }
        gd.showDialog();
        if (gd.wasCanceled()) {
            cancel = true;
        } else {
            for (int i = 0; i < classLabels.length; i++) {
                classLabels[i] = gd.getNextString();
                labelButtons[i].setText(classLabels[i]);
            }
        }
        return cancel;
    }

    public void deleteCropped() {
        String[] fileArray = new File(folder + "CroppedImages/").list();
        if (fileArray != null) {
            Arrays.sort(fileArray);
            for (int i = 0; i < fileArray.length; i++) {
                if (fileArray[i].indexOf(".tif") > 0) {
                    int position = fileArray[i].indexOf(";");
                    String imageTemp = fileArray[i].substring(0, position);
                    int position2 = fileArray[i].indexOf(";", position + 1);
                    int roiIndexTemp = Integer.parseInt(fileArray[i].substring(position + 1, position2));
                    if (imageTemp.equals(imageArray[imageIndex]) && roiIndexTemp == nucleusIndex) {
                        try {
                            Path pathFile = FileSystems.getDefault().getPath(folder + "CroppedImages/" + fileArray[i]);
                            Files.deleteIfExists(pathFile);
                        } catch (IOException x) {
                            System.err.println(x);
                        }
                    }
                }
            }
        }
    }
}
