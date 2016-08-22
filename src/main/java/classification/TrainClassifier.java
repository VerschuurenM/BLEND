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

import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.*;
import weka.filters.unsupervised.attribute.Remove;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import ij.IJ;
import java.util.Random;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.FilteredClassifier;

public class TrainClassifier {

    String pathFeatures;
    FilteredClassifier fc;

    public TrainClassifier(FilteredClassifier fc, String pathFeatures) {
        this.pathFeatures = pathFeatures;
        this.fc=fc;
    }

    public FilteredClassifier exec() {
        IJ.log("-----------------------------------------------");      
        Instances data = null;
        File file = new File(pathFeatures);
        CSVLoader loader = new CSVLoader();
        try {
            String[] options = weka.core.Utils.splitOptions(" -N first,last");
            loader.setOptions(options);
        } catch (Exception e) {

        }
        try {
            loader.setSource(file);
            data = loader.getDataSet();
            data.setClassIndex(data.numAttributes() - 1);
            data.getClass();
            System.out.println("\nDataset:\n");
            System.out.println(data);
        } catch (IOException e) {
            IJ.showMessage("IOException");
        } 
        try {
            fc.buildClassifier(data);
            IJ.log("\n"+fc.toString());
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        try {
           Evaluation eval = new Evaluation(data);
           eval.crossValidateModel(fc, data, 10, new Random());
           eval.toString();
           //eval.evaluateModel(fc, data, options);
           IJ.log(eval.toSummaryString("\nResults\n*********", false));
           IJ.log(eval.toClassDetailsString("\nDetail\n"));
           IJ.log(eval.toMatrixString("\nConfusionMatrix\n"));
           IJ.log("\n");
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        IJ.log("-----------------------------------------------");
        return fc;
    }
}
