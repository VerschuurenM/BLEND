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

package shapeDescriptors;

import java.awt.Polygon;
import java.util.ArrayList;
import ij.ImagePlus;
import ij.gui.Roi;

public class Curvature {
	double curvatureDescriptor;
	
	public double exec(Roi roi, int range){
                // Get the coordinates from a roi and use linear interpolation to fill the gaps
		int[][] contourArray = GetROICoordinates(roi);
                // [0] bending energy/mean square curvature of outline
		curvatureDescriptor = Curvatures(contourArray, range);	
                return curvatureDescriptor;
        }
	private double Curvatures(int[][] contourArray, int range) {
		int contourArrayLength = contourArray[1].length;
		double[] curvature = new double[contourArrayLength];
		double curvatureSum = 0;

		for( int i = 0 ; i < contourArrayLength ; i++){

			double dx1 = 0;
			double dy1 = 0;
			double dx2 = 0;
			double dy2 = 0;

			if( i >= range && i < contourArrayLength - range ){
				
				dx1 = contourArray[0][i-range]-contourArray[0][i];
				dy1 = contourArray[1][i]-contourArray[1][i-range];
				
				dx2 = contourArray[0][i+range]-contourArray[0][i];
				dy2 = contourArray[1][i]-contourArray[1][i+range];
				
			}
			else if( i < range){
				
				dx1 = contourArray[0][contourArrayLength+i-range]-contourArray[0][i];
				dy1 = contourArray[1][i]-contourArray[1][contourArrayLength+i-range];

				dx2 = contourArray[0][i+range]-contourArray[0][i];
				dy2 = contourArray[1][i]-contourArray[1][i+range];
			}	
			else if( i >= contourArrayLength - range){

				dx1 = contourArray[0][i-range]-contourArray[0][i];
				dy1 = contourArray[1][i]-contourArray[1][i-range];

				dx2 = contourArray[0][i+range-contourArrayLength]-contourArray[0][i];
				dy2 = contourArray[1][i]-contourArray[1][i+range-contourArrayLength];
			}	

			double angle1 = Math.atan2(dy1,dx1);
			double angle2 = Math.atan2(dy2,dx2);
			
			curvature[i]=Math.abs(angle2-angle1);
			
			if(curvature[i] == curvature[i]){
				curvatureSum = curvatureSum + curvature[i];;
			}
			

		}	
		
		double curvatureDescriptor = curvatureSum/contourArrayLength;
		return curvatureDescriptor;
		
	}
	private int[][] GetROICoordinates(Roi roi) {
		
		Polygon p = roi.getPolygon(); 
		
		ArrayList<Integer> ROICoordinatesX = new ArrayList<Integer>();
		ArrayList<Integer> ROICoordinatesY = new ArrayList<Integer>();
		
		for( int i = 0 ; i < p.npoints-1 ; i++){
			
                        // Small step between coordinates --> Add Coordinate
			if (Math.abs(p.xpoints[i] - p.xpoints[i+1]) < 2 && Math.abs(p.ypoints[i] - p.ypoints[i+1]) < 2){

				ROICoordinatesX.add(p.xpoints[i]);
				ROICoordinatesY.add(p.ypoints[i]);
			
			}
                        
                        // Big step between coordinates --> Add Coordinate  + Interpolate      
			else if (Math.abs(p.xpoints[i] - p.xpoints[i+1]) >= 2 || Math.abs(p.ypoints[i] - p.ypoints[i+1]) >= 2){
			
				ROICoordinatesX.add(p.xpoints[i]);
				ROICoordinatesY.add(p.ypoints[i]);
				
				int pointAddsX = -(p.xpoints[i] - p.xpoints[i+1]);
				int pointAddsY = -(p.ypoints[i] - p.ypoints[i+1]);
                                
                                // dx >= dy --> 
				if (Math.abs(p.xpoints[i] - p.xpoints[i+1]) >= Math.abs(p.ypoints[i] - p.ypoints[i+1])){

					for( int j = 1 ; j < Math.abs(pointAddsX) ; j++){
						ROICoordinatesX.add(p.xpoints[i] + j * (pointAddsX/Math.abs(pointAddsX)));
						ROICoordinatesY.add(p.ypoints[i] + Math.round(j * (float)pointAddsY/pointAddsX));
					}
				}
                                //dy > dx -->
				else{
					for( int j = 1 ; j < Math.abs(pointAddsY) ; j++){
						ROICoordinatesY.add(p.ypoints[i] + j * (pointAddsY/Math.abs(pointAddsY)));
						ROICoordinatesX.add(p.xpoints[i] + Math.round(j * (float)pointAddsX/pointAddsY));
					}
				}
				
			}
		}
		
		int[][] contourArray = new int[2][ROICoordinatesX.size()];
		for ( int i = 0 ; i < ROICoordinatesX.size() ; i++){
			contourArray[0][i] = ROICoordinatesX.get(i);
			contourArray[1][i] = ROICoordinatesY.get(i);
			
		}
		return contourArray;
	}
}


	