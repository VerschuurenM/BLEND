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
import ij.gui.ShapeRoi;
import ij.measure.*;
import ij.plugin.Duplicator;
import ij.plugin.filter.*;
import ij.process.ImageProcessor;
import ij.gui.*;
import ij.IJ;
import java.awt.Color;
import java.util.ArrayList;
import ij.plugin.ImageCalculator;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import java.util.Arrays;

public class Watershed {
    double minArea;
    double rangeEdge;
    boolean blackBackground;
    boolean contourRefinement;
    double watershedThreshold;
    int profileWatershed;

    public Watershed(double minArea, double rangeEdge, int profileWatershed, boolean blackBackground, double watershedThreshold, boolean contourRefinement) {
        this.minArea = minArea;
        this.rangeEdge = rangeEdge;
        this.profileWatershed = profileWatershed;
        this.blackBackground = blackBackground;
        this.contourRefinement = contourRefinement;
        this.watershedThreshold = watershedThreshold;
    }

    protected ArrayList<Roi> exec(ImagePlus imp, Roi[] roiArray) {
        ArrayList<Roi> roiList = new ArrayList<Roi>();
        for (int i = 0; i < roiArray.length; i++) {
            if (roiArray[i].getBounds().getHeight() > 0 && roiArray[i].getBounds().getWidth() > 0) {
                roiList.add(roiArray[i]);
            }
        }
        
        //DEBUG
        /*/
        RoiManager rm = new RoiManager();
        for (int i = 0; i < roiList.size(); i++) {
            rm.addRoi(roiList.get(i));
        }
        imp.show();
        rm.setVisible(true);
        System.out.println("DEBUG");
        //*/
        
        

        // Make binary image from roiList
        ImagePlus impBinary = IJ.createImage("impBinary", "8-bit black", imp.getWidth(), imp.getHeight(), 1);
        Color white = new Color(255, 255, 255);
        impBinary.getProcessor().setColor(white);
        for (int i = 0; i < roiList.size(); i++) {
            impBinary.getProcessor().fill(roiList.get(i));

        }

        //Duplicate impBinary for performing Watershed
        ImagePlus impWS = new Duplicator().run(impBinary);
        impWS.setTitle("WS");

        //impMask; fill ROIs with different values
        //Create impMask;
        ImagePlus impMask = new Duplicator().run(impBinary);
        impMask.setProcessor(impMask.getProcessor().convertToFloat());
        ImageProcessor ipMask = impMask.getProcessor();
        impMask.setTitle("Mask");
        //Measurements settings for impMask
        Analyzer.setMeasurements(0);
        int measurements = Measurements.MEAN;
        Analyzer.setMeasurements(measurements);
        ResultsTable rt = new ResultsTable();
        Analyzer analyzerImp = new Analyzer(impMask, measurements, rt);
        //Fill ROIs with different sequential values
        //Measurements of ROIs set on ipMask 
        for (int i = 0; i < roiList.size(); i++) {
            ipMask.setRoi(roiList.get(i));
            ipMask.setValue(i + 1);
            ipMask.fill(roiList.get(i));
            impMask.setRoi(roiList.get(i));
            analyzerImp.measure();
            impMask.killRoi();
        }
        //impMask.show();
        
        //Watershed
        if (blackBackground == false) {
            impWS.getProcessor().invert();
        }
        EDM getWatershed = new EDM();
        //Output = BYTE_OVERWRITE
        EDM.setOutputType(0);
        //Prepare for processing: String name + imagePlus impWS
        getWatershed.setup("watershed", impWS);
        getWatershed.run(impWS.getProcessor());
        //impWS.show();
        if (blackBackground == false) {
            impWS.getProcessor().invert();
        }

        //Create Image with WatershedLines
        ImageCalculator ic = new ImageCalculator();
        ImagePlus impWSLines = ic.run("Subtract create", impBinary, impWS);

        //Check if any nuclei are split = check if mean impWSLine > 0;
        Analyzer.setMeasurements(0);
        measurements = Measurements.MEAN;
        Analyzer.setMeasurements(measurements);
        ResultsTable rtTestWSLines = new ResultsTable();
        Analyzer analyzerTestWSLines = new Analyzer(impWSLines, measurements, rtTestWSLines);
        analyzerTestWSLines.measure();
        boolean WSPerformed;
        if (rtTestWSLines.getValueAsDouble(ResultsTable.MEAN, 0) == 0) {
            WSPerformed = false;
        } else {
            WSPerformed = true;
        }

        if (WSPerformed == true) {
            //Get ROIs after Watershed
            ThresholdToSelection TTS = new ThresholdToSelection();
            impWS.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            Roi roiGlobalWS = TTS.convert(impWS.getProcessor());
            ShapeRoi globalShapeWS = new ShapeRoi(roiGlobalWS);
            Roi[] roiArrayWS = globalShapeWS.getRois();

            //Get ROIs WatershedLines
            ThresholdToSelection TTS2 = new ThresholdToSelection();
            impWSLines.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            Roi roiGlobalWSLines = TTS2.convert(impWSLines.getProcessor());
            ShapeRoi globalShapeWSLines = new ShapeRoi(roiGlobalWSLines);
            Roi[] roiArrayWSLines = globalShapeWSLines.getRois();

            impWS.setProcessor(impWS.getProcessor().convertToFloat());
            impWS.getProcessor().setIntArray(ipMask.getIntArray());
            impWSLines.setProcessor(impWSLines.getProcessor().convertToFloat());
            impWSLines.getProcessor().setIntArray(ipMask.getIntArray());

            //Measurements of Watershed-ROIs set on ipMask 
            ResultsTable rtWS = new ResultsTable();
            Analyzer analyzer = new Analyzer(impWS, measurements, rtWS);
            for (int roiNrWS = 0; roiNrWS < roiArrayWS.length; roiNrWS++) {
                impWS.setRoi(roiArrayWS[roiNrWS]);
                analyzer.measure();
                impWS.killRoi();
            }

            //Measurements of WSLines set on ipMask 
            ResultsTable rtWSLines = new ResultsTable();
            Analyzer analyzer2 = new Analyzer(impWSLines, measurements, rtWSLines);
            for (int roiNrWSLines = 0; roiNrWSLines < roiArrayWSLines.length; roiNrWSLines++) {
                impWSLines.setRoi(roiArrayWSLines[roiNrWSLines]);
                analyzer2.measure();
                impWSLines.killRoi();
            }

            //Check Watershed
            ArrayList<Roi> roiListChecked = new ArrayList<Roi>();
            for (int roiNr = 0; roiNr < roiList.size(); roiNr++) {
                //System.out.println("    WS roi:" + roiNr + "/"+roiList.size());
                ArrayList<Roi> splitRoiList = new ArrayList<Roi>();
                for (int roiNrWS = 0; roiNrWS < roiArrayWS.length; roiNrWS++) {
                    if (rt.getValueAsDouble(ResultsTable.MEAN, roiNr) == rtWS.getValueAsDouble(ResultsTable.MEAN, roiNrWS)) {
                        splitRoiList.add(roiArrayWS[roiNrWS]);
                    }
                }
                ArrayList<Roi> wsLinesList = new ArrayList<Roi>();
                for (int nrLine = 0; nrLine < roiArrayWSLines.length; nrLine++) {
                    if (rt.getValueAsDouble(ResultsTable.MEAN, roiNr) == rtWSLines.getValueAsDouble(ResultsTable.MEAN, nrLine)) {
                        wsLinesList.add(roiArrayWSLines[nrLine]);
                    }
                }
                
                //Debug
                /*/
                if(roiNr==55){
                    imp.setRoi(roiList.get(roiNr));
                    imp.show();
                    System.out.println("DEBUG");
                }
                //*/

                ResultsTable rtLocal = new ResultsTable();
                if (splitRoiList.size() > 1) {
                    for (int localRoiNr = 0; localRoiNr < splitRoiList.size(); localRoiNr++) {
                        rtLocal = RTlocal(imp, splitRoiList);
                        //rtLocal.show("rtLocal");
                        boolean merge = false;
                        boolean twoRois = false;
                        if (splitRoiList.size() == 2) {
                            twoRois = true;
                        }

                        //Find nearest ROI + Line
                        double minDistanceRois = imp.getWidth();
                        int indexLocal = 0;
                        int indexNearest = 0;
                        int finalIndexLocal = 0;
                        int finalIndexNearest = 0;

                        Roi localRoi = splitRoiList.get(localRoiNr);
                        Roi nearestRoi = null;
                        int nearestRoiNr = -1;

                        FloatPolygon interpolatedRoiLocal = localRoi.getInterpolatedPolygon(1.0, true);
                        float[] xCoordLocal = interpolatedRoiLocal.xpoints;
                        float[] yCoordLocal = interpolatedRoiLocal.ypoints;

                        double xL = 0;
                        double yL = 0;
                        double xN = 0;
                        double yN = 0;

                        for (int nr = 0; nr < splitRoiList.size(); nr++) {
                            if (nr != localRoiNr) {
                                double distanceRois = imp.getWidth();
                                nearestRoi = splitRoiList.get(nr);
                                FloatPolygon interpolatedRoiNearest = nearestRoi.getInterpolatedPolygon(1.0, true);
                                float[] xCoordNearest = interpolatedRoiNearest.xpoints;
                                float[] yCoordNearest = interpolatedRoiNearest.ypoints;

                                for (int i = 0; i < xCoordLocal.length; i++) {
                                    float xLocal = xCoordLocal[i];
                                    float yLocal = yCoordLocal[i];
                                    for (int j = 0; j < xCoordNearest.length; j++) {
                                        float xNearest = xCoordNearest[j];
                                        float yNearest = yCoordNearest[j];
                                        double distance = Math.sqrt(Math.pow((xLocal - xNearest), 2) + Math.pow((yLocal - yNearest), 2));
                                        if (distance < distanceRois) {
                                            distanceRois = distance;
                                            indexLocal = i;
                                            indexNearest = j;
                                        }
                                    }
                                }
                                if (distanceRois < minDistanceRois) {
                                    minDistanceRois = distanceRois;
                                    finalIndexLocal = indexLocal;
                                    finalIndexNearest = indexNearest;
                                    xL = xCoordLocal[finalIndexLocal];
                                    yL = yCoordLocal[finalIndexLocal];
                                    xN = xCoordNearest[finalIndexNearest];
                                    yN = yCoordNearest[finalIndexNearest];
                                    nearestRoiNr = nr;
                                }
                            }
                        }

                        double minDistanceWS = (double) (imp.getWidth());
                        double XMid = (xL + xN) / 2;
                        double YMid = (yL + yN) / 2;

                        int lineRoiNr = -1;
                        nearestRoi = splitRoiList.get(nearestRoiNr);
                        Roi lineRoi = null;
                        for (int i = 0; i < wsLinesList.size(); i++) {
                            double XLine = wsLinesList.get(i).getBounds().getCenterX();
                            double YLine = wsLinesList.get(i).getBounds().getCenterY();
                            double distance = Math.sqrt(Math.pow((XLine - XMid), 2) + Math.pow((YLine - YMid), 2));
                            if (distance < minDistanceWS) {
                                minDistanceWS = distance;
                                lineRoiNr = i;
                            }
                        }

                        if (lineRoiNr != -1) {
                            lineRoi = wsLinesList.get(lineRoiNr);
                        }

                        double areaLocalRoi = rtLocal.getValueAsDouble(ResultsTable.AREA, localRoiNr);
                        double areaNearestRoi = rtLocal.getValueAsDouble(ResultsTable.AREA, nearestRoiNr);

                        //Merge if area < minArea
                        if (areaLocalRoi < minArea || areaNearestRoi < minArea || lineRoi == null) {
                            merge = true;
                        } //Merge based on intensityprofile perpedicular to watershedline
                        else {
                            double[] profile = profile(imp, lineRoi);
                            merge = analyseProfile(profile);
                        }

                        if (merge == true) {
                            ShapeRoi shape1 = new ShapeRoi(localRoi);
                            ShapeRoi shape2 = new ShapeRoi(nearestRoi);
                            ShapeRoi combinedShape = null;
                            if (lineRoi != null) {
                                ShapeRoi shapeInter = new ShapeRoi(lineRoi);
                                combinedShape = shape1.or(shape2).or(shapeInter);
                            } else {
                                combinedShape = shape1.or(shape2);
                            }

                            if (localRoiNr < nearestRoiNr) {
                                splitRoiList.set(localRoiNr, combinedShape);
                                splitRoiList.remove(nearestRoiNr);
                            } else {
                                splitRoiList.set(nearestRoiNr, combinedShape);
                                splitRoiList.remove(localRoiNr);
                            }

                            localRoiNr--;
                        }
                        if (twoRois == true) {
                            break;
                        }
                    }
                    if (splitRoiList.size() == 1) {
                        //roiList.get(roiNr).setStrokeColor(Color.RED);
                        roiListChecked.add(roiList.get(roiNr));
                    } else {
                        for (int i = 0; i < splitRoiList.size(); i++) {
                            if (contourRefinement == true) {
                                ContourRefinement CR = new ContourRefinement(rangeEdge);
                                Roi roiCR = CR.execRoi(splitRoiList.get(i), imp);
                                roiCR.setStrokeColor(Color.RED);
                                roiListChecked.add(roiCR);
                            } else {
                                roiListChecked.add(splitRoiList.get(i));
                            }
                        }
                    }
                } else {
                    roiListChecked.add(roiList.get(roiNr));
                }
            }
            return roiListChecked;
        } else {
            return roiList;
        }
    }

