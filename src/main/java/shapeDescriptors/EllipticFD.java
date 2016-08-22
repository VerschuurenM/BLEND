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

import ij.gui.*;
import java.awt.*;
import java.util.LinkedHashMap;

public class EllipticFD{
	
	int nFD;
        String prefix;
	
	public EllipticFD(int fourierDescriptor, String prefix){
		this.nFD = fourierDescriptor;
                this.prefix = prefix;
	}
	
	public  LinkedHashMap<String,Double> exec(Roi roiInput){
		
                LinkedHashMap<String,Double> ellipticFD = new  LinkedHashMap<String,Double>();
		PolygonRoi roi;
		try{
			roi = (PolygonRoi) roiInput;
		}
		catch(Exception e){
			Polygon ala = roiInput.getPolygon();
			roi = new PolygonRoi(ala, Roi.POLYGON);
		}
		Rectangle rect = roi.getBounds();
		  
		int n = roi.getNCoordinates();
		double[] x = new double[n];
		double[] y = new double[n];
		int[] xp = roi.getXCoordinates();
		int[] yp = roi.getYCoordinates();
	  
		for (int j = 0; j < n; j++){
			x[j] = (double) (rect.x + xp[j]);
			y[j] = (double) (rect.y + yp[j]); 
		}
	  
		ellipticFD = computeEllipticFD(x, y, nFD);
		return ellipticFD;
	}

	
	private  LinkedHashMap<String,Double> computeEllipticFD(double[] x, double[] y, int nFD){
		   
            double[] ax = new double[nFD];	// Fourier Descriptor
	    double[] ay = new double[nFD];	// Fourier Descriptor
	    double[] bx = new double[nFD];	// Fourier Descriptor
	    double[] by = new double[nFD];	// Fourier Descriptor
	     
	    double t = 2.0*Math.PI/x.length;
	    double p = 0.0;
	    double M = 2.0/x.length;;

	    for (int k = 0; k < nFD; k++){

	    	for (int i = 0; i < x.length; i++){
	    		p = k*t*i;
	    		ax[k] +=  x[i]*Math.cos(p);
	    		bx[k] +=  x[i]*Math.sin(p);
	    		ay[k] +=  y[i]*Math.cos(p);
	    		by[k] +=  y[i]*Math.sin(p);
	    	}
	      

	      ax[k] *= M;
	      bx[k] *= M;
	      ay[k] *= M;
	      by[k] *= M;
	      
	    }
	    
	    LinkedHashMap<String,Double> efd = new  LinkedHashMap<String,Double>();
            double efdSum=0;
	    double denomA = (ax[1]*ax[1]) + (ay[1]*ay[1]);
	    double denomB = (bx[1]*bx[1]) + (by[1]*by[1]);
	    for (int k = 2; k < nFD; k++){
                    efd.put(prefix+"_Efd_"+(k+1), Math.sqrt((ax[k]*ax[k] + ay[k]*ay[k])/denomA) + Math.sqrt((bx[k]*bx[k] + by[k]*by[k])/denomB));
                    efdSum=efdSum + Math.sqrt((ax[k]*ax[k] + ay[k]*ay[k])/denomA) + Math.sqrt((bx[k]*bx[k] + by[k]*by[k])/denomB);
                }
	    efd.put(prefix+"_Efd_Sum", efdSum);
            return efd;        
	}
 

}