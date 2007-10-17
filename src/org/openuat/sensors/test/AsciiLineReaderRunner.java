/* Copyright Rene Mayrhofer
 * File created 2006-07-12
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.openuat.authentication.accelerometer.MotionAuthenticationParameters;
import org.openuat.features.Coherence;
import org.openuat.features.QuantizedFFTCoefficients;
import org.openuat.features.TimeSeriesUtil;
import org.openuat.sensors.AsciiLineReaderBase;
import org.openuat.sensors.MainboardAccelerometerReaderFactory;
import org.openuat.sensors.ParallelPortPWMReader;
import org.openuat.sensors.SamplesSink;
import org.openuat.sensors.SegmentsSink;
import org.openuat.sensors.TimeSeries;
import org.openuat.sensors.TimeSeriesAggregator;
import org.openuat.sensors.WiTiltRawReader;

public class AsciiLineReaderRunner {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(AsciiLineReaderRunner.class);

	static class XYSink implements SamplesSink {
		XYSeries series = new XYSeries("Line", false);
		int num=0;
		ArrayList segment = null;
		XYSeries firstActiveSegment = null;
	
		public void addSample(double s, int index) {
			if (index != num++)
				logger.error("Sample index invalid");
			series.add(index, s);
			if (segment != null)
				segment.add(new Double(s));
		}
		public void segmentStart(int index) {
			logger.debug("Receiving segment starting from index " + index);
			
			if (firstActiveSegment == null)
				segment = new ArrayList();
		}
		public void segmentEnd(int index) {
			logger.debug("Receiving segment ending at index " + index);

			if (segment != null) {
				firstActiveSegment = new XYSeries("Segment", false);
				for (int i=0; i<segment.size(); i++)
					firstActiveSegment.add(i, ((Double) segment.get(i)).doubleValue());
			}
		}
	}

	static class SegmentSink implements SegmentsSink {
		static double[][] segs = new double[2][];
		static {
			segs[0] = null;
			segs[1] = null;
		}

		private int index;
		public SegmentSink(int index) {
			this.index = index;
		}
		public void addSegment(double[] segment, int startIndex) {
			logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);

			if (segs[index] == null)
				segs[index] = segment;
			else
				logger.warn("Already received segment " + index + ", this is a second significant one!");
		}
	}

	// a helper function for creating a graph of a time series
	private static void createGraph(double[] series, String seriesName, String xName, String yName, String graphTitle, String outFile) throws IOException {
		XYSeries s = new XYSeries(seriesName, false);
		for (int i=0; i<series.length; i++)
			s.add(i, series[i]);
		XYDataset s1 = new XYSeriesCollection(s);
		JFreeChart s2 = ChartFactory.createXYLineChart(yName, xName, 
				graphTitle, s1, PlotOrientation.VERTICAL, true, true, false);
		ChartUtilities.saveChartAsJPEG(new File(outFile), s2, 500, 300);
	}
	
	private static void plotTimeSeries(String runClassName, String filename) throws IOException {
		AsciiLineReaderBase r = null;
		if (runClassName.equals("ParallelPortPWMReader"))
			r = new ParallelPortPWMReader(filename, 100);
		else if (runClassName.equals("WiTiltRawReader")) {
			r = new WiTiltRawReader();
			((WiTiltRawReader) r).openSerial(filename, false);
		} 
		else if (runClassName.equals("MainboardAccelerometerReader"))
			r = MainboardAccelerometerReaderFactory.createInstance(100);
		else {
			System.err.println("Unknown derived class name!");
			System.exit(200);
		}
		
		TimeSeries[] t = new TimeSeries[r.getMaxNumLines()];
		XYSink[] s = new XYSink[r.getMaxNumLines()];
		int[] linesToAdd = new int[r.getMaxNumLines()];
		for (int i=0; i<r.getMaxNumLines(); i++) {
			t[i] = new TimeSeries(50);
			t[i].setParameters(r.getParameters());
			t[i].setActiveVarianceThreshold(0.02136f);
			s[i] = new XYSink();
			t[i].addNextStageSink(s[i]);
			
			linesToAdd[i] = i;
		}
		r.addSink(linesToAdd, t);
		r.simulateSampling();
	
		for (int i=0; i<r.getMaxNumLines(); i++) {
			XYDataset data = new XYSeriesCollection(s[i].series);
			JFreeChart chart = ChartFactory.createXYLineChart("Line " + i, "Number [100Hz]", 
					"Sample", data, PlotOrientation.VERTICAL, true, true, false);
			ChartUtilities.saveChartAsJPEG(new File("/tmp/line" + i + ".jpg"), chart, 500, 300);

			XYDataset segData = new XYSeriesCollection(s[i].firstActiveSegment);
			JFreeChart segChart = ChartFactory.createXYLineChart("Segment at line " + i, "Number [100Hz]", 
					"Sample", segData, PlotOrientation.VERTICAL, true, true, false);
			ChartUtilities.saveChartAsJPEG(new File("/tmp/seg" + i + ".jpg"), segChart, 500, 300);
		}
	}
	
	private static void computeSimilarityMeasures(String runClassName, String filename, 
			boolean paramSearch_coherence, boolean paramSearch_matches, boolean graph) throws IOException {
		double[] windowsizeFactors;
		double varthresholdMin, varthresholdMax, varthresholdStep;
		int[] coherence_windowSizes; 
		int cutOffFrequencyMin = 5;
		int cutOffFrequencyMax = 20;
		int cutOffFrequencyStep = 5;
		int maxSegmentLength = -1;
		int segmentSkip = -1;
		int[] samplerates;
		if (paramSearch_coherence) {
			samplerates = new int[] {64, 128, 256, 512}; // different sample rates
			windowsizeFactors = new double[] {1 , 1/2f, 1/4f};  // 1 second, 1/2 second or 1/4 second for active detection 
			varthresholdMin = 0.003052f;
			varthresholdMax = 0.061035f;
			varthresholdStep = 0.003052f;
			coherence_windowSizes = new int[] {32, 64, 128, 256, 512, 1024};
			cutOffFrequencyStep = 5;
			cutOffFrequencyMax = 40;
			maxSegmentLength = 3; // this is in seconds while MotionAuthenticationParameters.coherenceSegmentSize; is in samples (and thus not usable here)
			segmentSkip = maxSegmentLength; // seconds
		} else {
			samplerates = new int[] {128, 256, 512}; // different sample rates
			windowsizeFactors = new double[] {1/2f}; 
			varthresholdMin = MotionAuthenticationParameters.activityVarianceThreshold;
			varthresholdMax = MotionAuthenticationParameters.activityVarianceThreshold;
			varthresholdStep = 0.003052f;
			coherence_windowSizes = samplerates;
		}
		int[] quantLevels = new int[] {2, 3, 4, 5, 6, 8, 10, 12, 14, 16, 20};
		int numCandidatesMin = 2;
		int numCandidatesMax = 8;
		int numCandidatesStep = 2;
		double[] windowOverlapFactors = new double[] {0, 1/8f, 1/4f, 1/3f, 1/2f, 2/3f, 3/4f, 7/8f}; // or just 1/2? 
		
		// this is ugly.....
		for (int i1=0; i1<samplerates.length; i1++) {
			int samplerate = samplerates[i1];
			// these are the defaults when not searching for parameters
			if (!paramSearch_coherence && !paramSearch_matches) {
				samplerate = MotionAuthenticationParameters.samplerate; // Hz
				i1=samplerates.length; // makes the loop exit after this run
			}

			System.out.println("Sampling input data from " + filename + " with " + samplerate + " Hz");
			// can not sample now, because the time series aggregator needs all samples again...

			for (int i2=0; i2<windowsizeFactors.length; i2++) {
				int windowsize = (int) (samplerate*windowsizeFactors[i2]);
				// this is not yet searched, but restrict the minimum significant segment size to Xs
				int minsegmentsize = 3*samplerate; //MotionAuthenticationParameters.activityMinimumSegmentSize;
				// these are the defaults when not searching for parameters
				if (!paramSearch_coherence && !paramSearch_matches) {
					windowsize = MotionAuthenticationParameters.activityDetectionWindowSize;
					i2=windowsizeFactors.length; // makes the loop exit after this run
				}
				
				for (double varthreshold=varthresholdMin; varthreshold<=varthresholdMax; 
						varthreshold+=(paramSearch_coherence ? varthresholdStep : varthresholdMax)) {
					// these are the defaults when not searching for parameters
					if (!paramSearch_coherence) {
						varthreshold = MotionAuthenticationParameters.activityVarianceThreshold;
					}
					
					System.out.println("Searching for first significant segments with windowsize=" + windowsize + 
							", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold);
					AsciiLineReaderBase r2 = null;
					if (runClassName.equals("ParallelPortPWMReader"))
						r2 = new ParallelPortPWMReader(filename, samplerate);
					else {
						System.err.println("Unknown derived class name or not supported for WiTilt right now!");
						System.exit(200);
					}

					TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize, -1);
					TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize, -1);
					r2.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
					r2.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
					aggr_a.addNextStageSegmentsSink(new SegmentSink(0));
					aggr_b.addNextStageSegmentsSink(new SegmentSink(1));
					aggr_a.setParameters(r2.getParameters());
					aggr_b.setParameters(r2.getParameters());
					aggr_a.setActiveVarianceThreshold(varthreshold);
					aggr_b.setActiveVarianceThreshold(varthreshold);
					r2.simulateSampling();

					if (SegmentSink.segs[0] != null && SegmentSink.segs[1] != null) {
						if (graph) {
							createGraph(SegmentSink.segs[0], "Segment 1", "Number [100Hz]", "Aggregated samples", "Sample", "/tmp/aggrA.jpg");
							createGraph(SegmentSink.segs[1], "Segment 2", "Number [100Hz]", "Aggregated samples", "Sample", "/tmp/aggrB.jpg");
						}

						/////// test 3: calculate and plot the coherence between the segments from test 2
						// make sure they have similar length
						double[][] splits = TimeSeriesUtil.cutSegmentsToEqualLength(SegmentSink.segs[0], SegmentSink.segs[1]);
						int len = splits[0].length;
						double[][] s1 = TimeSeriesUtil.slice(splits[0], samplerate*maxSegmentLength, samplerate*segmentSkip);
						double[][] s2 = TimeSeriesUtil.slice(splits[1], samplerate*maxSegmentLength, samplerate*segmentSkip);

						for (int i3=0; i3<coherence_windowSizes.length; i3++) {
							int coherence_windowSize = coherence_windowSizes[i3];
							if (!paramSearch_coherence) {
								coherence_windowSize = MotionAuthenticationParameters.coherenceWindowSize;
								i3=coherence_windowSizes.length; // makes the loop exit after this run
							}

							for (int i4=0; i4<windowOverlapFactors.length; i4++) {
								int windowOverlap = (int) (coherence_windowSize*windowOverlapFactors[i4]);
								// these are the defaults when not searching for parameters
								if (!paramSearch_coherence) {
									windowOverlap = (int) (MotionAuthenticationParameters.coherenceWindowOverlapFactor * MotionAuthenticationParameters.coherenceWindowSize);
									i4=windowOverlapFactors.length;
								}

								for (int i5=0; i5<s1.length; i5++) {
									if (s1[i5].length >= 2*coherence_windowSize - windowOverlap) {
										double[] coherence = Coherence.cohere(s1[i5], s2[i5], coherence_windowSize, windowOverlap);
										if (coherence != null) {
											if (graph) {
												createGraph(coherence, "Coefficients", "", "Coherence", "Sample", "/tmp/coherence.jpg");
											}
				
											for (int cutOffFrequency=cutOffFrequencyMin; cutOffFrequency<=cutOffFrequencyMax; 
											cutOffFrequency+=(paramSearch_coherence ? cutOffFrequencyStep : cutOffFrequencyMax)) {
												// these are the defaults when not searching for parameters
												if (!paramSearch_coherence) {
													cutOffFrequency = MotionAuthenticationParameters.coherenceCutOffFrequency;
												}
												int max_ind = TimeSeriesUtil.getMaxInd(coherence_windowSize, samplerate, cutOffFrequency);
										
												double coherenceMean = Coherence.mean(coherence, max_ind);
												System.out.println("Coherence mean: " + coherenceMean + 
														" samplerate=" + samplerate + ", variance_windowsize=" + windowsize + 
														", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold + 
														", coherence_windowsize=" + coherence_windowSize + ", windowoverlap=" + 
														windowOverlap + ", signal_length=" + s1[i5].length + " (" + ((float) s1[i5].length)/samplerate +
														" s), slices=" + Coherence.getNumSlices(len, coherence_windowSize, windowOverlap) +
														", cutofffrequency=" + cutOffFrequency + " (max_ind=" + max_ind + "), segment=" +
														(i5+1) + " out of " + s1.length + " (whole signal with length=" + len +
														" / " + ((float) len)/samplerate + " s, segmentskip=" + segmentSkip + " s, offset=" 
														+ (segmentSkip*samplerate*i5) + ")");
											}
										}
									}
									else
										System.out.println("Can not compute coherence, not enough slices");
								}
							}
						}

						/////// test 4: calculate and compare the quantized FFT power spectra coefficients of the segments from test 2
						for (int i3=0; i3<windowOverlapFactors.length; i3++) {
							int fftpoints = samplerate;
							int windowOverlap = (int) (fftpoints*windowOverlapFactors[i3]);
							// these are the defaults when not searching for parameters
							if (!paramSearch_matches) {
								windowOverlap = fftpoints/2;
								i3=windowOverlapFactors.length;
							}

							for (int i4=0; i4<quantLevels.length; i4++) {
//							for (int numQuantLevels=numQuantLevelsMin; numQuantLevels<=numQuantLevelsMax; 
// //									numQuantLevels=(paramSearch ? numQuantLevels*numQuantLevelsStepMultiplicator : numQuantLevelsMax)) {
//									numQuantLevels+=(paramSearch ? numQuantLevelsStep : numQuantLevelsMax)) {
								int numQuantLevels = quantLevels[i4];
								// these are the defaults when not searching for parameters
								if (!paramSearch_matches) {
									numQuantLevels = MotionAuthenticationParameters.fftMatchesQuantizationLevels;
									i4 = quantLevels.length;
								}
								for (int numCandidates=numCandidatesMin; numCandidates<=numCandidatesMax; 
									numCandidates+=(paramSearch_matches ? numCandidatesStep : numCandidatesMax)) {
									// these are the defaults when not searching for parameters
									if (!paramSearch_matches) {
										numCandidates = MotionAuthenticationParameters.fftMatchesCandidatesPerRound;
									}

									for (int cutOffFrequency=cutOffFrequencyMin; cutOffFrequency<=cutOffFrequencyMax; 
										cutOffFrequency+=(paramSearch_matches ? cutOffFrequencyStep : cutOffFrequencyMax)) {
										// these are the defaults when not searching for parameters
										if (!paramSearch_matches) {
											cutOffFrequency = MotionAuthenticationParameters.fftMatchesCutOffFrequenecy; // Hz
										}

										int numMatchesVariantA=0;
										int numMatchesVariantB=0;
										int numMatchesVariantC=0;
										int numMatchesVariantD=0;
										int numWindows=0;

										for (int offset=0; offset<s1[0].length-fftpoints+1; offset+=fftpoints-windowOverlap) {
											boolean matches[] = QuantizedFFTCoefficients.quantizeAndCompare(s1[0], s2[0], offset, 
													fftpoints, 
													TimeSeriesUtil.getMaxInd(fftpoints, samplerate, cutOffFrequency), 
													numQuantLevels, numCandidates);
											
											if (matches[0])
												numMatchesVariantA++;
											if (matches[1])
												numMatchesVariantC++;
											if (matches[2])
												numMatchesVariantB++;
											if (matches[3])
												numMatchesVariantD++;
											
											numWindows++;
										}
										System.out.println("Match A: " + (float) numMatchesVariantA / numWindows + " (" + numMatchesVariantA + " out of " + numWindows + ")" + 
												" Match B: " + (float) numMatchesVariantB / numWindows + " (" + numMatchesVariantB + " out of " + numWindows + ")" +
												" Match C: " + (float) numMatchesVariantC / numWindows + " (" + numMatchesVariantC + " out of " + numWindows + ")" +
												" Match D: " + (float) numMatchesVariantD / numWindows + " (" + numMatchesVariantD + " out of " + numWindows + ")" +
												" samplerate=" + samplerate + ", windowsize=" + windowsize + 
												", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold +
												", windowoverlap=" + windowOverlap + ", numquantlevels=" + numQuantLevels +
												", numcandidates=" + numCandidates + ", cutofffrequ=" + cutOffFrequency +
												" (" + TimeSeriesUtil.getMaxInd(fftpoints, samplerate, cutOffFrequency) + " FFT coefficients)");
									}
								}
							}
						}
					}
					else 
						System.err.println("Did not get 2 significant active segments");

					r2.dispose();
					System.gc();
				}
			}
		}
	}

	private static void estimateEntropy(String runClassName, String subdir) throws IOException {
		String[] settings = {"sitting", "standing"};
		String[] hands = {"left", "right", "both"};

		HashMap[][][] vectorsPerSubjPerHandPerDev = new HashMap[51][][];
		for (int i=0; i<vectorsPerSubjPerHandPerDev.length; i++) {
			vectorsPerSubjPerHandPerDev[i] = new HashMap[3][];
			for (int j=0; j<vectorsPerSubjPerHandPerDev[i].length; j++) { 
				vectorsPerSubjPerHandPerDev[i][j] = new HashMap[2];
				for (int k=0; k<vectorsPerSubjPerHandPerDev[i][j].length; k++) 
					vectorsPerSubjPerHandPerDev[i][j][k] = new HashMap();
			}
		}
		
		// and starting reading from the logs: for each subject
		for (int i=0; i<vectorsPerSubjPerHandPerDev.length; i++) {
			// for each setting
			for (int j=0; j<settings.length; j++) {
				// for each combination of hands
				for (int k=0; k<hands.length; k++) {
					// and finally for each try
					for (int l=0; l<5; l++) {
						String filename = String.format(subdir + "/%s-%s-subj%03d-try%03d.log.gz", 
								new Object[] {settings[j], hands[k], new Integer(i+1), new Integer(l+1)});
						System.out.println("Reading from file " + filename);
						FileInputStream is = new FileInputStream(filename);
						
						AsciiLineReaderBase r = null;
						if (runClassName.equals("ParallelPortPWMReader"))
							r = new ParallelPortPWMReader(new GZIPInputStream(is), 
									MotionAuthenticationParameters.samplerate);
						else {
							System.err.println("Unknown derived class name or not supported for WiTilt right now!");
							System.exit(200);
						}

						SegmentSink.segs = new double[2][];
						TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, MotionAuthenticationParameters.activityDetectionWindowSize, MotionAuthenticationParameters.activityMinimumSegmentSize, -1);
						TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, MotionAuthenticationParameters.activityDetectionWindowSize, MotionAuthenticationParameters.activityMinimumSegmentSize, -1);
						r.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
						r.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
						aggr_a.addNextStageSegmentsSink(new SegmentSink(0));
						aggr_b.addNextStageSegmentsSink(new SegmentSink(1));
						aggr_a.setParameters(r.getParameters());
						aggr_b.setParameters(r.getParameters());
						aggr_a.setActiveVarianceThreshold(MotionAuthenticationParameters.activityVarianceThreshold);
						aggr_b.setActiveVarianceThreshold(MotionAuthenticationParameters.activityVarianceThreshold);
						r.simulateSampling();
						is.close();

						int fftpoints = MotionAuthenticationParameters.fftMatchesWindowSize; 
						int windowOverlap = MotionAuthenticationParameters.fftMatchesWindowOverlap;
						for (int device=0; device<2; device++) {
							if (SegmentSink.segs[device] != null) {
								for (int offset=0; offset<SegmentSink.segs[device].length-fftpoints+1; offset+=fftpoints-windowOverlap) {
									int[][] cand = QuantizedFFTCoefficients.computeFFTCoefficientsCandidates(
											SegmentSink.segs[device], offset, 
											MotionAuthenticationParameters.fftMatchesWindowSize,
											TimeSeriesUtil.getMaxInd(fftpoints, MotionAuthenticationParameters.samplerate, MotionAuthenticationParameters.fftMatchesCutOffFrequenecy),
											MotionAuthenticationParameters.fftMatchesQuantizationLevels,
											MotionAuthenticationParameters.fftMatchesCandidatesPerRound,
											true, true);
									for (int m=0; m<cand.length; m++) {
										vectorsPerSubjPerHandPerDev[i][k][device].put(
												new Integer(Arrays.hashCode(cand[m])), cand[m]);
										//System.out.println(i + " " + j + " " + k + " " + l + " " + offset + " " + device + " " + m + ": " + Arrays.toString(cand[m]));
									}
								}
							}
							else
								logger.warn("Could not get active segment for subject " +
										i + " " + settings[j] + " " + hands[k] + " try " +
										k + " device " + device);
						}
						System.out.println("Now " + 
								vectorsPerSubjPerHandPerDev[i][k][0].size() + " (dev1) / " +
								vectorsPerSubjPerHandPerDev[i][k][1].size() + 
								" (dev2) different vectors for subj " + i + 
								" hand " + hands[k]);
					}
				}
			}
		}
		
		// compute the aggregates
		HashMap[] vectorsPerSubj = new HashMap[vectorsPerSubjPerHandPerDev.length];
		HashMap[] vectorsPerHand = new HashMap[3];
		HashMap[] vectorsPerDev = new HashMap[2];
		for (int i=0; i<vectorsPerSubj.length; i++)
			vectorsPerSubj[i] = new HashMap();
		for (int i=0; i<vectorsPerHand.length; i++)
			vectorsPerHand[i] = new HashMap();
		for (int i=0; i<vectorsPerDev.length; i++)
			vectorsPerDev[i] = new HashMap();
		HashMap allVectors = new HashMap();
		
		for (int i=0; i<vectorsPerSubjPerHandPerDev.length; i++) {
			for (int j=0; j<vectorsPerSubjPerHandPerDev[i].length; j++) {
				for (int k=0; k<vectorsPerSubjPerHandPerDev[i][j].length; k++) {
					Iterator l = vectorsPerSubjPerHandPerDev[i][j][k].values().iterator();
					while (l.hasNext()) {
						int[] cand = (int[]) l.next();
						vectorsPerSubj[i].put(new Integer(Arrays.hashCode(cand)), cand);
						vectorsPerHand[j].put(new Integer(Arrays.hashCode(cand)), cand);
						vectorsPerDev[k].put(new Integer(Arrays.hashCode(cand)), cand);
						allVectors.put(new Integer(Arrays.hashCode(cand)), cand);
					}
				}
			}
		}
		
		System.out.println("Different feature vectors per subject:");
		for (int i=0; i<vectorsPerSubj.length; i++)
			System.out.println("    Subject " + i + ": " + vectorsPerSubj[i].size());
		System.out.println("Different feature vectors per hand:");
		for (int i=0; i<vectorsPerHand.length; i++)
			System.out.println("    Hand " + hands[i] + ": " + vectorsPerHand[i].size());
		System.out.println("Different feature vectors per device:");
		for (int i=0; i<vectorsPerDev.length; i++)
			System.out.println("    Device " + i + ": " + vectorsPerDev[i].size());
		System.out.println("Different feature vectors overall: " + allVectors.size());
	}
	
	public static void mainRunner(String runClassName, String[] args) throws IOException {
		String filename = args[0];
		
		boolean graph = false;
		boolean paramSearch_coherence = false;
		boolean paramSearch_matches = false;
		boolean estimateEntropy = false;
		if (args.length > 1 && args[1].equals("dographs"))
			graph = true;
		if (args.length > 1 && args[1].equals("paramsearch_coherence"))
			paramSearch_coherence = true;
		if (args.length > 1 && args[1].equals("paramsearch_matches"))
			paramSearch_matches = true;
		if (args.length > 1 && args[1].equals("estimate_entropy"))
			estimateEntropy = true;
		
		/////// test 1: just plot all time series
		if (graph) {
			plotTimeSeries(runClassName, filename);
		}
		
		/////// test 2: plot the 2 extracted segments from the first and the second device		int[] samplerates;
		if (!estimateEntropy)
			computeSimilarityMeasures(runClassName, filename, paramSearch_coherence, paramSearch_matches, graph);
		else
			estimateEntropy(runClassName, filename);
	}
}
