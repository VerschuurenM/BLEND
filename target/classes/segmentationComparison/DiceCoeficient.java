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

package segmentationComparison;

import ij.plugin.*;
import ij.ImagePlus;

public class DiceCoeficient {

    double similarityindex;

    public DiceCoeficient() {
    }

    public double getSimilarityIndex() {
        return similarityindex;
    }

    public void exec(ImagePlus impParameters, ImagePlus impGroundRule) {
        ImagePlus impSubtract = new ImageCalculator().run("Subtract create", impParameters, impGroundRule);
        ImagePlus impIntersection = new ImageCalculator().run("Subtract create", impParameters, impSubtract);

        double intersectionSurface = impIntersection.getStatistics().histogram[impIntersection.getStatistics().histogram.length - 1];
        double groundRuleSurface = impGroundRule.getStatistics().histogram[impGroundRule.getStatistics().histogram.length - 1];
        double parametersSurface = impParameters.getStatistics().histogram[impParameters.getStatistics().histogram.length - 1];
        double subtractSurface = impSubtract.getStatistics().histogram[impSubtract.getStatistics().histogram.length - 1];

        double similarityIndex = 2 * intersectionSurface / (groundRuleSurface + parametersSurface);

        this.similarityindex = similarityIndex;

    }

}
