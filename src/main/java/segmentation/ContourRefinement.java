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

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.FloatPolygon;
import java.util.ArrayList;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.ImagePlus;;

public class ContourRefinement {

    protected float maxPathStrenght;
    protected float pathStrenght;
    protected double[][][] correspondanceArray;
    Roi[] roiArray;
    boolean stopIteration = false;
    boolean splitRoi = true;
    int indexLoop1;
    int indexLoop2;
    ArrayList<Roi> roiListBeforeDP;
    int it;
    int iterations=25;
    double rangeEdge;
    
    public ContourRefinement(double rangeEdge) {
        this.rangeEdge= rangeEdge;
    }

    protected Roi[] exec(ArrayList<Roi> roiList, ImagePlus imp) {

    //Iterative search to optimal path describing the edge of each nucleus
        //For loop over all ROIs 
        //Iterative (#=it) search for path with maximal pathStrength
        //Create interpolated ROI: shuffle ROI --> Different starting point 
        //Check loops in ROI
        //LengthLoop1 > 0.10 * totalLength && LengthLoop2 > 0.10 * totalLength --> splitROI
        //If mean splitROI > MEAN - STDEV (foreground measurement local threshold) --> add extra ROI to RoiList
        //Else --> cutLoop
        //Straighten edge: width = rangeEdge
        //Dynamic programming --> Search optimal path in Straightened edge --> BooleanArray
        //CurveLine: reconstruct optimal Path
        //Add finalRoi to roiListFinal
        
        roiListBeforeDP = roiList;
        ArrayList<Roi> roiListFinal = new ArrayList<Roi>();
        int originalNumber = roiListBeforeDP.size();
        for (int i = 0; i < roiListBeforeDP.size(); i++) {
            Roi localRoi = roiListBeforeDP.get(i);
            double interpolationDist = Math.round(rangeEdge / 3);
            maxPathStrenght = 0;
            it = 0;
            boolean multipleRoi = false;
            if (localRoi.getBounds().getWidth() > rangeEdge && localRoi.getBounds().getHeight() > rangeEdge) {
                do {
                    it += 1;
                    splitRoi = false;
                    if (localRoi.getBounds().getWidth() > rangeEdge && localRoi.getBounds().getHeight() > rangeEdge) {
                        //FloatPolygon, an interpolated version of this selection                     
                        FloatPolygon interpolatedRoi = localRoi.getInterpolatedPolygon(interpolationDist, true);
                        FloatPolygon interpolatedRoiShuffle = shuffle(it, interpolatedRoi);
                        PolygonRoi initiationRoi = new PolygonRoi(interpolatedRoiShuffle, Roi.POLYGON);

                        float[][] edgeArray = straightenLine(imp, initiationRoi, (int) rangeEdge);
                        DynamicProgramming DP = new DynamicProgramming();
                        boolean[][] booleanArray = DP.exec(edgeArray, false);
                        
                        pathStrenght = DP.getPathStrenght();
                        localRoi = curveLine(booleanArray);
                        localRoi = new PolygonRoi(eliminateLoop(localRoi.getFloatPolygon(), rangeEdge, true), Roi.POLYGON);

                        //System.out.println("Number iterations= " + it + " pathstrenght= " + pathStrenght);
                        if ((maxPathStrenght <= pathStrenght)) {
                            //System.out.println(it + "' Path better");
                            roiListBeforeDP.set(i, localRoi);
                            maxPathStrenght = pathStrenght;
                        }
                    }
                } while (it < iterations);
            }
            Roi roiDP = roiListBeforeDP.get(i);
            roiListFinal.add(roiDP);
        }
        roiArray = new Roi[roiListFinal.size()];
        roiListFinal.toArray(roiArray);
        return roiArray;
    }

