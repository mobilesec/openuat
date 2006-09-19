/* Copyright Rene Mayrhofer
 * File created 2006-07-12
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.sensors.AsciiLineReaderBase;
import org.eu.mayrhofer.sensors.Coherence;
import org.eu.mayrhofer.sensors.FFT;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
import org.eu.mayrhofer.sensors.Quantizer;
import org.eu.mayrhofer.sensors.SamplesSink;
import org.eu.mayrhofer.sensors.SegmentsSink;
import org.eu.mayrhofer.sensors.TimeSeries;
import org.eu.mayrhofer.sensors.TimeSeriesAggregator;
import org.eu.mayrhofer.sensors.WiTiltRawReader;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

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

	// just a helper function for comparing
	private static boolean compareQuantizedVectors(int[][] cand1, int[][] cand2, int max_ind) {
		for (int i=0; i<cand1.length; i++) {
			for (int j=0; j<cand2.length; j++) {
				boolean equal = true;
				for (int k=0; k<max_ind && equal; k++) {
					if (cand1[i][k] != cand2[j][k])
						equal = false;
				}
				if (equal) {
					//System.out.println("Match at i=" + i + ", j=" + j);
					return true;
				}
			}
		}
		return false;
	}
	
	private static boolean[] quantizeAndCompare(double[] vector1, double[] vector2, 
			int numQuantLevels, int numCandidates, int max_ind) {
		boolean[] ret = new boolean[2];
		double max1 = Quantizer.max(vector1);
		double max2 = Quantizer.max(vector2);
		int cand1[][] = Quantizer.generateCandidates(vector1, 0, max1, numQuantLevels, false, numCandidates, false);
		int cand2[][] = Quantizer.generateCandidates(vector2, 0, max2, numQuantLevels, false, numCandidates, false);
		ret[0] = compareQuantizedVectors(cand1, cand2, max_ind);
		cand1 = Quantizer.generateCandidates(vector1, 0, max1, numQuantLevels, true, numCandidates, false);
		cand2 = Quantizer.generateCandidates(vector2, 0, max2, numQuantLevels, true, numCandidates, false);
		ret[1] = compareQuantizedVectors(cand1, cand2, max_ind);
		return ret;
	}
	
	public static void mainRunner(String runClassName, String[] args) throws IOException {
		String filename = args[0];
		
		boolean graph = false;
		boolean paramSearch_coherence = false;
		boolean paramSearch_matches = false;
		if (args.length > 1 && args[1].equals("dographs"))
			graph = true;
		if (args.length > 1 && args[1].equals("paramsearch_coherence"))
			paramSearch_coherence = true;
		if (args.length > 1 && args[1].equals("paramsearch_matches"))
			paramSearch_matches = true;
		
		/////// test 1: just plot all time series
		if (graph) {
			AsciiLineReaderBase r = null;
			if (runClassName.equals("ParallelPortPWMReader"))
				r = new ParallelPortPWMReader(filename, 100);
			else if (runClassName.equals("WiTiltRawReader")) {
				r = new WiTiltRawReader();
				((WiTiltRawReader) r).openSerial(filename, false);
			} else {
				System.err.println("Unknown derived class name!");
				System.exit(200);
			}
			
			TimeSeries[] t = new TimeSeries[r.getMaxNumLines()];
			XYSink[] s = new XYSink[r.getMaxNumLines()];
			int[] linesToAdd = new int[r.getMaxNumLines()];
			for (int i=0; i<r.getMaxNumLines(); i++) {
				t[i] = new TimeSeries(50);
				t[i].setOffset(0);
				t[i].setMultiplicator(1/128f);
				t[i].setSubtractTotalMean(true);
				t[i].setActiveVarianceThreshold(350);
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
		
		/////// test 2: plot the 2 extracted segments from the first and the second device
		int[] samplerates;
		double[] windowsizeFactors;
		double varthresholdMin, varthresholdMax, varthresholdStep;
		int[] coherence_windowSizes; 
		int cutOffFrequencyMin = 5;
		int cutOffFrequencyMax = 20;
		int cutOffFrequencyStep = 5;
		if (paramSearch_coherence) {
			samplerates = new int[] {64, 128, 256, 512}; // different sample rates
			windowsizeFactors = new double[] {1 , 1/2f, 1/4f};  // 1 second, 1/2 second or 1/4 second for active detection 
			varthresholdMin = 50;
			varthresholdMax = 1000;
			varthresholdStep = 10;
			coherence_windowSizes = new int[] {32, 64, 128, 256, 512, 1024};
			cutOffFrequencyStep = 1;
			cutOffFrequencyMax = 50;
		} else {
			samplerates = new int[] {128, 256, 512}; // different sample rates
			windowsizeFactors = new double[] {1/2f}; 
			varthresholdMin = 750;
			varthresholdMax = 750;
			varthresholdStep = 50;
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
				samplerate = 128; // Hz
				i1=samplerates.length; // makes the loop exit after this run
			}

			System.out.println("Sampling input data from " + filename + " with " + samplerate + " Hz");
			// can not sample now, because the time series aggregator needs all samples again...

			for (int i2=0; i2<windowsizeFactors.length; i2++) {
				int windowsize = (int) (samplerate*windowsizeFactors[i2]);
				// this is not yet searched, but restrict the minimum significant segment size to 3s
				int minsegmentsize = samplerate*3;
				// these are the defaults when not searching for parameters
				if (!paramSearch_coherence && !paramSearch_matches) {
					windowsize = samplerate/2; // 1/2 second
					minsegmentsize = samplerate*3; // 3 seconds
					i2=windowsizeFactors.length; // makes the loop exit after this run
				}
				
				for (double varthreshold=varthresholdMin; varthreshold<=varthresholdMax; 
						varthreshold+=(paramSearch_coherence ? varthresholdStep : varthresholdMax)) {
					// these are the defaults when not searching for parameters
					if (!paramSearch_coherence) {
						varthreshold = 750;
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

					TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
					TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
					r2.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
					r2.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
					aggr_a.addNextStageSegmentsSink(new SegmentSink(0));
					aggr_b.addNextStageSegmentsSink(new SegmentSink(1));
					aggr_a.setOffset(0);
					aggr_a.setMultiplicator(1/128f);
					aggr_a.setSubtractTotalMean(true);
					aggr_a.setActiveVarianceThreshold(varthreshold);
					aggr_b.setOffset(0);
					aggr_b.setMultiplicator(1/128f);
					aggr_b.setSubtractTotalMean(true);
					aggr_b.setActiveVarianceThreshold(varthreshold);
					r2.simulateSampling();

					if (SegmentSink.segs[0] != null && SegmentSink.segs[1] != null) {
						if (graph) {
							XYSeries seg1 = new XYSeries("Segment 1", false);
							for (int i=0; i<SegmentSink.segs[0].length; i++)
								seg1.add(i, SegmentSink.segs[0][i]);
							XYDataset dat1 = new XYSeriesCollection(seg1);
							JFreeChart chart1 = ChartFactory.createXYLineChart("Aggregated samples", "Number [100Hz]", 
									"Sample", dat1, PlotOrientation.VERTICAL, true, true, false);
							ChartUtilities.saveChartAsJPEG(new File("/tmp/aggrA.jpg"), chart1, 500, 300);

							XYSeries seg2 = new XYSeries("Segment 2", false);
							for (int i=0; i<SegmentSink.segs[1].length; i++)
								seg2.add(i, SegmentSink.segs[1][i]);
							XYDataset dat2 = new XYSeriesCollection(seg2);
							JFreeChart chart2 = ChartFactory.createXYLineChart("Aggregated samples", "Number [100Hz]", 
									"Sample", dat2, PlotOrientation.VERTICAL, true, true, false);
							ChartUtilities.saveChartAsJPEG(new File("/tmp/aggrB.jpg"), chart2, 500, 300);
						}

						/////// test 3: calculate and plot the coherence between the segments from test 2
						// make sure they have similar length
						int len = SegmentSink.segs[0].length <= SegmentSink.segs[1].length ? SegmentSink.segs[0].length : SegmentSink.segs[1].length;
						System.out.println("Using " + len + " samples for coherence computation");
						double[] s1 = new double[len];
						double[] s2 = new double[len];
						System.arraycopy(SegmentSink.segs[0], 0, s1, 0, len);
						System.arraycopy(SegmentSink.segs[1], 0, s2, 0, len);

						for (int i3=0; i3<coherence_windowSizes.length; i3++) {
							int coherence_windowSize = coherence_windowSizes[i3];
							if (!paramSearch_coherence) {
								coherence_windowSize = samplerate;
								i3=coherence_windowSizes.length; // makes the loop exit after this run
							}

							for (int i4=0; i4<windowOverlapFactors.length; i4++) {
								int windowOverlap = (int) (coherence_windowSize*windowOverlapFactors[i4]);
								// these are the defaults when not searching for parameters
								if (!paramSearch_coherence) {
									windowOverlap = 0;
									i4=windowOverlapFactors.length;
								}

								if (s1.length >= 2*coherence_windowSize - windowOverlap) {
									double[] coherence = Coherence.cohere(s1, s2, coherence_windowSize, windowOverlap);
									if (coherence != null) {
										if (graph) {
											XYSeries c = new XYSeries("Coefficients", false);
											for (int i=0; i<coherence.length; i++)
												c.add(i, coherence[i]);
											XYDataset c1 = new XYSeriesCollection(c);
											JFreeChart c2 = ChartFactory.createXYLineChart("Coherence", "", 
													"Sample", c1, PlotOrientation.VERTICAL, true, true, false);
											ChartUtilities.saveChartAsJPEG(new File("/tmp/coherence.jpg"), c2, 500, 300);
										}
				
										for (int cutOffFrequency=cutOffFrequencyMin; cutOffFrequency<=cutOffFrequencyMax; 
										cutOffFrequency+=(paramSearch_coherence ? cutOffFrequencyStep : cutOffFrequencyMax)) {
											// these are the defaults when not searching for parameters
											if (!paramSearch_coherence) {
												cutOffFrequency = 30; // Hz
											}
											// only compare until the cutoff frequency
											int max_ind = (int) (((float) (coherence_windowSize * cutOffFrequency)) / samplerate) + 1;
											//System.out.println("Only comparing the first " + max_ind + " FFT coefficients");
										
											double coherenceMean = Coherence.mean(coherence, max_ind);
											System.out.println("Coherence mean: " + coherenceMean + 
													" samplerate=" + samplerate + ", variance_windowsize=" + windowsize + 
													", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold + 
													", coherence_windowsize=" + coherence_windowSize + ", windowoverlap=" + 
													windowOverlap + ", signal_length=" + len + " (" + ((float) len)/samplerate +
													" seconds), slices=" + Coherence.getNumSlices(len, coherence_windowSize, windowOverlap) +
													", cutofffrequency=" + cutOffFrequency + " (max_ind=" + max_ind + ")");
										}
									}
								}
								else
									System.out.println("Can not compute coherence, not enough slices");
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
									numQuantLevels = 8;
								}
								for (int numCandidates=numCandidatesMin; numCandidates<=numCandidatesMax; 
									numCandidates+=(paramSearch_matches ? numCandidatesStep : numCandidatesMax)) {
									// these are the defaults when not searching for parameters
									if (!paramSearch_matches) {
										numCandidates = 6;
									}

									for (int cutOffFrequency=cutOffFrequencyMin; cutOffFrequency<=cutOffFrequencyMax; 
										cutOffFrequency+=(paramSearch_matches ? cutOffFrequencyStep : cutOffFrequencyMax)) {
										// these are the defaults when not searching for parameters
										if (!paramSearch_matches) {
											cutOffFrequency = 15; // Hz
										}

										// only compare until the cutoff frequency
										int max_ind = (int) (((float) (fftpoints * cutOffFrequency)) / samplerate) + 1;
										//System.out.println("Only comparing the first " + max_ind + " FFT coefficients");
										
										int numMatchesVariantA=0;
										int numMatchesVariantB=0;
										int numMatchesVariantC=0;
										int numMatchesVariantD=0;
										int numWindows=0;

										for (int offset=0; offset<s1.length-fftpoints+1; offset+=fftpoints-windowOverlap) {
											double[] allCoeff1 = FFT.fftPowerSpectrum(s1, offset, fftpoints);
											double[] allCoeff2 = FFT.fftPowerSpectrum(s2, offset, fftpoints);
											
											// for better performance, only use the first max_ind coefficients since the others will not be compared anyway
											double[] fftCoeff1 = new double[max_ind];
											double[] fftCoeff2 = new double[max_ind];
											System.arraycopy(allCoeff1, 0, fftCoeff1, 0, max_ind);
											System.arraycopy(allCoeff2, 0, fftCoeff2, 0, max_ind);
											
											// HACK HACK HACK: set DC components to 0
											fftCoeff1[0] = 0;
											fftCoeff2[0] = 0;
											
											// variants a and c: compare the quantized FFT coefficients directly
											boolean[] matches = quantizeAndCompare(fftCoeff1, fftCoeff2, 
													numQuantLevels, numCandidates, max_ind);
											if (matches[0])
												numMatchesVariantA++;
											if (matches[1])
												numMatchesVariantC++;
											
											// variants b and d: compare the quantized pairwise neighboring sums of the coefficients
											double sums1[] = new double[max_ind];
											double sums2[] = new double[max_ind];
											for (int k=0; k<max_ind; k++) {
												sums1[k] = allCoeff1[k] + allCoeff1[k+1];
												sums2[k] = allCoeff2[k] + allCoeff2[k+1];
												//System.out.println("k=" + k + ": sum1=" + sums1[k] + " sum2=" + sums2[k]);
											}
											matches = quantizeAndCompare(sums1, sums2,
													numQuantLevels, numCandidates, max_ind);
											if (matches[0])
												numMatchesVariantB++;
											if (matches[1])
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
												" (" + max_ind + " FFT coefficients)");
									}
								}
							}
						}
					}
					else 
						System.err.println("Did not get 2 significant active segments");

				}
			}
		}
	}
}
