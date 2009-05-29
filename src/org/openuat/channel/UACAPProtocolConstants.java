package org.openuat.channel;

public class UACAPProtocolConstants {
	/** Specify which key verification methods are available */
	/** QR code - take picture and decode */
	public static final String VISUAL = "VISUAL";
	/** Use HAPADEP to transmit the hash of the key over the audio channel */
	public static final String AUDIO = "AUDIO";
	/** The user compares two piano songs coming from the two devices */
	public static final String SLOWCODEC = "SLOWCODEC";
	/** The user compares two sentences displayed by the devices */
	public static final String MADLIB = "MADLIB";
	public static final String HASH_COMP = "MANUAL_COMP";

	/** how many bytes of the key are used for the Audio method */	
	public static final int AUDIO_KEYHASH_LENGTH = 7;
	
	/** how many bytes of the key are used for the QR code method */	
	public static final int VIDEO_KEYHASH_LENGTH = 7;
	
	/** with how many bits to pad the hash before sending */
	public static final int AUDIO_PADDING = 6;
	
	/* 
	 * Number of characters of the hash string (hex) that will be shown to the user.
	 * Used by the 'Hash Comparison' authentication method.
	 */
	public static final int HASH_STRING_LENGTH		= 12;
	
	/** how many bytes of the key are used for the Audio method */	
	public static final int AUDIO_KEY_LENGTH = 7;

	/** An identifier used to register a command handler with the RFCOMMServer */
	public static final String PRE_AUTH = "PRE_AUTH";
	
	/** Authentication method supported by BEDA: authentic transfer */
	public static final String TRANSFER_AUTH = "TRANSFER_AUTH";
	
	/** Authentication method supported by BEDA: input */
	public static final String INPUT = "INPUT";
	
	/** synchronization commands */
	public static final String VERIFY = "VERIFY";
	public static final String ACK = "OK";

	/** prepares to transmit the code on the previously selected channel */
	public static final String PREPARE = "PREPARE";

	/** tells to start transmitting */
	public static final String START = "START";

	/** informs that transmission was completed */
	public static final String DONE = "DONE";

	/** informs that verification was successful */ 
	public static final String SUCCESS = "SUCCESS";

	/** informs that verification was NOT successful */
	public static final String FAILURE = "FAILURE";

	/** Replay the slow codec tune */
	public static final String REPLAY = "REPLAY";
	
	
	
	
}