    protected Roi execRoi(Roi roiBefore, ImagePlus imp) {
        //see Exec; 1 ROI
        Roi roiAfter = roiBefore;
        double interpolationDist = Math.round(rangeEdge / 3);
        maxPathStrenght = 0;
        it = 0;

        //25 iterations  
        do {
            it += 1;
            if (roiBefore.getBounds().getWidth() > rangeEdge && roiBefore.getBounds().getHeight() > rangeEdge) {
                //FloatPolygon, an interpolated version of this selection that has points spaced 10/2 pixel apart + smoothed
                FloatPolygon interpolatedRoi = roiBefore.getInterpolatedPolygon(interpolationDist, true);
                FloatPolygon interpolatedRoiShuffle = shuffle(it, interpolatedRoi);
                PolygonRoi initiationRoi = new PolygonRoi(interpolatedRoiShuffle, Roi.POLYGON);

                float[][] edgeArray = straightenLine(imp, initiationRoi, (int) rangeEdge);
                DynamicProgramming DP = new DynamicProgramming();
                boolean[][] booleanArray = DP.exec(edgeArray, false);
                pathStrenght = DP.getPathStrenght();
                roiBefore = curveLine(booleanArray);
                roiBefore = new PolygonRoi(eliminateLoop(roiBefore.getFloatPolygon(), rangeEdge,false), Roi.POLYGON);

                //System.out.println("Number iterations= " + it + " pathstrenght= " + pathStrenght);
                if ((maxPathStrenght <= pathStrenght)) {
                    //System.out.println(it + "' Path better");
                    roiAfter = roiBefore;
                    maxPathStrenght = pathStrenght;
                }
            }
        } while (it < iterations);
        return roiAfter;

    }
    
    protected float[][] straightenLine(ImagePlus imp, Roi tempRoi, int width) {

        if (!(tempRoi instanceof PolygonRoi)) {
            return null;
        }
        PolygonRoi roi = (PolygonRoi) tempRoi;
        if (roi == null) {
            return null;
        }
        if (roi.getState() == Roi.CONSTRUCTING) {
            roi.exitConstructingMode();
        }
        boolean isSpline = roi.isSplineFit();
        int type = roi.getType();
        int n = roi.getNCoordinates();
        double len = roi.getLength();
        if (!(isSpline && Math.abs(1.0 - roi.getLength() / n) < 0.5)) {
            roi.fitSplineForStraightening();
        }
        if (roi.getNCoordinates() < 2) {
            return null;
        }
        FloatPolygon p = roi.getFloatPolygon();
        n = p.npoints;
        ImageProcessor ip = imp.getProcessor();
        ImageProcessor ip2 = new FloatProcessor(n, width);
		//ImageProcessor distances = null;
        //if (IJ.debugMode)  distances = new FloatProcessor(n, 1);
        float[] pixels = (float[]) ip2.getPixels();
        double x1, y1;
        double x2 = p.xpoints[0] - (p.xpoints[1] - p.xpoints[0]);
        double y2 = p.ypoints[0] - (p.ypoints[1] - p.ypoints[0]);
        if (width == 1) {
            ip2.putPixelValue(0, 0, ip.getInterpolatedValue(x2, y2));
        }
        correspondanceArray = new double[n][width][2];
        for (int i = 0; i < n; i++) {
            x1 = x2;
            y1 = y2;
            x2 = p.xpoints[i];
            y2 = p.ypoints[i];
            //if (distances!=null) distances.putPixelValue(i, 0, (float)Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1)));
            if (width == 1) {
                ip2.putPixelValue(i, 0, ip.getInterpolatedValue(x2, y2));
                continue;
            }
            double dx = x2 - x1;
            double dy = y1 - y2;
            double length = (float) Math.sqrt(dx * dx + dy * dy);
            dx /= length;
            dy /= length;
            //IJ.log(i+"  "+x2+"  "+dy+"  "+(dy*width/2f)+"   "+y2+"  "+dx+"   "+(dx*width/2f));
            double x = x2 - dy * width / 2.0;
            double y = y2 - dx * width / 2.0;
            int j = 0;
            int n2 = width;
            do {
                correspondanceArray[i][j][0] = x;
                correspondanceArray[i][j][1] = y;
                ip2.putPixelValue(i, j++, ip.getInterpolatedValue(x, y));;

                //ip.drawDot((int)x, (int)y);
                x += dy;
                y += dx;
            } while (--n2 > 0);
        }

        //imp.updateAndDraw();
        if (!isSpline) {
            if (type == Roi.FREELINE) {
                roi.removeSplineFit();
            } else {
                imp.draw();
            }
        }
        if (imp.getBitDepth() != 24) {
            ip2.setColorModel(ip.getColorModel());
            ip2.resetMinAndMax();
        }

        float[][] ip3 = ip2.getFloatArray();

        return ip3;
    }
    
