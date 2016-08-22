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

import Nucleus.Nucleus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class AssignClass {

    ArrayList<String> textList;
    String inputDirectory;
    String path;
    int curvature;
    int fourierDescriptor;
    boolean saveCroppedNuclei;
    String folder;
    String filename = "ResultsClassification.csv";

    public AssignClass(String folder, boolean saveCroppedNuclei, String inputDirectory) {
        this.inputDirectory = inputDirectory;
        this.saveCroppedNuclei = saveCroppedNuclei;
        this.folder = folder;
        this.path = folder + filename;
    }

    public ArrayList<String> exec(String[] imageArray, Nucleus[] nuclei, int imageIndex, int roiIndex, ArrayList<String> text, String label) {
        textList = text;

        ImagePlus imp = IJ.openImage(inputDirectory + imageArray[imageIndex]);
        Nucleus nucleus = nuclei[roiIndex];
        Roi roi = nucleus.roiNucleus;
        String imageTitle = imp.getTitle();
        
        nucleus.labelClass=label;

        shapeDescriptors.ShapeDescriptors morpho = new shapeDescriptors.ShapeDescriptors();
        nucleus.morpho = morpho.exec(imp, roi, "Morph");
        textureDiscriptors.GLCMTexture textural = new textureDiscriptors.GLCMTexture();
        nucleus.textural = textural.exec(imp, roi, "Text");

        String csvFile = path;
        File f = new File(csvFile);
        String COMMA_DELIMITER = ",";
        String NEW_LINE_SEPARATOR = "\n";
        String FILE_HEADER = "";

        if (textList.size() == 0) {
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
                FILE_HEADER=FILE_HEADER.concat("Class");
                
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
                textList.add(FILE_HEADER + "\n");
        }

        FileWriter fileWriter = null;
        //WriteResults
        try {
            fileWriter = new FileWriter(csvFile, true);
        } catch (Exception e) {
            System.out.println("Error in CsvFileWriter !!!");
            e.printStackTrace();
        }
        String resultLine = "";
        Set keys = nucleus.index.entrySet();
        Iterator i = keys.iterator();
        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            resultLine = resultLine.concat(me.getValue().toString());
            resultLine = resultLine.concat(COMMA_DELIMITER);
        }
        keys = nucleus.morpho.entrySet();
        i = keys.iterator();
        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            resultLine = resultLine.concat((String) me.getValue().toString());
            resultLine = resultLine.concat(COMMA_DELIMITER);
        }
        keys = nucleus.textural.entrySet();
        i = keys.iterator();
        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            resultLine = resultLine.concat((String) me.getValue().toString());
            resultLine = resultLine.concat(COMMA_DELIMITER);
        }
        resultLine = resultLine.concat(String.valueOf(nucleus.labelClass));
        try {
            fileWriter.append(resultLine);
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
        textList.add(resultLine + "\n");

        //Create and Save cropped image
        if (saveCroppedNuclei == true) {
            String newFolder = folder + "CroppedImages/";
            (new File(newFolder)).mkdirs();
            IJ.selectWindow("Crop");
            String impTitleCrop = imageTitle + ";" + roiIndex + ";" + label;
            IJ.saveAs("tiff", newFolder + impTitleCrop);
        }
        try {
            FileWriter fstreamAdd = new FileWriter(path, false);
            BufferedWriter outAdd = new BufferedWriter(fstreamAdd);
            for (int j = 0; j < textList.size(); j++) {
                outAdd.write(textList.get(j));
            }
            outAdd.close();

        } catch (IOException e1) {
            stop();
        }
        return textList;
    }

    public void stop() {
        System.exit(0);
    }

}