    protected double[] profile(ImagePlus imp, Roi roiLine) {
        //Make line from the Roi of the watershedline 
        FloatPolygon interpolatedRoi = roiLine.getInterpolatedPolygon(1.0, false);
        float[] xCoord = interpolatedRoi.xpoints;
        float[] yCoord = interpolatedRoi.ypoints;

        float minX = imp.getWidth();
        float maxX = 0;
        int indexMinX = 0;
        int indexMaxX = 0;
        float minY = imp.getWidth();
        float maxY = 0;
        int indexMinY = 0;
        int indexMaxY = 0;

        for (int index = 0; index < xCoord.length; index++) {
            if (xCoord[index] < minX) {
                indexMinX = index;
                minX = xCoord[index];
            }
            if (xCoord[index] > maxX) {
                indexMaxX = index;
                maxX = xCoord[index];
            }
            if (yCoord[index] < minY) {
                indexMinY = index;
                minY = yCoord[index];
            }
            if (yCoord[index] > maxY) {
                indexMaxY = index;
                maxY = yCoord[index];
            }
        }
        float xBegin;
        float yBegin;
        float xEnd;
        float yEnd;

        if (Math.abs(maxX - minX) > Math.abs(maxY - minY)) {
            xBegin = minX;
            yBegin = yCoord[indexMinX];
            xEnd = maxX;
            yEnd = yCoord[indexMaxX];
        } else {
            xBegin = xCoord[indexMinY];
            yBegin = minY;
            xEnd = xCoord[indexMaxY];
            yEnd = maxY;
        }

        Line line = new Line(xBegin, yBegin, xEnd, yEnd);
        //imp.setRoi(line);

        //Settings profile
        float slope = (yEnd - yBegin) / (xEnd - xBegin);
        double length = line.getRawLength();

        double distanceProfiles = 1;
        int numberProfiles = (int) (length / distanceProfiles);
        //double lengthProfile = profileWatershed/2;
        double lengthProfile = 10;
        float slopePerpendicullar = -1 / slope;
        double[] profile = null;
        double[][] profileArray = new double[(int) lengthProfile * 2][numberProfiles];

        //Median Profile
        for (int nr = 0; nr < numberProfiles; nr++) {
            //Line: y−y0=m(x−x0) &  circle (x−x0)^2+(y−y0)^2=r^2 --> (x−x0)^2+m^2(x−x0)^2=r^2 --> (x−x0)^2(1+m^2)=r^2 and so (x−x0)^2=r^2/(1+m^2)
            //x=x0 +or- r/sqrt(1+m^2)
            //or
            //Line: y−y0=m(x−x0) &  circle (x−x0)^2+(y−y0)^2=r^2 --> (x−x0)^2/m^2+(y−y0)^2=r^2 --> (y−y0)^2(1+1/m^2)=r^2 and so (y−y0)^2=r^2/(1+1/m^2)
            //y=y0 +or- r/sqrt(1+1/m^2)
            double midX;
            double midY;

            if (Math.abs(maxX - minX) > Math.abs(maxY - minY)) {
                midX = (double) xBegin + (nr * distanceProfiles) / Math.sqrt(1 + Math.pow((double) slope, 2));
                midY = (double) slope * Math.abs(xBegin - midX) + yBegin;
            } else {
                midY = (double) yBegin + (nr * distanceProfiles) / Math.sqrt(1 + 1 / Math.pow((double) slope, 2));
                midX = Math.abs(yBegin - midY) / (double) slope + xBegin;
            }

            double x1;
            double x2;
            double y1;
            double y2;

            if (slopePerpendicullar != Double.POSITIVE_INFINITY && slopePerpendicullar != Double.NEGATIVE_INFINITY) {
                x1 = (double) midX - lengthProfile / Math.sqrt(1 + Math.pow((double) slopePerpendicullar, 2));
                y1 = (double) slopePerpendicullar * (x1 - midX) + midY;
                x2 = (double) midX + lengthProfile / Math.sqrt(1 + Math.pow((double) slopePerpendicullar, 2));
                y2 = (double) slopePerpendicullar * (x2 - midX) + midY;
            } else {
                x1 = (double) midX - lengthProfile / Math.sqrt(1 + Math.pow((double) slopePerpendicullar, 2));
                y1 = midY - lengthProfile;
                x2 = (double) midX + lengthProfile / Math.sqrt(1 + Math.pow((double) slopePerpendicullar, 2));
                y2 = midY + lengthProfile;
            }

            if (x1 < 0) {
                x1 = 0;
            }
            if (x2 < 0) {
                x1 = 0;
            }
            if (x1 > imp.getWidth()) {
                x1 = imp.getWidth();
            }
            if (x2 > imp.getWidth()) {
                x2 = imp.getWidth();
            }
            if (y1 < 0) {
                y1 = 0;
            }
            if (y2 < 0) {
                y2 = 0;
            }
            if (y1 > imp.getHeight()) {
                y1 = imp.getHeight();
            }
            if (y2 > imp.getHeight()) {
                y2 = imp.getHeight();
            }

            Line linePerpendicullar = new Line(x1, y1, x2, y2);
            imp.setRoi(linePerpendicullar);
            ProfilePlot PP = new ProfilePlot(imp);
            //PP.createWindow();
            profile = PP.getProfile();
            for (int i = 0; i < profileArray.length && i < profile.length; i++) {
                profileArray[i][nr] = profile[i];
            }

            imp.killRoi();
        }
        double[] finalProfile = new double[profileArray.length];
        for (int i = 0; i < profileArray.length && i < profile.length; i++) {
            Arrays.sort(profileArray[i]);
            double median;
            if (profileArray[i].length % 2 == 0) {
                median = ((double) profileArray[i][profileArray[i].length / 2] + (double) profileArray[i][profileArray[i].length / 2 - 1]) / 2;
            } else {
                median = (double) profileArray[i][profileArray[i].length / 2];
            }
            finalProfile[i] = median;
        }
        return finalProfile;
    }