    protected Roi curveLine(boolean[][] booleanArray) {
    //Recreate ROI based on booleanArray & correspondanceArray    
        
        float[] xPoints = new float[booleanArray.length];
        float[] yPoints = new float[booleanArray.length];

        for (int i = 0; i < booleanArray.length; i++) {
            for (int j = 0; j < booleanArray[0].length; j++) {
                if (booleanArray[i][j] == true) {
                    xPoints[i] = (float) correspondanceArray[i][j][0];
                    yPoints[i] = (float) correspondanceArray[i][j][1];
                    j = booleanArray[0].length;
                }
            }
        }
        PolygonRoi roi = new PolygonRoi(xPoints, yPoints, booleanArray.length, Roi.POLYGON);

        return roi;
    }

    protected FloatPolygon eliminateLoop(FloatPolygon polygonBefore, double rangeEdge, boolean splitAllowed) {
        //Search for loop in ROI; if loop --> function cutloop

        FloatPolygon polygonReturn = polygonBefore;

        float[] xCoord = polygonBefore.xpoints;
        float[] yCoord = polygonBefore.ypoints;

        for (indexLoop1 = 0; indexLoop1 < xCoord.length - 1; indexLoop1++) {
            float x1 = xCoord[indexLoop1];
            float y1 = yCoord[indexLoop1];
            float x2 = xCoord[indexLoop1 + 1];
            float y2 = yCoord[indexLoop1 + 1];
            boolean intersect = false;
            for (indexLoop2 = indexLoop1 + 2; indexLoop2 < xCoord.length - 1; indexLoop2++) {
                float x3 = xCoord[indexLoop2];
                float y3 = yCoord[indexLoop2];
                float x4 = xCoord[indexLoop2 + 1];
                float y4 = yCoord[indexLoop2 + 1];

                //Check if segments are vertical
                if (x1 == x2 && x3 == x4) {
                    //Check if segments are located on the same line
                    if (x1 == x3) {
                        //Check if segments overlap
                        if (Math.min(y1, y2) < Math.max(y3, y4) && Math.min(y3, y4) < Math.max(y1, y2)) {
                            intersect = true;
                            polygonReturn = cutLoop(polygonReturn, rangeEdge, splitAllowed);
                            xCoord = polygonReturn.xpoints;
                            yCoord = polygonReturn.ypoints;
                            indexLoop1 = 0;
                            break;

                        }
                    }
                } else if (x1 == x2 && x3 != x4) {
                    float a2 = (y4 - y3) / (x4 - x3);
                    float b2 = y3 - a2 * x3;
                    float yi = a2 * x1 + b2;
                    if (yi > Math.min(y1, y2) && yi < Math.max(y1, y2)) {
                        if (yi > Math.min(y3, y4) && yi < Math.max(y3, y4)) {
                            intersect = true;
                            polygonReturn = cutLoop(polygonReturn, rangeEdge, splitAllowed);
                            xCoord = polygonReturn.xpoints;
                            yCoord = polygonReturn.ypoints;
                            indexLoop1 = 0;
                            break;
                        }
                    }
                } else if (x1 != x2 && x3 == x4) {
                    float a1 = (y2 - y1) / (x2 - x1);
                    float b1 = y1 - a1 * x1;
                    float yi = a1 * x3 + b1;
                    if (yi > Math.min(y1, y2) && yi < Math.max(y1, y2)) {
                        if (yi > Math.min(y3, y4) && yi < Math.max(y3, y4)) {
                            intersect = true;
                            polygonReturn = cutLoop(polygonReturn, rangeEdge, splitAllowed);
                            xCoord = polygonReturn.xpoints;
                            yCoord = polygonReturn.ypoints;
                            indexLoop1 = 0;
                            break;
                        }
                    }
                } else {
                    float a1 = (y2 - y1) / (x2 - x1);
                    float b1 = y1 - a1 * x1;
                    float a2 = (y4 - y3) / (x4 - x3);
                    float b2 = y3 - a2 * x3;

                    if (a1 == a2 && b1 == b2) {
                        if (Math.min(x1, x2) < Math.max(x3, x4) && Math.min(x3, x4) < Math.max(x1, x2)) {
                            intersect = true;
                            polygonReturn = cutLoop(polygonReturn, rangeEdge, splitAllowed);
                            xCoord = polygonReturn.xpoints;
                            yCoord = polygonReturn.ypoints;
                            indexLoop1 = 0;
                            break;
                        }
                    } else {
                        float xi = -(b1 - b2) / (a1 - a2);

                        if (xi > Math.min(x1, x2) && xi < Math.max(x1, x2)) {
                            if (xi > Math.min(x3, x4) && xi < Math.max(x3, x4)) {
                                intersect = true;
                                polygonReturn = cutLoop(polygonReturn, rangeEdge, splitAllowed);
                                xCoord = polygonReturn.xpoints;
                                yCoord = polygonReturn.ypoints;
                                indexLoop1 = 0;
                                break;
                            }
                        }

                    }
                }
            }
        }
        return polygonReturn;
    }

