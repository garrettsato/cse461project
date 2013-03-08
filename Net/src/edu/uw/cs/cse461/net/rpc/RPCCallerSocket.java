package edu.uw.cs.cse461.net.rpc;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage.RPCControlMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCCallMessage.RPCInvokeMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCResponseMessage;
import edu.uw.cs.cse461.net.rpc.RPCMessage.RPCResponseMessage.RPCNormalResponseMessage;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.Log;

/**
 * Implements a Socket to use in sending remote RPC invocations.  (It must engage
 * in the RPC handshake before sending the invocation request.)
 * @author zahorjan
 *
 */
 class RPCCallerSocket extends Socket {
	private static final String TAG = "RPCCallerSocket";	
	
	private int id;
	private TCPMessageHandler tcpMsgHandler;
	private boolean wantPersistent;
	private String host;
	
	/**
	 * Create a socket for sending RPC invocations, connecting it to the specified remote ip and port.
	 * @param Remote host's name. In Project 3, it's not terribly meaningful - repeat the ip.
	 *  In Project 4, it's intended to be the string name of the remote system, allowing a degree of sanity checking.
	 * @param ip  Remote system IP address.
	 * @param port Remote RPC service's port.
	 * @param wantPersistent True if caller wants to try to establish a persistent connection, false otherwise
	 * @throws IOException
	 * @throws JSONException
	 */
	RPCCallerSocket(String ip, int port, boolean wantPersistent) throws IOException, JSONException {
		super(ip, port);	
		
		// save state
		JSONObject options = null;
		if (wantPersistent) {
			options = new JSONObject().put("connection", "keep-alive");
		}
		RPCControlMessage controlMsg = new RPCControlMessage("connect", options);
		this.tcpMsgHandler = new TCPMessageHandler(this);
		this.wantPersistent = wantPersistent;
		this.id = controlMsg.id();
		this.host = ip;
		
		// send the connection mesage to the server
		tcpMsgHandler.sendMessage(controlMsg.marshall());
		
		// read the connection message
		JSONObject response = tcpMsgHandler.readMessageAsJSONObject();
		// server did not provide a success response
		if (!response.has("type") || !response.getString("type").equals("OK")) {
			String msg = "The server is not configured to respond to RPC calls";
			if (response.has("msg")) {
				msg = response.getString("msg");
			}
			throw new IOException(msg);
		}
	}
	
	public String getHost() {
		return host;
	}
	
	public boolean isPersistent() {
		return wantPersistent;
	}
	
	public int id() { 
		return id;
	}
	
	public JSONObject invoke(String serviceName, String method, JSONObject userRequest) throws JSONException, IOException  {
		JSONObject response = null;
		RPCInvokeMessage invokeMsg = new RPCInvokeMessage(serviceName, method, userRequest);
		tcpMsgHandler.sendMessage(invokeMsg.marshall());
		response = tcpMsgHandler.readMessageAsJSONObject();
		System.out.println(response);
		return response.getJSONObject("value");
	}

	/**
	 * Close this socket.
	 */
	synchronized public void discard() {
	}
}
