/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.accelerometer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.DHOverTCPWithVerification;
import org.eu.mayrhofer.authentication.InterlockProtocol;
import org.eu.mayrhofer.sensors.Coherence;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
import org.eu.mayrhofer.sensors.SegmentsSink;
import org.eu.mayrhofer.sensors.TimeSeriesAggregator;

/** This is the first variant of the motion authentication protocol.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class MotionAuthenticationProtocol1 extends DHOverTCPWithVerification implements SegmentsSink {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(MotionAuthenticationProtocol1.class);

	public static final int TcpPort = 54322;

	private double[] localSegment = null;
	private Object localSegmentLock = new Object();
	
	private double[] remoteSegment = null;
	
	private double coherenceThreshold = 0.25;
	
	private Socket socketToRemote = null;
	
	private Thread interlockRunner = null;
	
	public MotionAuthenticationProtocol1(boolean useJSSE) {
		super(TcpPort, false, null, useJSSE);
	}
	
	/** Called by the base class when the object is reset to idle state. */
	protected void resetHook() {
		// idle again --> no segments to compare
		localSegment = null;
		remoteSegment = null;
	}
	
	/** Called by the base class when the whole authentication protocol succeeded. */
	protected void protocolSucceededHook(InetAddress remote, 
			Object optionalRemoteId, String optionalParameterFromRemote, 
			byte[] sharedSessionKey) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolSucceededHook called");
		System.out.println("SUCCESS");
	}
	
	/** Called by the base class when the whole authentication protocol failed. */
	protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, 
			Exception e, String message) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolFailedHook called");
		System.out.println("FAILURE");
	}
	
	/** Called by the base class when the whole authentication protocol shows progress. */
	protected void protocolProgressHook(InetAddress remote, 
			Object optionalRemoteId, int cur, int max, String message) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolProgressHook called");
	}
	
	/** Called by the base class when shared keys have been established and should be verified now.
	 * In this implementation, verification is done listening for significant motion segments and
	 * exchanging them via interlock. 
	 */
	protected void startVerification(byte[] sharedAuthenticationKey, 
			InetAddress remote, String param, Socket socketToRemote) {
		logger.info("startVerification hook called with " + remote + ", param " + param);
	
		this.socketToRemote = socketToRemote;
		interlockRunner = new Thread(new AsyncInterlockHelper(sharedAuthenticationKey));
		interlockRunner.start();
	}

	private boolean checkCoherence() {
		if (localSegment == null || remoteSegment == null) {
			throw new RuntimeException("Did not yet receive both segments, skipping comparing for now");
		}
		
		int len = localSegment.length <= remoteSegment.length ? localSegment.length : remoteSegment.length;
		System.out.println("Using " + len + " samples for coherence computation");
		double[] s1 = new double[len];
		double[] s2 = new double[len];
		for (int i=0; i<len; i++) {
			s1[i] = localSegment[i];
			s2[i] = remoteSegment[i];
		}
		double[] coherence = Coherence.cohere(s1, s2, 128, 0);
		
		double coherenceMean = Coherence.mean(coherence);
		System.out.println("Coherence mean: " + coherenceMean);
		
		return coherenceMean > coherenceThreshold;
	}
	
	/** The implementation of SegmentsSink.addSegment. It will be called whenever
	 * a significant active segment has been sampled completely, i.e. when the
	 * source has become quiescent again.
	 */
	public void addSegment(double[] segment, int startIndex) {
		logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);
		synchronized (localSegmentLock) {
			localSegment = segment;
			localSegmentLock.notify();
		}
	}
	
	public void startAuthentication(String remoteHost) throws UnknownHostException, IOException {
		logger.info("Starting authentication with " + remoteHost);
		startAuthentication(remoteHost, null);
	}
	
	private class AsyncInterlockHelper implements Runnable {
		private byte[] sharedAuthenticationKey;
		
		AsyncInterlockHelper(byte[] authKey) {
			this.sharedAuthenticationKey = authKey;
		}
		
		public void run() {
			try {
			//while (remoteSegment == null) {
				// first wait for the local segment to be received to start the interlock protocol
			logger.debug("Waiting for local segment");
			synchronized(localSegmentLock) {
				while (localSegment == null) {
					try {
						localSegmentLock.wait();
					}
					catch (InterruptedException e) {}
				}
			}
			logger.debug("Local segment sampled, starting interlock protocol");
			
			// TODO: make configurable??? mabye not necessary
			int rounds = 2;

			// TODO: optimize me for smaller arrays!
			String tmp = "";
			synchronized (localSegmentLock) {
				for (int i=0; i<localSegment.length; i++) {
					tmp += Double.toString(localSegment[i]);
					if (i<localSegment.length-1)
						tmp+=" ";
				}
			}
			byte[] localPlainText = tmp.getBytes();
			logger.debug("My segment is " + localPlainText.length + " bytes long");
			
			InterlockProtocol myIp = new InterlockProtocol(sharedAuthenticationKey, rounds, 
					localPlainText.length*8, null, useJSSE);
			byte[][] localParts = myIp.split(myIp.encrypt(localPlainText));
			
			PrintWriter toRemote = new PrintWriter(socketToRemote.getOutputStream(), true);
			/* do not use a BufferedReader here because that would potentially mess up
			 * the stream for other users of the socket (by consuming too many bytes)
			 */
			InputStream fromRemote = socketToRemote.getInputStream();
			
			// first exchange length of message
			toRemote.println("ILCKINIT " + localPlainText.length);
			toRemote.flush();
			String remoteLength = "";
			int remLen = -1;
			int ch = fromRemote.read();
			while (ch != -1 && ch != '\n') {
				// TODO: check if this is enough to deal with line ending problems
				if (ch != '\r')
					remoteLength += (char) ch;
				ch = fromRemote.read();
			}
			if (remoteLength.startsWith("ILCKINIT ")) {
				remLen = Integer.parseInt(remoteLength.substring(9, remoteLength.length()));
				logger.debug("Remote reported message lenght of" + remLen + " bytes");
			}
			else {
				logger.error("Did not received interlock init line from remote. Can not continue");
				verificationFailure(null, null, null, null);
				return;
			}
			InterlockProtocol remoteIp = new InterlockProtocol(sharedAuthenticationKey, rounds, 
					remLen*8, null, useJSSE);
			
			int round=0;
			// TODO: this can be an endless loop - time for a SafetyBeltTimer
			// TODO: for TCP, we should not even use retries.... that could give an attacker ideas
			while (round < rounds) {
				// sending my round
				toRemote.println("ILCKRND " + round + " " + new String(Hex.encodeHex(localParts[round])));
				logger.debug("Sent my round " + round + ", length of part is " + localParts[round].length + " bytes");
				toRemote.flush();
				String remotePart = "";
				ch = fromRemote.read();
				while (ch != -1 && ch != '\n') {
					// TODO: check if this is enough to deal with line ending problems
					if (ch != '\r')
						remotePart += (char) ch;
					ch = fromRemote.read();
				}
				if (remotePart.startsWith("ILCKRND ")) {
					// TODO: do me properly!
					int remoteRound = Integer.parseInt(remotePart.substring(8, 9));
					logger.debug("Received remote round " + remoteRound);
					if (remoteRound == round) {
						byte[] part = Hex.decodeHex(remotePart.substring(10, remotePart.length()).toCharArray());
						remoteIp.addMessage(part, round);
						logger.debug("Received " + part.length + " bytes from other host");
						logger.debug("The round is what I expected, going on to next round");
						round++;
					}
				}
				else {
					logger.error("Unknown line from remote: " + remotePart);
				}
			}
			logger.debug("Interlock protocol completed");
			
			byte[] remotePlainText = remoteIp.decrypt(remoteIp.reassemble());
			logger.debug("Remote segment is " + remotePlainText.length + " bytes long");
			StringTokenizer st = new StringTokenizer(new String(remotePlainText), " ");
			remoteSegment = new double[st.countTokens()];
			int i=0;
			while (st.hasMoreTokens()) {
				remoteSegment[i++] = Float.parseFloat(st.nextToken());
			}
			logger.debug("remote segment is " + remoteSegment.length + " elements long");
			
			boolean coherence = checkCoherence();
			System.out.println("COHERENCE MATCH: " + coherence);
			
			if (coherence)
				verificationSuccess(null, null);
			else
				verificationFailure(null, null, null, null);
			
			// HACK HACK HACK to make the application exit
			stopServer();
			
			//}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	
	/////////////////// testing code begins here ///////////////
	public static void main(String[] args) throws IOException {
		int samplerate = 128; // Hz
		int windowsize = samplerate/2; // 1/2 second
		int minsegmentsize = windowsize; // 1/2 second
		double varthreshold = 350;
		ParallelPortPWMReader r2_a = new ParallelPortPWMReader(args[0], new int[] {0, 1, 2}, samplerate);
		ParallelPortPWMReader r2_b = new ParallelPortPWMReader(args[0], new int[] {4, 5, 6}, samplerate);
		TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		r2_a.addSink(aggr_a.getInitialSinks());
		r2_b.addSink(aggr_b.getInitialSinks());
		MotionAuthenticationProtocol1 ma1 = new MotionAuthenticationProtocol1(true); 
		MotionAuthenticationProtocol1 ma2 = new MotionAuthenticationProtocol1(true); 
		aggr_a.addNextStageSink(ma1);
		aggr_b.addNextStageSink(ma2);
		ma1.startServer();
		ma2.startAuthentication("localhost");
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1/128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(varthreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1/128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(varthreshold);
		r2_a.simulateSampling();
		r2_b.simulateSampling();
	}
}