    protected FloatPolygon cutLoop(FloatPolygon polygonRoi, double rangeEdge, boolean splitAllowed) {
        //Split ROI if Loop > 10% length ROI
        float[] xCoord = polygonRoi.xpoints;
        float[] yCoord = polygonRoi.ypoints;
        FloatPolygon polygonReturn;

        ArrayList<Float> xCoordList = new ArrayList<Float>();
        for (int i = 0; i < xCoord.length; i++) {
            xCoordList.add(xCoord[i]);
        }
        ArrayList<Float> yCoordList = new ArrayList<Float>();
        for (int i = 0; i < yCoord.length; i++) {
            yCoordList.add(yCoord[i]);
        }

        int lengthLoop1 = indexLoop2 - indexLoop1;
        int lengthLoop2 = (xCoord.length - 1 - indexLoop2) + (indexLoop1);

        if (lengthLoop1 > 0.30 * xCoord.length && lengthLoop2 > 0.30 * xCoord.length && splitAllowed==true) {
            float[] xCoord1 = new float[(indexLoop1 + 1) + (xCoord.length - indexLoop2)];
            float[] yCoord1 = new float[xCoord1.length];
            float[] xCoord2 = new float[indexLoop2 - indexLoop1];
            float[] yCoord2 = new float[xCoord2.length];

            int count1 = 0;
            int count2 = 0;
            for (int i = 0; i <= indexLoop1; i++) {
                xCoord1[count1] = xCoord[i];
                yCoord1[count1] = yCoord[i];
                count1++;
            }
            for (int i = indexLoop2; i < xCoord.length; i++) {
                xCoord1[count1] = xCoord[i];
                yCoord1[count1] = yCoord[i];
                count1++;
            }
            for (int i = indexLoop1 + 1; i <= indexLoop2; i++) {
                xCoord2[count2] = xCoord[i];
                yCoord2[count2] = yCoord[i];
                count2++;
            }
            if (xCoord1.length > rangeEdge && xCoord2.length > rangeEdge) {
                polygonReturn = new FloatPolygon(xCoord1, yCoord1);
                roiListBeforeDP.add(new PolygonRoi(xCoord2, yCoord2, Roi.POLYGON));
                maxPathStrenght = 0;
                it = 0;
            } else {
                if (xCoord1.length > xCoord2.length) {
                    polygonReturn = new FloatPolygon(xCoord1, yCoord1);
                } else {
                    polygonReturn = new FloatPolygon(xCoord2, yCoord2);
                }

            }
        } else if (lengthLoop1 < lengthLoop2) {
            int count = 0;
            while (count < (indexLoop2 - indexLoop1)) {
                xCoordList.remove(indexLoop1 + 1);
                yCoordList.remove(indexLoop1 + 1);
                count++;
            }
            float[] xCoordReturn = new float[xCoordList.size()];
            for (int i = 0; i < xCoordList.size(); i++) {
                xCoordReturn[i] = xCoordList.get(i);
            }
            float[] yCoordReturn = new float[yCoordList.size()];
            for (int i = 0; i < yCoordList.size(); i++) {
                yCoordReturn[i] = yCoordList.get(i);
            }
            polygonReturn = new FloatPolygon(xCoordReturn, yCoordReturn);
        } else {
            for (int i = indexLoop2; i < xCoord.length; i++) {
                xCoordList.remove(indexLoop2);
                yCoordList.remove(indexLoop2);
            }
            for (int i = 0; i <= indexLoop1; i++) {
                xCoordList.remove(0);
                yCoordList.remove(0);
            }
            float[] xCoordReturn = new float[xCoordList.size()];
            for (int i = 0; i < xCoordList.size(); i++) {
                xCoordReturn[i] = xCoordList.get(i);
            }
            float[] yCoordReturn = new float[yCoordList.size()];
            for (int i = 0; i < yCoordList.size(); i++) {
                yCoordReturn[i] = yCoordList.get(i);
            }
            polygonReturn = new FloatPolygon(xCoordReturn, yCoordReturn);
        }

        return polygonReturn;
    }

