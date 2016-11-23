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
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import shapeDescriptors.ShapeDescriptors;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.classifiers.meta.FilteredClassifier;

public class PredictClass {

    String inputDirectory;
    String folder;
    String filename = "Temp.csv";
    String path;

    boolean error = false;

    public PredictClass(String folder, String inputDirectory) {
        this.inputDirectory = inputDirectory;
        this.folder = folder;
        this.path = folder + filename;
    }

    //public String exec(FilteredClassifier fc, String[] imageArray, int imageIndex, Nucleus nucleus, String[] classLabels) {
      public String exec(FilteredClassifier fc, ImagePlus imp, Nucleus nucleus, String[] classLabels) {

        Roi roi = nucleus.roiNucleus;
        //imp.show();
        //ImagePlus imp = IJ.openImage(inputDirectory + imageArray[imageIndex]);

        shapeDescriptors.ShapeDescriptors morpho = new shapeDescriptors.ShapeDescriptors();
        nucleus.morpho = morpho.exec(imp, roi, "Morph");
        textureDiscriptors.GLCMTexture textural = new textureDiscriptors.GLCMTexture();
        nucleus.textural = textural.exec(imp, roi, "Text");

        writeResults(nucleus, classLabels);

        Instances dataTemp = null;
        File file = new File(path);
        CSVLoader loader = new CSVLoader();
        try {
            String[] options = weka.core.Utils.splitOptions(" -N first,last");
            loader.setOptions(options);
        } catch (Exception e) {

        }
        try {
            loader.setSource(file);
            dataTemp = loader.getDataSet();
            dataTemp.setClassIndex(dataTemp.numAttributes() - 1);
            dataTemp.getClass();
        } catch (IOException e) {
            System.out.println(e.toString());
            error = true;
            return null;
        }
        String predClass = "";
        System.out.println(dataTemp.instance(0));
        try {
            double pred = fc.classifyInstance(dataTemp.instance(0));
            predClass = dataTemp.classAttribute().value((int) pred);
            //test
            for (int i = 0; i < dataTemp.numInstances(); i++) {
                pred = fc.classifyInstance(dataTemp.instance(i));
                System.out.println(pred);
                System.out.print("ID: " + dataTemp.instance(i).value(0));
                System.out.print(", actual: " + dataTemp.classAttribute().value((int) dataTemp.instance(i).classValue()));
                System.out.println(", predicted: " + dataTemp.classAttribute().value((int) pred));
            }

        } catch (Exception e) {
            System.out.println(e.toString());
            error = true;
            return null;
        }
        try {
            Path pathFile = FileSystems.getDefault().getPath(path);
            Files.deleteIfExists(pathFile);
        } catch (IOException x) {
            System.err.println(x);
            return null;
        }
        return predClass;
    }

    public boolean getErrorPredict() {
        return error;
    }

    public void writeResults(Nucleus nucleus, String[] classLabels) {
        String csvFile = path;
        File f = new File(csvFile);
        String COMMA_DELIMITER = ",";
        String NEW_LINE_SEPARATOR = "\n";
        String FILE_HEADER = "";

        //Create Header
        Set keys = nucleus.index.entrySet();
        Iterator i = keys.iterator();
        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            FILE_HEADER = FILE_HEADER.concat((String) me.getKey());
            FILE_HEADER = FILE_HEADER.concat(COMMA_DELIMITER);
        }
        keys = nucleus.morpho.entrySet();
        i = keys.iterator();
        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            FILE_HEADER = FILE_HEADER.concat((String) me.getKey());
            FILE_HEADER = FILE_HEADER.concat(COMMA_DELIMITER);
        }
        keys = nucleus.textural.entrySet();
        i = keys.iterator();
        while (i.hasNext()) {
            Map.Entry me = (Map.Entry) i.next();
            FILE_HEADER = FILE_HEADER.concat((String) me.getKey());
            FILE_HEADER = FILE_HEADER.concat(COMMA_DELIMITER);
        }
        FILE_HEADER = FILE_HEADER.concat("Class");
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

        //WriteResults
        for (int indexLabel = 0; indexLabel < classLabels.length; indexLabel++) {
            String classLabel = classLabels[indexLabel];
            if (!classLabel.equals("New Class")) {
                try {
                    fileWriter = new FileWriter(csvFile, true);
                } catch (Exception e) {
                    System.out.println("Error in CsvFileWriter !!!");
                    e.printStackTrace();
                }
                String resultLine = "";
                keys = nucleus.index.entrySet();
                i = keys.iterator();
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
                resultLine = resultLine.concat(classLabel);
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
            }
        }
    }
}
