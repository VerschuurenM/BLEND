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

import GUI.GenericDialogPlus;
import Nucleus.Nucleus;
import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.WindowManager;
import ij.gui.*;
import ij.measure.Calibration;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.RoiManager;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import java.util.*;

public class Segmentation {

    boolean showDebugImages;

    boolean backgroundSubtraction;
    double sizeRollingBall;
    int indexFilter;
    double radiusFilter;

    boolean twoPass;
    AutoThresholder.Method globalThresholdMethod;
    AutoThresholder.Method localThresholdMethod;

    double minArea;
    double maxArea;
    boolean refinement;
    double watershed;

    Nucleus[] nuclei;
    double[] measureFGBG;
    boolean goodInitialSegmentation = true;

    public Segmentation(boolean showDebugImages, boolean backgroundSubstraction, double sizeRollingBall, int indexFilter, double radiusFilter, boolean twoPass, AutoThresholder.Method globalThresholdMethod, AutoThresholder.Method localThresholdMethod, boolean refinement, double watershed, double minArea, double maxArea) {
        this.showDebugImages = showDebugImages;
        this.backgroundSubtraction = backgroundSubstraction;
        this.sizeRollingBall = sizeRollingBall;
        this.indexFilter = indexFilter;
        this.radiusFilter = radiusFilter;
        this.twoPass = twoPass;
        this.globalThresholdMethod = globalThresholdMethod;
        this.localThresholdMethod = localThresholdMethod;
        this.refinement = refinement;
        this.watershed = watershed;
        this.minArea = minArea;
        this.maxArea = maxArea;
    }

