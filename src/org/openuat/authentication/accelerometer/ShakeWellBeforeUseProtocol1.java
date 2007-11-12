/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * Initial public release 2007-03-29
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.accelerometer;

import java.io.IOException;
import java.util.BitSet;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.openuat.authentication.DHWithVerification;
import org.openuat.authentication.InterlockProtocol;
import org.openuat.authentication.KeyManager;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.features.Coherence;
import org.openuat.features.TimeSeriesUtil;
import org.openuat.sensors.SegmentsSink;
import org.openuat.sensors.SegmentsSink_Int;
import org.openuat.sensors.TimeSeriesAggregator;
import org.openuat.util.HostAuthenticationServer;
import org.openuat.util.ProtocolCommandHandler;
import org.openuat.util.RemoteConnection;

/** This is the first variant of the motion authentication protocol. It
 * uses Diffie-Hellman key agreement with verification that the shared keys
 * are equal on both hosts by sending the full time series segment through
 * interlock, encrypted with the shared key. Then both hosts compute the
 * coherence between the received time series segment and their own and continue
 * when it exceeds a threshold. 
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ShakeWellBeforeUseProtocol1 extends DHWithVerification 
		implements SegmentsSink, SegmentsSink_Int {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.accelerometer.ShakeWellBeforeUseProtocol1" /*ShakeWellBeforeUseProtocol1.class*/);

	/** The TCP port we use for this protocol, if running over TCP. */
	public static final int TcpPort = 54322;
	
	/** Allow the (incoming) key agreement to take at maximum this amout of ms. */
	public static final int KeyAgreementProtocolTimeout = 15000;
	
	/** The maximimum time that the interlock exchange of active segments
	 * with the remote host is allowed to take in ms.
	 */
	public static final int RemoteInterlockExchangeTimeout = 10000;
	
	/** Keep an incoming key verification connection open for this long when
	 * no local segment is available [ms].
	 */
	public static final int IncomingConnectionWaitForLocalSegmentTimeout = 2000;
	
	/** If running with split phases (verification decoupled from key agreement),
	 * then this host protocol command can be handled by a protocol handler
	 * returned by getCommandHandler().
	 */
	public static final String MotionVerificationCommand = "VERF_Motion1";

	/** This holds our last local segment, as soon as we have received it from the
	 * segment source. It is modified by addSegment and read by AsyncInterlockHelper#run
	 * from two different threads. Synchronization happens via localSegmentLock.
	 * @see #addSegment(double[], int)
	 * @see AsyncInterlockHelper#run
	 * @see #localSegmentLock
	 * 
	 * TODO: do we need to be more intelligent here, and keep a queue of local segments to
	 * check in the interlock helpers? 
	 */
	private double[] localSegment = null;
	/** This is only used as a synchronization lock for accessing localSegment from
	 * different threads, and has no other use.
	 */
	private Object localSegmentLock = new Object();
	
	/** The window size for the coherence computation. */
	private int windowSize;
	
	/** The current threshold for the coherence. If it is higher, the two segments
	 * are considered similar enough.
	 */
	private double coherenceThreshold;
	
	/** If set to true, the thread started by startVerification will not terminate
	 * but will check continuously, only calling the hook methods in this class
	 * itself. This is mainly used for debugging, and should not be set to true for
	 * "real-world" operation!
	 */
	private boolean continuousChecking = false;
	/** In continuousChecking mode, this may be set to a static key for
	 * <b>demonstration purposes only</b>. If set, it will be used instead
	 * of any key from KeyManager;
	 */ 
	protected byte[] staticAuthenticationKey;
	
	/** This is only used to remember the coherence mean that has been computed last.
	 * It should only be used for debugging, because the decision if verification 
	 * succeeded or not is made within this class.
	 */
	protected double lastCoherenceMean = 0;
	
	/** Holds the thread objects that are used to run the interlock protocols asynchronously.
	 * If concurrentVerificationSupported=true, multiple interlock protocols may run
	 * at the same time, and thus multiple thread objects may be registered in
	 * this vector. If concurrentVerificationSupported=false, only one element will
	 * be put into the vector. When this is set != null, then one or multiple
	 * interlock protocols are running. startVerification will exit unless this
	 * is set to null.
	 *  
	 * Thread objects are set and started by startVerification, and executed in 
	 * AsyncInterlockHelper#run.
	 * @see #startVerification
	 * @see AsyncInterlockHelper
	 */
	private Vector interlockRunners = new Vector();

	/** This is used for the synchronization of multiple concurrent interlock
	 * protocol runs when we are trying to verify multiple devices at the same
	 * time.
	 */
	private BitSet interlockGroup = null;
	
	/** Initializes the object, only setting useJSSE at the moment.
	 * 
	 * @param coherenceThreshold A good value is 0.65 for samplerate=512 or 0.82 for samplerate=128.
	 * @param windowSize A good value is samplerate/2.
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
	public ShakeWellBeforeUseProtocol1(HostAuthenticationServer server, boolean keepConnected, 
			boolean concurrentVerificationSupported, double coherenceThreshold, 
			int windowSize, boolean useJSSE) {
		super(server, keepConnected, concurrentVerificationSupported, null, useJSSE);
		// also register our command handler for split phase operation
		server.addProtocolCommandHandler(MotionVerificationCommand, new MotionVerificationCommandHandler());
		this.coherenceThreshold = coherenceThreshold;
		this.windowSize = windowSize;
	}
	
	/** Called by the base class when the whole authentication protocol succeeded. 
	 * Does nothing. */
	//@Override
	protected void protocolSucceededHook(RemoteConnection remote, Object optionalVerificationId,
			String optionalParameterFromRemote,	byte[] sharedSessionKey) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolSucceededHook called, remote host reported coherence value of " + optionalParameterFromRemote);
		System.out.println("SUCCESS");
	}
	
	/** Called by the base class when the whole authentication protocol failed. 
	 * Does nothing. */
	//@Override
	protected void protocolFailedHook(RemoteConnection remote, Object optionalVerificationId,
			Exception e, String message) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolFailedHook called");
		System.out.println("FAILURE");
	}
	
	/** Called by the base class when the whole authentication protocol shows progress. 
	 * Does nothing. */
	//@Override
	protected void protocolProgressHook(RemoteConnection remote,  
			int cur, int max, String message) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolProgressHook called");
	}

	protected void protocolStartedHook(RemoteConnection remote) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolStartedHook called");
	}

	/** Called by the base class when the whole authentication protocol is reset. 
	 * Does nothing. */
	//@Override
	protected void resetHook(RemoteConnection remote) {
		// nothing to do, really
	}

	/** Called by the base class when shared keys have been established and should be verified now.
	 * In this implementation, verification is done listening for significant motion segments and
	 * exchanging them via interlock. 
	 * @see #interlockRunner
	 * @see AsyncInterlockHelper
	 */
	//@Override
	protected void startVerificationAsync(byte[] sharedAuthenticationKey, 
			String param, RemoteConnection toRemote) {
		logger.info("startVerification hook called with " + toRemote.getRemoteName() + ", param " + param);
	
		synchronized (interlockRunners) {
			if (interlockRunners.isEmpty()) {
				interlockGroup = null; // no synchronization necessary
				Thread runner = new AsyncInterlockHelper(toRemote, false, sharedAuthenticationKey,
						0, 0);
				interlockRunners.addElement(runner); 
				runner.start();
			}
			else {
				logger.warn("Interlock thread already running, can not process two interlock " +
					"protocol runs concurrently. Terminating second request.");
			}
		}
	}
	
	/** Starts multiple concurrent verifications in an interlock group. All
	 * hosts specified in toRemotes must be in state STATE_VERIFICATION.
	 * @param toRemotes The remote hosts to try and verify with.
	 * @param openChannels If set to true, then the channels will be opened
	 *                     before using them. 
	 */
	protected void startConcurrentVerifications(RemoteConnection[] toRemotes, boolean openChannels) {
		logger.info("startVerification hook called with " + toRemotes.length + " hosts concurrently");
		
		synchronized (interlockRunners) {
			if (interlockRunners.isEmpty()) {
				interlockGroup = new BitSet();
				int groupSize = toRemotes.length, instanceNum=0;
				// sanity check
				for (int i=0; i<toRemotes.length; i++) {
					if (keyManager.getState(toRemotes[i]) != KeyManager.STATE_VERIFICATION) {
						logger.error("Remote " + toRemotes[i] + " is not in verifying state, not starting key verification with it");
						toRemotes[i] = null;
						groupSize--;
					}
				}
				
				for (int i=0; i<toRemotes.length; i++) {
					Thread runner = new AsyncInterlockHelper(toRemotes[i], openChannels, 
							keyManager.getAuthenticationKey(toRemotes[i]),
							groupSize, instanceNum++);
					interlockRunners.addElement(runner); 
					runner.start();
				}
			}
			else {
				logger.warn("Interlock thread already running, can not process two interlock " +
					"protocol runs concurrently. Terminating second request.");
			}
		}
	}
	
	private static double coherence(double[] segment1, double[] segment2, int windowSize) {
		if (segment1 == null || segment2 == null) {
			throw new RuntimeException("Did not yet receive both segments, skipping comparing for now");
		}

		double[][] equalizedSeries = TimeSeriesUtil.cutSegmentsToEqualLength(segment1, segment2);
		double[] coherence = Coherence.cohere(equalizedSeries[0], equalizedSeries[1], windowSize, 
				(int) (ShakeWellBeforeUseParameters.coherenceWindowOverlapFactor * windowSize));
		if (coherence == null) {
			logger.warn("Coherence not computed, no match");
			return -1;
		}
		return Coherence.mean(coherence, ShakeWellBeforeUseParameters.coherenceCutOffFrequency);
	}

	/** This helper function calls Coherence.cohere on localSegment and remoteSegment,
	 * but only on the first part of both with the minimum length. That is, it trims the
	 * larger of the two to have the same length as the smaller. 
	 * @return true if the mean of the coherence function is larger than the threshold,
	 *         false otherwise.
	 * @see #coherenceThreshold
	 */
	private boolean checkCoherence(double[] remoteSegment) {
		double c = coherence(localSegment, remoteSegment, windowSize);
		if (c < 0)
			return false;
		
		lastCoherenceMean = c;
		System.out.println("Coherence mean: " + lastCoherenceMean);
		
		return lastCoherenceMean > coherenceThreshold;
	}
	
	/** The implementation of SegmentsSink.addSegment. It will be called whenever
	 * a significant active segment has been sampled completely, i.e. when the
	 * source has become quiescent again.
	 * @see #localSegment
	 * @see #localSegmentLock
	 */
	public void addSegment(double[] segment, int startIndex) {
		logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);
		synchronized (localSegmentLock) {
			localSegment = segment;
			localSegmentLock.notify();
		}
	}

	/** The implementation of SegmentsSink_Int.addSegment. It will be called whenever
	 * a significant active segment has been sampled completely, i.e. when the
	 * source has become quiescent again.
	 * @see #localSegment
	 * @see #localSegmentLock
	 */
	public void addSegment(int[] segment, int startIndex) {
		logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);
		synchronized (localSegmentLock) {
			localSegment = new double[segment.length];
			for (int i=0; i<segment.length; i++)
				localSegment[i] = segment[i];
			localSegmentLock.notify();
		}
	}

	/** Sets the coherence threshold. 
	 * @param coherenceThreshold The threshold over which a coherence value will be taken
	 *                           as valid (i.e. shaken within the same hand). Must be
	 *                           between 0 and 1.
	 * @see #coherenceThreshold 
	 */
	public void setCoherenceThreshold(double coherenceThreshold) {
		if (coherenceThreshold < 0 || coherenceThreshold > 1)
			throw new IllegalArgumentException("Coherence threshold must be in [0;1].");
		
		logger.debug("Setting coherence threshold to " + coherenceThreshold);
		this.coherenceThreshold = coherenceThreshold;
	}
	
	/** Returns the current value of the coherence threshold. 
	 * @return The current coherence threshold.
	 * @see #coherenceThreshold
	 */
	public double getCoherenceThreshold() {
		return coherenceThreshold;
	}
	
	/** Enable or disable continuous checking.
	 * @param continuousChecking Only set to true after reading the description
	 *                           of the member variable continuousChecking. Generally
	 *                           leave to false (the default).
	 * @see #continuousChecking
	 */
	public void setContinuousChecking(boolean continuousChecking) {
		if (continuousChecking) {
			logger.warn("Enabling continuous checking mode! This should only be used for debugging, and not in production");
		}
		this.continuousChecking = continuousChecking;
	}
	
	/** Returns the current value of continuousChecking.
	 * @return The current value of continuousChecking.
	 * @see #continuousChecking
	 */
	public boolean getContinuousChecking() {
		return continuousChecking;
	}
	
	/** Returns the last coherence mean value that has been computed locally.
	 * It is valid after protocolSucceededHook has been called and might be valid
	 * after protocolFailedHook has been called.
	 * @return The last coherence mean that has been computed.
	 */
	public double getLastCoherenceMean() {
		return lastCoherenceMean;
	}
	
	/** Returns true if (at least) one protocol instance is currently running 
	 * (i.e. interlockRunners has an entry).
	 */
	public boolean isAsyncProtocolRunning() {
		synchronized (interlockRunners) {
			return !interlockRunners.isEmpty();
		}
	}
	
	/** This method implements the main functionality of this class. It exchanges
	 * local and remote segments via interlock* and uses coherence to check their
	 * similarity. It will call all the necessary hooks, but expects localSegment
	 * to be set.
	 * @return true if continous checking should continue, false on a fatal error.
	 *         <b>Note</b>: This is <b>not</b> the decision! If authentication
	 *         failed or succeeded will be communicated with events.
	 * @throws InternalApplicationException 
	 * @throws IOException 
	 */
	private boolean keyVerification(RemoteConnection remote, 
			byte[] sharedAuthenticationKey, int groupSize, int instanceNum) 
			throws IOException, InternalApplicationException {
		// TODO: make configurable??? mabye not necessary
		int rounds = 2;
		int totalCodingTime=0, totalInterlockTime=0, totalComparisonTime=0;
		long timestamp=0;

		byte[] localPlainText = null;
		synchronized (localSegmentLock) {
			// sanity checks
			if (localSegment == null) {
				logger.error("keyVerification called without localSegment being set. This should not happen!");
				return false;
			}
/*			for (int i=0; i<localSegment.length; i++) {
				if (localSegment[i] < 0 || localSegment[i] > 2) {
					logger.error("Sample value out of expected range: " + localSegment[i] + ", aborting");
					protocolRunFinished(myThread);
					return false;
				}
			}*/
            if (logger.isInfoEnabled())
            	timestamp = System.currentTimeMillis();
			localPlainText = TimeSeriesUtil.encodeVector(localSegment);
            if (logger.isInfoEnabled())
            	totalCodingTime += System.currentTimeMillis()-timestamp;
		}
		if (localPlainText == null) {
			verificationFailure(remote, null, null, null, "Interlock exchange aborted: sample value out of expected range");
			return false;
		}
		logger.debug("My segment is " + localPlainText.length + " bytes long");

		// exchange with the remote host
        if (logger.isInfoEnabled())
        	timestamp = System.currentTimeMillis();
		byte[] remotePlainText = InterlockProtocol.interlockExchange(localPlainText, 
				remote.getInputStream(), remote.getOutputStream(),
				// TODO: enable mirror attack prevention after testing
				sharedAuthenticationKey, rounds, false, 
				false, RemoteInterlockExchangeTimeout, useJSSE,
				interlockGroup, groupSize, instanceNum);
        if (logger.isInfoEnabled())
        	totalInterlockTime += System.currentTimeMillis()-timestamp;
		if (remotePlainText == null) {
			logger.warn("Interlock protocol failed, can not continue to compare with remote segment");
			verificationFailure(remote, null, null, null, "Interlock protocol failed");
			return false;
		}

		boolean decision = false;
		// and check the received remote segment, compare it with our local segment
		logger.debug("Remote segment is " + remotePlainText.length + " bytes long");
		// count the tokens
        if (logger.isInfoEnabled())
        	timestamp = System.currentTimeMillis();
		double[] remoteSegment = TimeSeriesUtil.decodeVector(remotePlainText);
        if (logger.isInfoEnabled())
        	totalCodingTime += System.currentTimeMillis()-timestamp;
		if (remoteSegment != null) {
			logger.debug("remote segment is " + remoteSegment.length + " elements long");
	        if (logger.isInfoEnabled())
	        	timestamp = System.currentTimeMillis();
			decision = checkCoherence(remoteSegment);
	        if (logger.isInfoEnabled())
	        	totalComparisonTime += System.currentTimeMillis()-timestamp;
			logger.info("COHERENCE MATCH: " + decision + "(computed " + 
					lastCoherenceMean + " and threshold is " + coherenceThreshold + ")");
		}
		else
			decision = false;

		// final decision
		if (decision) { 
			if (!continuousChecking)
				// "productive" case
				verificationSuccess(remote, null, Double.toString(lastCoherenceMean));
			else {
				// demo-only case!
				protocolSucceededHook(remote, null, Double.toString(lastCoherenceMean), null);
				// need to to that here, as we don't call the cleanup/finished method in this case
				synchronized (localSegmentLock) {
					localSegment = null;
				}
			}
		}
		else {
			if (!continuousChecking)
				// "productive" case
				verificationFailure(remote, null, null, null, "Coherence is below threshold, time series are not similar enough");
			else {
				// demo-only case!
				protocolFailedHook(remote, null, null, "Coherence is below threshold, time series are not similar enough");
				// need to to that here, as we don't call the cleanup/finished method in this case
				synchronized (localSegmentLock) {
					localSegment = null;
				}
			}
		}

        if (logger.isInfoEnabled())
        	logger.info("Segment coding took " + totalCodingTime + 
        			"ms, interlock took " + totalInterlockTime + 
        			"ms, comparison took" + totalComparisonTime + "ms");

        return true;
	}
	
	/** This is a helper class for executing the interlock protocol in the background.
	 * It is started by startVerification.
	 */
	private class AsyncInterlockHelper extends Thread {
		private byte[] sharedAuthenticationKey;
		private RemoteConnection remote;
		private int groupSize, instanceNum;
		private boolean openChannel;
		
		AsyncInterlockHelper(RemoteConnection remote, boolean openChannel, byte[] authKey,
				int groupSize, int instanceNum) {
			this.remote = remote;
			this.sharedAuthenticationKey = authKey;
			this.groupSize = groupSize;
			this.instanceNum = instanceNum;
			this.openChannel = openChannel;
		}
		
		public void run() {
			try {
				do {
					// first wait for the local segment to be received to start the interlock protocol
					logger.debug("Waiting for local segment");
					synchronized(localSegmentLock) {
						while (localSegment == null) {
							try {
								localSegmentLock.wait();
							}
							catch (InterruptedException e) {
								// just ignore, it will only make the wait shorter but won't hurt
							}
						}
					}
					logger.debug("Local segment sampled, starting interlock protocol");
					
					if (openChannel)
						remote.open();
					
					if (!keyVerification(remote, sharedAuthenticationKey,
							groupSize, instanceNum) && !continuousChecking) {
						/* If keyVerification returns false, then it will already
						 * have fired an AuthenticationFailure event and closed
						 * fromRemote. So any derived classes can already react
						 * to this, just close the thread cleanly here.
						 */
						break;
					}
				} while (continuousChecking);
			} catch (IOException e) {
				logger.error("Background verification thread aborted with: " + e);
				e.printStackTrace();
				verificationFailure(remote, null, null, e, "Background verification aborted (possibly due to timeout in interlock phase?)");
			} catch (InternalApplicationException e) {
				logger.error("Background verification thread aborted with: " + e);
				e.printStackTrace();
				verificationFailure(remote, null, null, e, "Background verification aborted");
			} catch (Exception e) {
				logger.error("UNEXPECTED EXCEPTION, exiting interlock runner thread: " + e);
				e.printStackTrace();
				verificationFailure(remote, null, null, e, "Background verification aborted");
			}
			
			// thread finished, so remove ourselves from the list of threads
			synchronized (interlockRunners) {
				if (!interlockRunners.removeElement(this)) 
					logger.error("Error: tried to remove runner object " + this +
						" but could not find it in list. This should not happen!");
				if (interlockRunners.isEmpty()) {
					// removed the last one now, clean up - this allows startVerification to be called again
					// don't re-use the segment
					localSegment = null;
					interlockGroup = null;
				}
			}
		}
	}

	/** This is a protocol command handler to implement split phases, i.e.
	 * to run key agreement opportunistically, disconnect the remote channel,
	 * and reconnect later for key verification. The handler implements the
	 * server part to react to incoming key verification requests (instead of
	 * incoming key agreement requests, which are handled by HostProtocolHandler).
	 * The client part is activated by first connecting the appropriate
	 * RemoteConnection and then calling startVerificationAsync on it.
	 */
	private class MotionVerificationCommandHandler implements ProtocolCommandHandler {
		public boolean handleProtocol(String firstLine, RemoteConnection remote) {
			// sanity check
			if (! firstLine.startsWith(MotionVerificationCommand)) {
				logger.error("MotionVerificationCommandHandler invoked with a protocol line that does not start with the expected command '" + 
						MotionVerificationCommand + "'. This should not happen!");
				return false;
			}

			if (!incomingVerificationRequestHook(remote)) {
				logger.error("incomfingVerificationRequestHook returned false, aborting verification");
				return true;
			}
				
			synchronized(localSegmentLock) {
				/* If we don't have a local segment (yet) when being contacted, 
				 * wait for some time before aborting. The segment may just be
				 * finished and thus available shortly. */
				long start = System.currentTimeMillis();
				while (localSegment == null && 
						System.currentTimeMillis() - start <= 
							IncomingConnectionWaitForLocalSegmentTimeout) {
					try {
						localSegmentLock.wait(IncomingConnectionWaitForLocalSegmentTimeout);
					} catch (InterruptedException e) {
						// just ignore - it will drop into the loop and try again
					}
				}
				if (localSegment == null && !continuousChecking) {
					logger.error("Incoming motion key verification request from " +
							remote + ", but no local segment available after "+
							IncomingConnectionWaitForLocalSegmentTimeout + "ms, aborting");
					verificationFailure(remote, null, null, null, 
							"No local segment available to process incoming key verification");
					return false;
				}
			}
			
			try {
				if (!continuousChecking) {
					// incoming key verification, so need to retrieve the authentication key
					byte[] authKey = keyManager.getAuthenticationKey(remote);
					keyVerification(remote, authKey, 
						0, 0 // incoming request, so it can't be an interlock group
						);
				}
				else {
					byte[] authKey;
					if (staticAuthenticationKey != null) {
						logger.warn("Using static authentication key for continuous checking mode");
						authKey = staticAuthenticationKey;
					}
					else
						authKey = keyManager.getAuthenticationKey(remote);
					// this is rather hackish, but stay connected and verifying...
					AsyncInterlockHelper h = new AsyncInterlockHelper(remote, false, 
							authKey, 0, 0);
					h.run();
				}
				return true;
			} catch (IOException e) {
				logger.error("IOException while running incoming key verification: " + e);
				return false;
			} catch (InternalApplicationException e) {
				logger.error("InternalApplicationException while running incoming key verification: " + e);
				return false;
			}
		}
	}

	/** This hook is called when an incoming verification request has been
	 * received, but before starting the verification in terms of interlock
	 * exchange. The implementation in here does nothing, but derived classes
	 * may use this for pre-processing or a "veto" of an incoming connection.
	 * @param remote The remote host requesting verification.
	 * @return true if verification should succeed, false to abort.
	 */
	protected boolean incomingVerificationRequestHook(RemoteConnection remote) {
		logger.info("Accepting incoming verification request from " + remote);
		return true;
	}
	
	/////////////////// testing code begins here ///////////////
