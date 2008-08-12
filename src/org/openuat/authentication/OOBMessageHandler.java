package org.openuat.authentication;

public interface OOBMessageHandler {
	
	public void handleOOBMessage(int channelType, byte [] data);

}
