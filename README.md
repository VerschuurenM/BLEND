# BLEND
FIJI plugin for accurate detection of dysmorphic nuclei.

**ABOUT**

Cancer, aging and viral infection, have one major common denominator at the cellular level: nuclear dysmorphy. Next to overt modifications in size and overall shape, subtle protrusions, called blebs, are often indicative of a local weakening of the nucleus and represent a strong predictor of pathology development. Accurate detection of dysmorphic nuclei may therefore be a valuable tool in routine screening and intelligent imaging approaches. However, due to lack of prior knowledge about their size, shape and intensity,
dysmorphic nuclei are often not accurately detected in standard image analysis routines. To enable accurate detection of dysmorphic nuclei, we have implemented an automated image analysis algorithm, called BLEND (BLEbbed Nuclei Detector), which is based on two-tier seed detection and dynamic programming. Morphological features will be extracted from the detected nuclei, as well as textural features in all channels.

The following functions are implemented in the plugin:
* Segmentation: Segmentation of input images with selected parameter settings.
* Supervised Classifications: Iterative learning process for classification of nuclei.
* Validation: Validation by comparing automated segmentations with user defined ground truths.
* Compare roiLists: Compare roiLists with user defined ground truth roiLists.

**INSTALL**

BLEND is implemented as a java plugin in the software platform FIJI. Fiji can be downloaded from www.fiji.sc. BLEND can be downloaded from: https://github.com/VerschuurenM/BLEND. Drag 'n drop the jar file that can be found in the target folder into the FIJI user interface and save. After restarting FIJI, BLEND can be called from Plugins > BLEND.

**CITATION**
Verschuuren M, De Vylder J, Catrysse H, Robijns J, Philips W, De Vos WH. Accurate Detection of Dysmorphic Nuclei Using Dynamic Programming and Supervised Classification. PLoS One. 2017 Jan 26;12(1):e0170688. doi: 10.1371/journal.pone.0170688. Erratum in: PLoS One. 2017 Mar 6;12(3):e0173701. doi: 10.1371/journal.pone.0173701. PMID: 28125723; PMCID: PMC5268651.