    public void exec(ImagePlus imp, double dilationMicron, double rangeEdgeMicron, double profileWatershedMicron) {
        if (imp.getProcessor().maxValue() != 0) {
            String impTitle = imp.getTitle();
            Calibration cal = imp.getLocalCalibration();
            int rangeEdge = Math.round((float) (rangeEdgeMicron) / (float) (cal.pixelWidth));
            if (rangeEdge < 4) {
                rangeEdge = 4;
            }
            int dilations = Math.round((float) (dilationMicron) / (float) (cal.pixelWidth));
            int profileWatershed = Math.round((float) (profileWatershedMicron) / (float) (cal.pixelWidth));

            System.out.println("-------------------------------------");
            System.out.println("Image:" + imp.getTitle());
            long startTimeImage = System.currentTimeMillis();
            System.out.println(System.currentTimeMillis() - startTimeImage);

            //16 bits
            ImagePlus impDup = imp.duplicate();
            impDup.setProcessor(impDup.getProcessor().convertToShortProcessor());

            if (showDebugImages) {
                impDup.setTitle(imp.getTitle() + "_Original");
                impDup.show();
            }

            //GradientRemoval
            ImagePlus impGradientRemoval = impDup.duplicate();
            /*
             GradientRemoval GR = new GradientRemoval();
             impGradientRemoval = GR.exec(impGradientRemoval, 3);
             impGradientRemoval.setTitle(imp.getTitle() + "_impGradientRemoval");
             if (showDebugImages) {
             impGradientRemoval.show();
             }
            //
             //*/

            //Substract background
            ImagePlus impBackground = impGradientRemoval.duplicate();
            impBackground.setTitle(imp.getTitle() + "_BackgroundSubtraction");
            if (backgroundSubtraction) {
                IJ.run(impBackground, "Subtract Background...", "rolling=" + sizeRollingBall);
                System.out.println("Background subtraction: " + (System.currentTimeMillis() - startTimeImage));
            }
            if (showDebugImages) {
                impBackground.show();
            }
            ImagePlus impPreProcessed = impBackground.duplicate();
            impPreProcessed.setTitle(imp.getTitle() + "_Filter");
            if (indexFilter != -2) {
                if (indexFilter == (-1)) {
                    new GaussianBlur().blurGaussian(impPreProcessed.getProcessor(), radiusFilter);
                } else {
                    RankFilters filter = new RankFilters();
                    filter.rank(impPreProcessed.getProcessor(), radiusFilter, indexFilter);
                }
            }
            if (showDebugImages) {
                impPreProcessed.show();
            }

            //Binary image with global Threshold
            GlobalThreshold GT = new GlobalThreshold();
            ImagePlus impGlobal = GT.exec(impPreProcessed, globalThresholdMethod);
            measureFGBG = GT.getMeasurements();
            System.out.println("GT: " + (System.currentTimeMillis() - startTimeImage));

            //Areafilter
            AreaFilterPre AF = new AreaFilterPre();
            ImagePlus impGlobalFiltered = AF.exec(impGlobal, minArea);
            System.out.println("AreaFilter: " + (System.currentTimeMillis() - startTimeImage));

            if (showDebugImages) {
                impGlobalFiltered.setTitle(imp.getTitle() + "_Global");
                impGlobalFiltered.show();
            }

            ArrayList<Roi> roiListCF;
            if (impGlobalFiltered.getProcessor().getStatistics().max == 0) {
                nuclei = null;
                IJ.log("No nuclei detected");
            } else {
                if (twoPass) {
                    //Dilation
                    Dilation Di = new Dilation();
                    ImagePlus impDilated = Di.exec(impGlobalFiltered, dilations);
                    System.out.println("Dilation: " + (System.currentTimeMillis() - startTimeImage));

                    if (showDebugImages) {
                        impDilated.setTitle(imp.getTitle() + "_Dilated");
                        impDilated.show();
                    }

                    //LocalThreshold
                    LocalThreshold LT = new LocalThreshold(localThresholdMethod, impPreProcessed);
                    ArrayList<Roi> roiListLT = LT.exec(impDilated);
                    measureFGBG = LT.getMeasurements();
                    System.out.println("LT: " + (System.currentTimeMillis() - startTimeImage));

                    //CompositeFilter       
                    CompositeFilter CF = new CompositeFilter();
                    roiListCF = CF.exec(impPreProcessed, roiListLT);
                    System.out.println("CF: " + (System.currentTimeMillis() - startTimeImage));

                } else {
                    ThresholdToSelection ThresholdToSelectionObject = new ThresholdToSelection();
                    impGlobalFiltered.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
                    Roi roiGlobalFiltered = ThresholdToSelectionObject.convert(impGlobalFiltered.getProcessor());
                    ShapeRoi roiGlobalFilteredShape = new ShapeRoi(roiGlobalFiltered);
                    Roi[] roiArrayGlobalFiltered = roiGlobalFilteredShape.getRois();
                    ArrayList<Roi> roiListGlobalFiltered = new ArrayList<Roi>(Arrays.asList(roiArrayGlobalFiltered));

                    //CompositeFilter       
                    CompositeFilter CF = new CompositeFilter();
                    roiListCF = CF.exec(impPreProcessed, roiListGlobalFiltered);
                    System.out.println("CF: " + (System.currentTimeMillis() - startTimeImage));
                }

                //IntensityFilter
                IntensityFilter IF = new IntensityFilter();
                ArrayList<Roi> roiListIF = IF.exec(impPreProcessed, roiListCF, measureFGBG);

                if (showDebugImages) {
                    ImagePlus impTest = impDup.duplicate();
                    Overlay overlay = new Overlay();
                    RoiManager rm = new RoiManager(false);
                    for (int i = 0; i < roiListIF.size(); i++) {
                        overlay.add(roiListIF.get(i));
                    }
                    impTest.setTitle(imp.getTitle() + "_Local");
                    impTest.show();
                    impTest.setOverlay(overlay);
                }

                goodInitialSegmentation = checkInitialSegmentation(roiListCF, imp.getHeight(), imp.getWidth());

                if (goodInitialSegmentation == true) {
                    Roi[] roiArrayCR;
                    if (refinement == true) {
                        ContourRefinement CR = new ContourRefinement(rangeEdge);
                        roiArrayCR = CR.exec(roiListIF, impPreProcessed);
                        System.out.println("EdgeEnhancement: " + (System.currentTimeMillis() - startTimeImage));
                    } else {
                        roiArrayCR = new Roi[roiListIF.size()];
                        for (int i = 0; i < roiListIF.size(); i++) {
                            roiArrayCR[i] = roiListIF.get(i);
                        }
                    }

                    if (showDebugImages) {
                        ImagePlus impTest = impDup.duplicate();
                        Overlay overlay = new Overlay();
                        for (int i = 0; i < roiArrayCR.length; i++) {
                            overlay.add(roiArrayCR[i]);
                        }
                        impTest.setTitle(imp.getTitle() + "_CR");
                        impTest.show();
                        impTest.setOverlay(overlay);
                    }

                    ArrayList<Roi> roiListWS;
                    if (watershed > 0) {
                        //Watershed
                        Watershed WS = new Watershed(minArea, rangeEdge, profileWatershed, watershed, refinement, showDebugImages);
                        roiListWS = WS.exec(impPreProcessed, roiArrayCR);
                        System.out.println("WS: " + (System.currentTimeMillis() - startTimeImage));
                    } else {
                        roiListWS = new ArrayList<Roi>(Arrays.asList(roiArrayCR));
                    }

                    if (showDebugImages) {
                        ImagePlus impTest = impDup.duplicate();
                        Overlay overlay = new Overlay();
                        for (int i = 0; i < roiListWS.size(); i++) {
                            overlay.add(roiListWS.get(i));
                        }
                        impTest.setTitle(imp.getTitle() + "_WS");
                        impTest.show();
                        impTest.setOverlay(overlay);
                    }

                    //SplineFit
                    FitSpline FS = new FitSpline();
                    ArrayList<Roi> roiListFS = FS.exec(impPreProcessed, roiListWS);
                    System.out.println("FitSpline: " + (System.currentTimeMillis() - startTimeImage));

                    if (showDebugImages) {
                        ImagePlus impTest = impDup.duplicate();
                        Overlay overlay = new Overlay();
                        for (int i = 0; i < roiListFS.size(); i++) {
                            overlay.add(roiListFS.get(i));
                        }
                        impTest.setTitle(imp.getTitle() + "_Spline");
                        impTest.show();
                        impTest.setOverlay(overlay);
                    }

                    //Remove Roi on edge
                    RemoveRoiEdge RRE = new RemoveRoiEdge();
                    ArrayList<Roi> roiListRRE = RRE.exec(impPreProcessed, roiListFS);
                    System.out.println("RemoveRoiOnEdge: " + (System.currentTimeMillis() - startTimeImage));

                    if (showDebugImages) {
                        ImagePlus impTest = impDup.duplicate();
                        Overlay overlay = new Overlay();
                        for (int i = 0; i < roiListRRE.size(); i++) {
                            overlay.add(roiListRRE.get(i));
                        }
                        impTest.setTitle(imp.getTitle() + "_RemoveNucleiEdge");
                        impTest.show();
                        impTest.setOverlay(overlay);
                    }

                    //Remove Small Roi
                    AreaFilterPost RSR = new AreaFilterPost();
                    ArrayList<Roi> roiListRSR = RSR.exec(impPreProcessed, roiListRRE, minArea, maxArea);
                    System.out.println("RemoveSmallRoi: " + (System.currentTimeMillis() - startTimeImage));

                    if (showDebugImages) {
                        ImagePlus impTest = impDup.duplicate();
                        Overlay overlay = new Overlay();
                        for (int i = 0; i < roiListRSR.size(); i++) {
                            overlay.add(roiListRSR.get(i));
                        }
                        impTest.setTitle(imp.getTitle() + "_AreaFilterPost");
                        impTest.show();
                        impTest.setOverlay(overlay);
                    }

                    //Remove Overlap
                    RemoveOverlap RO = new RemoveOverlap();
                    ArrayList<Roi> roiListRO = RO.exec(impPreProcessed, roiListRSR);
                    System.out.println("RemoveOverlap: " + (System.currentTimeMillis() - startTimeImage));

                    if (showDebugImages) {
                        ImagePlus impTest = impDup.duplicate();
                        Overlay overlay = new Overlay();
                        for (int i = 0; i < roiListRO.size(); i++) {
                            overlay.add(roiListRO.get(i));
                        }
                        impTest.setTitle(imp.getTitle() + "_RemoveOverlap");
                        impTest.show();
                        impTest.setOverlay(overlay);
                    }

                    nuclei = new Nucleus[roiListRO.size()];
                    for (int i = 0; i < roiListRO.size(); i++) {
                        ShapeRoi shapeRoi = new ShapeRoi(roiListRO.get(i));
                        nuclei[i] = new Nucleus(imp.getTitle(), shapeRoi, i);
                    }
                } else {
                    nuclei = new Nucleus[roiListIF.size()];
                    for (int i = 0; i < roiListIF.size(); i++) {
                        ShapeRoi shapeRoi = new ShapeRoi(roiListIF.get(i));
                        nuclei[i] = new Nucleus(imp.getTitle(), shapeRoi, i);
                    }
                }
            }
        }
    }

    protected boolean checkInitialSegmentation(ArrayList<Roi> roiList, int impHeight, int impWidth) {
        boolean goodInitialSegmentation = true;
        if (roiList.size() == 0) {
            System.out.println("Wrong initial segmentation: no nuclei detected - exclude refinement and watershed");
            goodInitialSegmentation = false;
        }
        for (int i = 0; i < roiList.size(); i++) {
            //if (roiList.get(i).getBounds().getHeight() > 0.5 * impHeight || roiList.get(i).getBounds().getWidth() > 0.5 * impWidth) {
            if (roiList.get(i).getBounds().getHeight() > 0.9 * impHeight || roiList.get(i).getBounds().getWidth() > 0.9 * impWidth) {
                IJ.log("Wrong initial segmentation: to large ROI detected - exclude refinement and watershed");
                goodInitialSegmentation = false;
            }
        }
        return goodInitialSegmentation;
    }

    public Nucleus[] getNuclei() {
        return nuclei;
    }
}
