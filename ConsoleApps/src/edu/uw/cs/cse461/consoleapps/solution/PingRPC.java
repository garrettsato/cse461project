package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.service.EchoRPCService;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;
import edu.uw.cs.cse461.consoleapps.PingInterface.PingRPCInterface;

public class PingRPC extends NetLoadableConsoleApp implements PingRPCInterface {
	private static final String TAG="PingRPC";
	
	// ConsoleApp's must have a constructor taking no arguments
	public PingRPC() {
		super("pingrpc");
	}
	
	@Override
	public void run() {
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();
			
			String targetIP = config.getProperty("net.server.ip");
			if ( targetIP == null ) {
				System.out.println("No net.server.ip entry in config file.");
				System.out.print("Enter a server ip, or empty line to exit: ");
				targetIP = console.readLine();
				if ( targetIP == null || targetIP.trim().isEmpty() ) return;
			}

			System.out.print("Enter the server's RPC port, or empty line to exit: ");
			String targetTCPPortStr = console.readLine();
			if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) return;
			int targetRPCPort = Integer.parseInt(targetTCPPortStr);

			try {
				
				// send message
				JSONObject header = new JSONObject().put("tag", "echo");
		
				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				int nTrials = Integer.parseInt(trialStr);

				int socketTimeout = config.getAsInt("net.timeout.socket", 5000);
				
				System.out.println("Host: " + targetIP);
				System.out.println("rcp port: " + targetTCPPortStr);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval udpResult = null;

				if ( targetRPCPort != 0  ) {
					ElapsedTime.clear();
					// we rely on knowing the implementation of udpPing here -- we throw
					// away the return value because we'll print the ElaspedTime stats
					udpResult = ping(header, targetIP, targetRPCPort, socketTimeout, nTrials);
				}
				
				if ( udpResult != null ) System.out.println("RPC: " + String.format("%.2f msec (%d failures)", udpResult.mean(), udpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingRPC.run() caught exception: " +e.getMessage());
		}
	}
	

	@Override
	public ElapsedTimeInterval ping(JSONObject header, String hostIP, int port,
			int timeout, int nTrials) throws Exception {
		// TODO Auto-generated method stub
		for (int i = 0; i < nTrials; i++) {
			try {
				ElapsedTime.start("PingRPCTotal");
				doRcpPing(header, hostIP, port, timeout);
				ElapsedTime.stop("PingRPCTotal");
			} catch (SocketTimeoutException e) {
				ElapsedTime.abort("PingRPCTotal");
				System.out.println("PingRPCTotal timed out: " + e.getMessage());
			} catch (Exception e) {
				ElapsedTime.abort("PingRPCTotal");
				System.out.println("PingRPCTotal Exception: " + e.getMessage());
			}
		}
		return ElapsedTime.get("PingRPCTotal");
	}
	
	public void doRcpPing(JSONObject header, String hostIP, int port, int timeout) throws Exception {
		JSONObject args = new JSONObject().put(EchoRPCService.HEADER_KEY, header)
				  .put(EchoRPCService.PAYLOAD_KEY, "");
		RPCCall.invoke(hostIP, port, "echorpc", "echo", args, timeout);
	}
}

