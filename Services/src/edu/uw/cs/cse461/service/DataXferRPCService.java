package edu.uw.cs.cse461.service;

import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.json.JSONObject;

import edu.uw.cs.cse461.net.rpc.RPCCallableMethod;
import edu.uw.cs.cse461.net.rpc.RPCService;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.Base64;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;

public class DataXferRPCService extends DataXferServiceBase /*implements NetLoadableServiceInterface*/ {
	
	public static final String HEADER_KEY = "header";
	public static final String HEADER_TAG_KEY = "tag";
	public static final String HEADER_XFERLENGTH_KEY = "xferlength";
	public static final String DATA_KEY = "data";
	private RPCCallableMethod dataxferrpc;

	public DataXferRPCService() throws Exception {
		
		super("dataxferpc");

		
		dataxferrpc = new RPCCallableMethod(this, "_dataxfer");
		((RPCService)NetBase.theNetBase().getService("rpc")).registerHandler(loadablename(), "dataxfer", dataxferrpc);


	}
	
	public JSONObject _dataxfer(JSONObject args) throws Exception {
		
		// check header
		JSONObject header = args.getJSONObject(DataXferRPCService.HEADER_KEY);
		if ( header == null  || !header.has(HEADER_TAG_KEY) || !header.getString(HEADER_TAG_KEY).equalsIgnoreCase(HEADER_STR) 
				|| !header.has(HEADER_XFERLENGTH_KEY) )
			throw new Exception("Missing or incorrect header value: '" + header + "'");
		
		int dataLength = header.getInt(HEADER_XFERLENGTH_KEY);
		
		JSONObject data = args.getJSONObject(DataXferRPCService.DATA_KEY);
		
		header.put(HEADER_TAG_KEY, RESPONSE_OKAY_STR);
		header.put(HEADER_XFERLENGTH_KEY, dataLength);
		
		data.put(DATA_KEY, Base64.encodeBytes(new byte[dataLength]));
		return args;
	}
}