    protected FloatPolygon shuffle(int it, FloatPolygon polygonBefore) {
        float[] xCoord = polygonBefore.xpoints;
        float[] yCoord = polygonBefore.ypoints;
        ArrayList<Float> xCoordList = new ArrayList<Float>();
        ArrayList<Float> yCoordList = new ArrayList<Float>();
        for (int index = 0; index < xCoord.length; index++) {
            if (xCoord[index] > 0 || yCoord[index] > 0) {
                xCoordList.add(xCoord[index]);
                yCoordList.add(yCoord[index]);
            }
        }
        int start = 0;
        if (it % 5 == 0) {
            start = 0;
        } else if (it % 5 == 1) {
            start = xCoordList.size() / 5;
        } else if (it % 5 == 2) {
            start = xCoordList.size() / 5 * 2;
        } else if (it % 5 == 3) {
            start = xCoordList.size() / 5 * 3;
        } else if (it % 5 == 4) {
            start = xCoordList.size() / 5 * 4;
        }
        //*/
        float[] xCoordSwap = new float[xCoordList.size()];
        float[] yCoordSwap = new float[yCoordList.size()];

        for (int i = 0; i < xCoordList.size(); i++) {
            if ((start + i) < xCoordList.size()) {
                xCoordSwap[i] = xCoordList.get(start + i);
                yCoordSwap[i] = yCoordList.get(start + i);
            } else {
                xCoordSwap[i] = xCoordList.get(start + i - (xCoordList.size() - 1));
                yCoordSwap[i] = yCoordList.get(start + i - (yCoordList.size() - 1));
            }

        }
        FloatPolygon polygonReturn = new FloatPolygon(xCoordSwap, yCoordSwap);
        return polygonReturn;
    }

    protected void createImage(float[][] edgeArray, String title) {
        ImageProcessor ip = new FloatProcessor(edgeArray.length, edgeArray[0].length);
        ImagePlus imp = new ImagePlus(title, ip);
        imp.getProcessor().setFloatArray(edgeArray);
        imp.show();
    }
}