//#if cfg.includeTestCode
	public static void main(String[] args) throws IOException {
		if (args.length == 4 && args[0].equals("check")) {
			double[] segment1 = TimeSeriesUtil.decodeVector(args[1].getBytes());
			double[] segment2 = TimeSeriesUtil.decodeVector(args[2].getBytes());
			System.out.println(coherence(segment1, segment2, Integer.parseInt(args[3])));
			return;
		}
		
		org.openuat.sensors.SamplesSource r = new org.openuat.sensors.ParallelPortPWMReader(args[0], ShakeWellBeforeUseParameters.samplerate);
		TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, ShakeWellBeforeUseParameters.activityDetectionWindowSize, ShakeWellBeforeUseParameters.coherenceSegmentSize, ShakeWellBeforeUseParameters.coherenceSegmentSize);
		TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, ShakeWellBeforeUseParameters.activityDetectionWindowSize, ShakeWellBeforeUseParameters.coherenceSegmentSize, ShakeWellBeforeUseParameters.coherenceSegmentSize);
		r.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
		r.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1/128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(ShakeWellBeforeUseParameters.activityVarianceThreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1/128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(ShakeWellBeforeUseParameters.activityVarianceThreshold);
		
		boolean keepConnected = false;
		HostAuthenticationServer s1, s2;
		s1 = new org.openuat.util.TCPPortServer(TcpPort, KeyAgreementProtocolTimeout, keepConnected, true);
		// this will not be started
		s2 = new org.openuat.util.TCPPortServer(0, KeyAgreementProtocolTimeout, keepConnected, true); 
		ShakeWellBeforeUseProtocol1 ma1 = new ShakeWellBeforeUseProtocol1(s1, keepConnected, false,
				0.82, ShakeWellBeforeUseParameters.coherenceWindowSize, true); 
		ShakeWellBeforeUseProtocol1 ma2 = new ShakeWellBeforeUseProtocol1(s2, keepConnected, false,
				0.82, ShakeWellBeforeUseParameters.coherenceWindowSize, true);
		aggr_a.addNextStageSegmentsSink(ma1);
		aggr_b.addNextStageSegmentsSink(ma2);
		ma1.startListening();
		ma2.startAuthentication(new org.openuat.util.RemoteTCPConnection(
				new java.net.Socket("localhost", TcpPort)), KeyAgreementProtocolTimeout, null);
		
		r.simulateSampling();
	}
//#endif
}