    protected boolean analyseProfile(double[] profile) {
        //Merge if min-intensity < 75% max
        boolean merge = true;

        double min = 10000;
        double max = 0;

        for (int index = 0; index < profile.length; index++) {
            if (profile[index] > max) {
                max = profile[index];
            }
        }
        for (int index = 1; index < profile.length - 1; index++) {
            int sign1 = (int) Math.signum(profile[index] - profile[index - 1] / 1);
            int sign2 = (int) Math.signum(profile[index+1] - profile[index] / 1);
            if (sign1 == -1 || sign1 == 0) {
                if (sign2 == 1 || sign2 == 0) {
                    if (profile[index] < watershedThreshold * max) {
                        merge = false;
                    }

                }
            }
        }
        return merge;
    }

    

    protected ResultsTable RTlocal(ImagePlus imp, ArrayList<Roi> localRoiList) {
        Analyzer.setMeasurements(0);
        int measurements = Measurements.PERIMETER + Measurements.MEAN + Measurements.AREA;
        ResultsTable rt = new ResultsTable();
        Analyzer analyzer = new Analyzer(imp, measurements, rt);
        for (int roiNr = 0; roiNr < localRoiList.size(); roiNr++) {
            imp.setRoi(localRoiList.get(roiNr));
            analyzer.measure();
            imp.killRoi();
        }
        return rt;
    }
}
