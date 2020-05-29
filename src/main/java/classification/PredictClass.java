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
import ij.plugin.Duplicator;
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

    int nChannels;
    
    boolean error = false;

    public PredictClass(String folder, String inputDirectory, int nChannels) {
        this.inputDirectory = inputDirectory;
        this.folder = folder;
        this.path = folder + filename;
    }

      public String exec(FilteredClassifier fc, ImagePlus impStack, Nucleus nucleus, String[] classLabels) {
        nChannels=impStack.getNChannels();
        Duplicator dup = new Duplicator();
        Roi roi = nucleus.roiNucleus;

        ImagePlus impCH1 = new ImagePlus();
        ImagePlus impCH2 = new ImagePlus();
        ImagePlus impCH3 = new ImagePlus();
        ImagePlus impCH4 = new ImagePlus();
     
        impCH1 = dup.run(impStack, 1, 1, 1, 1, 1, 1);
        if(nChannels>1){
            impCH2 = dup.run(impStack, 2, 2, 1, 1, 1, 1);
        }
        if(nChannels>2){
            impCH3 = dup.run(impStack, 3, 3, 1, 1,  1, 1);
        }
        if(nChannels>3){
            impCH4 = dup.run(impStack, 4, 4, 1, 1, 1, 1);
        }
        
        shapeDescriptors.ShapeDescriptors morpho = new shapeDescriptors.ShapeDescriptors();
        nucleus.morpho = morpho.exec(impCH1, roi, "Morph");
        for(int ch=1; ch<=nChannels;ch++){
            textureDiscriptors.GLCMTexture textural = new textureDiscriptors.GLCMTexture();
            ImagePlus impChannel = new ImagePlus();
            if(ch==1){
                impChannel=impCH1;
            }else if (ch==2){
                impChannel=impCH2;
            }else if (ch==3){ 
                impChannel=impCH3;
            }else if (ch==4){
                impChannel=impCH4;
            }
            nucleus.textural.putAll(textural.exec(impChannel, roi, ("Text_Ch"+Integer.toString(ch))));
        }

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
        return predClass;
    }

    public boolean getErrorPredict() {
        return error;
    }

    public void writeResults(Nucleus nucleus, String[] classLabels) {
        try {
            Path pathFile = FileSystems.getDefault().getPath(path);
            Files.deleteIfExists(pathFile);
        } catch (IOException x) {
            System.err.println(x);
        }
     
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
