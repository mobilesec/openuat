package org.openuat.apps.android;

import org.openuat.authentication.KeyManager;
import org.openuat.authentication.accelerometer.ShakeWellBeforeUseProtocol1;
import org.openuat.channel.main.HostAuthenticationServer;
import org.openuat.channel.main.RemoteConnection;

import android.content.Intent;
import android.util.Log;

/**
 * 
 * @author Erich FH
 *
 */
public class ShakeAuthenticator extends ShakeWellBeforeUseProtocol1 {

	public final static String Command_Debug_Streaming = "DEBG_Stream";
	public final static float CoherenceThresholdSucceed = 0.45f;
	public final static float CoherenceThresholdFailHard = 0.15f;

	ShakeAuthenticator(HostAuthenticationServer server) {
		super(server, true, true, true, CoherenceThresholdSucceed,
				CoherenceThresholdFailHard, 32, false);
		setContinuousChecking(true);
	}

	KeyManager getKeyManager() {
		return keyManager;
	}

	protected void protocolFailedHook(boolean failHard,
			RemoteConnection remote, Object optionalVerificationId,
			Exception e, String message) {
		Log.i(this.getClass().toString(), "protocol failed");
//		AppObject.authenticationSuccess = false;
//		AppObject app = new AppObject();
//		app.setAuthenticationSuccess(false);
	}

	protected void protocolSucceededHook(RemoteConnection remote,
			Object optionalVerificationId, String optionalParameterFromRemote,
			byte[] sharedSessionKey) {
		Log.i(this.getClass().toString(), "protocol succeeded");
//		AppObject.authenticationSuccess = true;
//		AppObject app = new AppObject();
//		app.setAuthenticationSuccess(true);
	}

	protected void startVerificationAsync(byte[] sharedAuthenticationKey,
			String optionalParam, RemoteConnection remote) {
		super.startVerificationAsync(sharedAuthenticationKey, optionalParam, remote);
		Log.i("log", "startVerificationAsync");
	}
}
