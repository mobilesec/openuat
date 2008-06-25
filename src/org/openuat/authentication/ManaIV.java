/* Copyright Rene Mayrhofer
 * File created 2008-01-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.SecureRandom;
import java.util.Arrays;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.Hash;
import org.openuat.util.RemoteConnection;
import org.openuat.util.SafetyBeltTimer;

/** This is a base class that implements the basics of all protocols based 
 * on/belonging to the MANA IV family of multi-channel authentication
 * protocols as specified in 
 * [Sven Laur and Kaisa Nyberg: "Efﬁcient Mutual Data Authentication Using 
 * Manually Authenticated Strings: Extended Version"]
 *
 * The ManaIV class has a dual interface and can thus be used in two different
 * ways: either by explicitly calling all steps in the protocol from an outside
 * caller on the instantiated object (henceforth referred to as the PlainObject
 * style) or by registering an out-of-band channel that is subsequently used by
 * ManaIV to handle the complete protocol run (hanceforth referred to as the
 * Hollywood style, cf. http://c2.com/cgi/wiki?HollywoodPrinciple). The 
 * PlainObject style is more flexible, as all steps "outside" the ManaIV crypto
 * can be handled in any way, and is used by instantiating ManaIV with the
 * constructor @see ManaIV(...).
 * The Hollywood style can be easier to use, making the whole authentication
 * protocol a black box by reducing it to a single method call, and can be used
 * when an object implementing the OOBChannel interface is conveniently
 * available. In this case, instantiate ManaIV with the constructor 
 * @see ManaIV(...) to pass the OOBChannel.
 * 
 * In both cases, ManaIV will fire usual authentication events for any outside
 * listener, and can be configured to internally run a Diffie-Hellman key 
 * agreement (the MA-DH protocol) or to use a shared secret (but 
 * unauthenticated) key provided by the caller.
 * 
 * The simplest possible case to use the ManaIV class for authentication is to
 * instantiate it, Hollywood style, with an existing OOBChannel object and to 
 * let it run the key agreement:
 * <pre>
 * ManaIV m = new ManaIV(myOobChannel, true);
 * m.addAuthenticationProgressHandler(myself);
 * m.authenticate(); // this method returns immediately
 * // ... wait for AuthenticationSuccess event and use the embedded key 
 * </pre>
 * 
 * Alternatively, it may e.g. be used for the heavy crypto lifting, but 
 * out-of-band message transfer is handled in a custom way, PlainObject style:
 * ManaIV m1 = new ManaIV(true);
 * byte[] msg = m1.getOobMessage();
 * // transfer msg to the remote side in a secure way
 * // on the other side:
 * m2.addOobMessage(msg);  
 * 
 * TODO: signal to event listeners if the other side has been human-verified
 * (in case of unidirectional OOB channels) 
 * 
 * TODO: for input or unidirectional OOB channels, might need commitments on the
 * wireless channel with ACKs before sending the OOB message? cf. Wong/Stajano
 * 
 * TODO: compare MA-DH with SAS and DH-SC (cagalj): message order
 * seeing-is-believing uses "pre-authentication" by sending hashes over visual before transmitting the public keys
 * --> long OOB messages required
 * sib-plus-mana transmits keys first over wireless and then does one-way "transmit"
 * of a hash over visual --> authentication with one-way channel, but A needs to 
 * trust B's comparison of the hashes --> not really mutual, but ok when devices are trusted
 * sib+ma3 --> additional mutual commitment steps to random strings that are added
 * to the hash that is transmitted visually
 * TODO: do we need this for one-way? maybe add to our protocol?
 * DH-SC uses two commitments before exchanging public keys, then id comparison as in MANA IV
 * --> only difference is that MA-DH only uses 1 commitment while DH-SC uses 2
 * --> another difference is that random numbers (in id in MA-DH) are part of first 
 * commitment in DH-SC, but not in MA-DH --> make them a part?
 * SAS is basically similar to MANA-IV/MA-DH 
 * BEDA is a variant of MANA III with n rounds, 1 for each bit --> seems unnecessary and can be done in 1 round
 * 
 * Wong and Stajano have a MANA III variant where the manually input code no longer needs to be secret
 * --> maybe use this extension?
 * --> commit mutually and include secret chosen earlier
 * --> BUT: security only holds when auxiliary input takes place AFTER commitments!
 * a restricted variant can be used asymmetrically
 * 
 * TODO: rename out-of-band to auxiliary channel
 * 
 * ==> results should be the "unified auxiliary channel authentication protocol"
 * UACAP 
 * needs to be able to cope with INPUT, TRANSFER, and VERIFY
 * probably by using optional protocol steps (either of the auxiliary transfer must be done)
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ManaIV extends HostProtocolHandler {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.authentication.ManaIV" /*ManaIV.class*/);

	private static final int NonceByteLength = 16;
	
	public final static String ProtocolTypeMaDH = "MA-DH";

    public static final String Protocol_AuthenticationAcknowledge2 = "AUTHACK2 ";

    /** At the moment, the whole protocol consists of 5 stages. */
    public static final int AuthenticationStages = 5;
    
    private OOBChannel oobChannel = null;

	/**
	 * 
	 * @param combinedMaDH Set to true if the protocol should run an instance
	 *        of the MA-DH protocol that combines Diffie-Hellman key exchange
	 *        with verification over an out-of-band channel (this is probably
	 *        what you want if no specific protocol design is used). 
	 *        Set to false if an instance of the Mana IV protocol should run
	 *        for using a key agreement different from Diffie-Hellman.
	 */
	public ManaIV(OOBChannel oob, boolean combinedMaDH, RemoteConnection con, int timeoutMs, 
    		boolean keepConnected, boolean useJSSE) {
		super(con, timeoutMs, keepConnected, useJSSE);
		this.oobChannel = oob;
	}
	
	/** This method implements the simplest possible commitment scheme for
	 * public Diffie-Hellman keys: a hash value on the key. There is no 
	 * security proof for this scheme yet, as discussed in the original
	 * Mana IV article:
	 * 
	 * Practical implementations [ZJC06,WUS06] of the MA–DH protocol use 
	 * c = H(g^a) and such a relaxed security proof would bridge the gap 
	 * between theory and practice.
	 * 
	 * The current implementation uses the @see Hash class as H and therefore
	 * SHA256-double.
     *
     * Other options for implementing the commitment scheme according to the
     * Mana IV article are:
     * 
     * In reality, a cryptographic hash functions like SHA-1 are used instead 
     * of commitments, as such constructions are hundred times faster and there 
     * are no setup assumptions. Let H be a collision resistant hash function. 
     * Then the hash commitment is computed as (c, d) := Com(x, r) with 
     * c = H(x||r) and d = (x, r) or, as in HMAC, c = H(r ⊕ opad||H(r ⊕ ipad||x)) 
     * with d = r. Both constructions are a priori not hiding. We would like to 
     * have a provably secure construction. In theory, we could use one-wayness 
     * of H and define commitment with hard-core bits but this leads to large 
     * commitments. Instead, we use Bellare-Rogaway random oracle design 
     * principle to heuristically argue that a hash commitment based on the 
     * OAEP padding is a better alternative. Recall that the OAEP padding is 
     * c = H(s, t), s = (x||0^k0 ) XOR g(r), t = r XOR f(s). The corresponding 
     * commitment c along with d = r is provably hiding and binding if g is 
     * pseudorandom, f is random oracle, and H is collision resistant. A priori 
     * SHA-1 and SHA-512 are not known to be non-malleable, as it has never 
     * been a design goal. On the other hand, the security proof of OAEP shows 
     * CCA2 security (non-malleability) provided that H is a partial-domain 
     * one-way permutation.
     *
	 * @param ownPublicKey This side's public key for MA-DH.
	 * @return
	 * @throws InternalApplicationException 
	 */
	public byte[] commitment(byte[] ownPublicKey) throws InternalApplicationException {
		return Hash.doubleSHA256(ownPublicKey, useJSSE);
	}
	
	/** MANA-IV:
	 * 1. Alice computes (c, d) := Com_pk(ka) for random ka and sends (ma, c) 
	 *    to Bob.
	 * 2. Bob chooses random kb and sends (mb, kb) to Alice.
	 * 3. Alice sends d to Bob, who computes ka = Open_pk(c, d) and halts if 
	 *    ka = empty.
     * Both parties compute a test value oob = h(ma || mb, ka, kb) from the 
     * received messages.
	 * 4. Both parties accept (ma, mb) iff the local l-bit test values ooba 
	 *    and oobb coincide.
     *
     * Speciﬁcation: h is a keyed hash function with sub-keys ka, kb from a 
     * message space of commitment scheme. The hash function h and the public 
     * parameters pk of the commitment scheme are ﬁxed and distributed by a 
     * trusted authority.
	 */

	/** MA-DH:
	 * 1. Alice computes (c, d) := Com_pk(ka) for ka = g^a, a = random 
	 *    and sends (ida, c) to Bob.
	 * 2. Bob computes kb = g^b for random b and sends (idb, kb) to Alice.
	 * 3. Alice sends d to Bob, who computes ka = Open_pk(c, d) and halts if 
	 *    ka = empty.
     * Both parties compute sid = (ida, idb) and oob = h(sid, ka, kb) from the 
     * received messages.
	 * 4. Both parties accept key = (g^a)^b = (g^b)^a iff the l-bit test 
	 *    values ooba and oobb coincide.
     * 
     * Specification: h is a keyed hash function with sub-keys ka, kb of G 
     * where G = g is a q element Decisional Diffie-Hellman group; G is a 
     * message space of commitment scheme. Public parameters pk and G are 
     * fixed and distributed by a trusted authority. Device identifiers ida 
     * and idb must be unique in time, for example, a device address followed 
     * by a session counter.
     * 
     * In this implementation, Alice is the client and Bob the server. This 
     * protocol is only assumed to be secure for a <b>bidirectional and
     * authentic</b> out-of-band channel.
     * 
     * TODO: what should we use with unidirectional OOB channels?
     * TODO: maybe provide a factory instead of folding the selection based
     * on channel properties into ManaIV???
	 */
    protected void performAuthenticationProtocolMaDh(boolean serverSide) {
    	SimpleKeyAgreement ka = null;
        String inOrOut, serverToClient, clientToServer, remoteAddr=null;
        int totalTransferTime=0, totalCryptoTime=0;
        long timestamp=0;
        
        try {
			remoteAddr = connection.getRemoteAddress().toString();
		} catch (IOException e1) {
			logger.error("Can not get address of remote. This should not happen!");
		}

        if (logger.isDebugEnabled()) {
        	logger.debug("Starting authentication protocol as " + (serverSide ? "server" : "client"));
        	logger.debug("Remote is " + remoteAddr + ", with timeout " + timeoutMs + "ms");
        }

        if (serverSide) {
        	inOrOut = "Incoming";
        	serverToClient = "sent";
        	clientToServer = "received";
        } else {
        	inOrOut = "Outgoing";
        	serverToClient = "received";
        	clientToServer = "sent";
        }
        
        if (logger.isDebugEnabled())
        	logger.debug(inOrOut + " connection to authentication service with " + remoteAddr);
        
        SafetyBeltTimer timer = null;
        try
        {
        	fromRemote = connection.getInputStream();
            // this enables auto-flush
            toRemote = new OutputStreamWriter(connection.getOutputStream());

            // now that we have the InputStream, bind our timer to it
            if (timeoutMs > 0)
            	timer = new SafetyBeltTimer(timeoutMs, fromRemote);

            if (serverSide) {
            	println(Protocol_Hello);
            }
            else {
                String msg = readLine();
                if (!msg.equals(Protocol_Hello)) {
                	raiseAuthenticationFailureEvent(connection, null, "Protocol error: did not get greeting from server");
                    shutdownConnectionCleanly();
                    return;
                }
        	}
            raiseAuthenticationProgressEvent(connection, 1, AuthenticationStages, inOrOut + " authentication connection, " + serverToClient + " greeting");

           	timestamp = System.currentTimeMillis();
           	
    		/* Both sides add a random nonce to refer to this specific session
    		 * to prevent two overlapping protocol runs with the same ida and
    		 * idb.
    		 */
            SecureRandom r = new SecureRandom();
            byte[] nonce = new byte[NonceByteLength];
            r.nextBytes(nonce);
            // TODO: we should really add our own address to the ID!
            String myId = new String(Hex.encodeHex(nonce));

            byte[] myPubKey = null, remotePubKey = null, 
            	remoteCommitment = null, remoteId = null;
            if (!serverSide) {
            	// step 1: Alice computes her public key and sends the commitment
            	ka = new SimpleKeyAgreement(useJSSE);
            	myPubKey = ka.getPublicKey();
            	String commitment = new String(Hex.encodeHex(commitment(myPubKey)));
               	totalCryptoTime += System.currentTimeMillis()-timestamp;
               	timestamp = System.currentTimeMillis();
            	println(Protocol_AuthenticationRequest + ProtocolTypeMaDH + 
            			" " + myId + " " + commitment +
            			(optionalParameter != null ? " " + Protocol_AuthenticationRequest_Param + optionalParameter : ""));
               	totalTransferTime += System.currentTimeMillis()-timestamp;
            }
            else {
            	// step 1, part 2: Bob receives Alice's commitment
            	String expectedMsg = Protocol_AuthenticationRequest + 
   			 		ProtocolTypeMaDH;
            	String line = helper_getLine(expectedMsg, connection, true);
            	
            	// first part is the remote ID part
            	int off1 = line.indexOf(' ', expectedMsg.length());
                String remoteIdStr = line.substring(expectedMsg.length(), off1);
                // next part is the commitment, which may be the last one
            	int off2 = line.indexOf(' ', off1+1);
            	String remoteCommitmentStr = line.substring(off1, 
                		off2 != -1 ? off2 : line.length());
                try {
                	remoteId = Hex.decodeHex(remoteIdStr.toCharArray());
                	remoteCommitment = Hex.decodeHex(remoteCommitmentStr.toCharArray());
                }
                catch (DecoderException e) {
                    logger.warn("Protocol error: could not parse commitment or remote ID");
                    println("Protocol error: could not parse commitment or remote ID");
                    raiseAuthenticationFailureEvent(connection, e, "Protocol error: can not decode remote commitment or ID");
                    shutdownConnectionCleanly();
                    return;
                }
               
                // additional parameter from the remote?
                int optParamOff = line.indexOf(Protocol_AuthenticationRequest_Param);
                if (optParamOff != -1) {
                	optionalParameter = line.substring(optParamOff + Protocol_AuthenticationRequest_Param.length());
                	if (logger.isDebugEnabled())
                		logger.debug("Received optional parameter from client: '" + optionalParameter + "'.");
                }
               	totalTransferTime += System.currentTimeMillis()-timestamp;
            }
            raiseAuthenticationProgressEvent(connection, 2, AuthenticationStages, inOrOut + " authentication connection, " + clientToServer + " public key");

           	timestamp = System.currentTimeMillis();
            if (serverSide) {
            	// step 2: Bob sends his public key and ID
                // for performance reasons: only now start the DH phase
            	ka = new SimpleKeyAgreement(useJSSE);
            	myPubKey = ka.getPublicKey();
            	String myPubKeyStr = new String(Hex.encodeHex(myPubKey));
               	totalCryptoTime += System.currentTimeMillis()-timestamp;
               	timestamp = System.currentTimeMillis();
            	println(Protocol_AuthenticationAcknowledge + myId + " " + myPubKeyStr);
               	totalTransferTime += System.currentTimeMillis()-timestamp;
            }
            else {
            	// step 2, part 2: Alice receives Bob's ID and public key
            	String expectedMsg = Protocol_AuthenticationAcknowledge;
            	String line = helper_getLine(expectedMsg, 
            			connection, false);
            	
            	// first part is the remote ID part
            	int off = line.indexOf(' ', expectedMsg.length());
                String remoteIdStr = line.substring(expectedMsg.length(), off);
                // next part is the public key
            	String remotePubKeyStr = line.substring(off, line.length());
                try {
                	remoteId = Hex.decodeHex(remoteIdStr.toCharArray());
                	remotePubKey = Hex.decodeHex(remotePubKeyStr.toCharArray());
                }
                catch (DecoderException e) {
                    logger.warn("Protocol error: could not parse remote public key or ID");
                    println("Protocol error: could not parse remote public key or ID");
                    raiseAuthenticationFailureEvent(connection, e, "Protocol error: can not decode remote public key or ID");
                    shutdownConnectionCleanly();
                    return;
                }
                if (remotePubKey.length < 128) {
                    logger.warn("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
                    println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
                    raiseAuthenticationFailureEvent(connection, null, "Protocol error: remote key too short (only " + remotePubKey.length + " bytes instead of 128)");
                    shutdownConnectionCleanly();
                    return;
                }
                totalTransferTime += System.currentTimeMillis()-timestamp;
            }
            raiseAuthenticationProgressEvent(connection, 3, AuthenticationStages, inOrOut + " authentication connection, " + serverToClient + " public key");

           	timestamp = System.currentTimeMillis();
            if (!serverSide) {
            	// step 3: Alice sends her public key
            	String myPubKeyStr = new String(Hex.encodeHex(myPubKey));
            	println(Protocol_AuthenticationAcknowledge2 + myPubKeyStr);
               	totalTransferTime += System.currentTimeMillis()-timestamp;            	
            }
            else {
            	// step 3, part 2: Bob receives Alice's public key
            	String expectedMsg = Protocol_AuthenticationAcknowledge2;
            	String line = helper_getLine(expectedMsg, 
            			connection, false);
            	
            	// first and only part is the remote public key
            	String remotePubKeyStr = line.substring(expectedMsg.length(), line.length());
                try {
                	remotePubKey = Hex.decodeHex(remotePubKeyStr.toCharArray());
                }
                catch (DecoderException e) {
                    logger.warn("Protocol error: could not parse remote public key");
                    println("Protocol error: could not parse remote public key");
                    raiseAuthenticationFailureEvent(connection, e, "Protocol error: can not decode remote public key");
                    shutdownConnectionCleanly();
                    return;
                }
                if (remotePubKey.length < 128) {
                    logger.warn("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
                    println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
                    raiseAuthenticationFailureEvent(connection, null, "Protocol error: remote key too short (only " + remotePubKey.length + " bytes instead of 128)");
                    shutdownConnectionCleanly();
                    return;
                }
                // and check that it matches the commitment
                totalTransferTime += System.currentTimeMillis()-timestamp;
               	timestamp = System.currentTimeMillis();
               	byte[] remoteCommitmentExpected = commitment(remotePubKey);
                if (!Arrays.equals(remoteCommitment, remoteCommitmentExpected)) {
                    logger.warn("Protocol error: remote commitment does not match public key");
                    println("Protocol error: remote commitment does not match public key");
                    raiseAuthenticationFailureEvent(connection, null, "Protocol error: remote commitment does not match public key");
                    shutdownConnectionCleanly();
                    return;
                }
                totalCryptoTime += System.currentTimeMillis()-timestamp;
            }
            // step 3, part 3: Alice and Bob compute the out-of-band message
           	timestamp = System.currentTimeMillis();
           	// TODO: add local and remote addresses
            byte[] oobInput = new byte[2*NonceByteLength + 
                                       myPubKey.length + remotePubKey.length];
            // order: first client, then server
            if (!serverSide) {
            	System.arraycopy(myId, 0, oobInput, 0, NonceByteLength);
            	System.arraycopy(remoteId, 0, oobInput, NonceByteLength, NonceByteLength);
            	System.arraycopy(myPubKey, 0, oobInput, NonceByteLength*2, myPubKey.length);
            	System.arraycopy(remotePubKey, 0, oobInput, NonceByteLength*2+myPubKey.length, remotePubKey.length);
            }
            else {
            	System.arraycopy(remoteId, 0, oobInput, 0, NonceByteLength);
            	System.arraycopy(myId, 0, oobInput, NonceByteLength, NonceByteLength);
            	System.arraycopy(remotePubKey, 0, oobInput, NonceByteLength*2, remotePubKey.length);
            	System.arraycopy(myPubKey, 0, oobInput, NonceByteLength*2+remotePubKey.length, myPubKey.length);
            }
            byte[] oobMsg = Hash.doubleSHA256(oobInput, true);
            totalCryptoTime += System.currentTimeMillis()-timestamp;
            raiseAuthenticationProgressEvent(connection, 4, AuthenticationStages, inOrOut + " authentication connection, " + clientToServer + " public key");
            // TODO: now transmit the OOB message
            
            // step 4: compare OOB messages and, if equal, create keys
            // TODO: compare OOB messages
           	timestamp = System.currentTimeMillis();
            ka.addRemotePublicKey(remotePubKey);
            Object sessKey = ka.getSessionKey();
            Object authKey = ka.getAuthenticationKey();
           	totalCryptoTime += System.currentTimeMillis()-timestamp;
            raiseAuthenticationProgressEvent(connection, 5, AuthenticationStages, inOrOut + " authentication connection, computed shared secret");

            // the authentication success event sent here is just an array of two keys
            if (keepConnected) {
            	logger.debug("Not closing socket as requested, but passing it to the success event.");
            	// don't shut down the streams because this effectively shuts down the connection
            	// but make sure that the last message has been sent successfully
            	toRemote.flush();
            	raiseAuthenticationSuccessEvent(connection, new Object[] {sessKey, authKey,
            			optionalParameter, connection});
            }
            else {
            	raiseAuthenticationSuccessEvent(connection, new Object[] {sessKey, authKey,
            			optionalParameter });
				logger.info("Closing channel that has been used for key agreement");
            	shutdownConnectionCleanly();
            }
            
            	logger.warn("Key transfers took " + totalTransferTime + 
            			"ms, crypto took " + totalCryptoTime + "ms");
        }
        catch (InternalApplicationException e)
        {
            logger.error("Caught exception during host protocol run, aborting: " + e);
            // also communicate any application exception to interested
			// listeners
            raiseAuthenticationFailureEvent(connection, e, null);
            shutdownConnectionCleanly();
        }
        catch (IOException e)
        {
            logger.error("Caught exception during host protocol run, aborting: " + e);
            // even if we ignore the exception and not treat it as an error
			// case, report it to listeners
            // so that they can clean up their state of this authentication
			// (identified by the remote)
            raiseAuthenticationFailureEvent(connection, null, "Client closed connection unexpectedly or hit timeout");
            shutdownConnectionCleanly();
        }
        catch (Exception e)
        {
            logger.fatal("UNEXPECTED EXCEPTION: " + e);
            e.printStackTrace();
            shutdownConnectionCleanly();
        }
        finally {
            if (ka != null)
                ka.wipe();
            // this is not strictly necessary, but clean up properly
            if (timer != null)
            	timer.stop();
            if (logger.isDebugEnabled())
            	logger.debug("Ended " + inOrOut + " authentication connection with " + remoteAddr);
        }
    }
}
