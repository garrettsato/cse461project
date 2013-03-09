package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferRPCInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.service.EchoRPCService;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.Base64;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferRPC extends NetLoadableConsoleApp implements DataXferRPCInterface {

	public DataXferRPC() {
		super("dataxferrpc");
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run() throws Exception {
		// TODO Auto-generated method stub
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
				JSONObject header = new JSONObject().put("tag", "xfer");
		
				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				int nTrials = Integer.parseInt(trialStr);
				
				System.out.print("Enter xferLength: ");
				String xferStr = console.readLine();
				int xferLength = Integer.parseInt(xferStr);
				header.put("xferLength", xferLength);

				int socketTimeout = config.getAsInt("net.timeout.socket", 5000);
				
				System.out.println("Host: " + targetIP);
				System.out.println("rcp port: " + targetTCPPortStr);
				System.out.println("trials: " + nTrials);
				
				TransferRateInterval udpResult = null;

				if ( targetRPCPort != 0  ) {
					ElapsedTime.clear();
					// we rely on knowing the implementation of udpPing here -- we throw
					// away the return value because we'll print the ElaspedTime stats
					udpResult = DataXferRate(header, targetIP, targetRPCPort, socketTimeout, nTrials);
				}
				
				if ( udpResult != null ) System.out.println("RPC: " + String.format("%.2f msec (%d failures)", udpResult.mean(), udpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("DataXferRPC.run() caught exception: " +e.getMessage());
		}
	}

	@Override
	public byte[] DataXfer(JSONObject header, String hostIP, int port,
			int timeout) throws JSONException, IOException {
		// TODO Auto-generated method stub
		JSONObject args = new JSONObject().put(EchoRPCService.HEADER_KEY, header);
		JSONObject response = RPCCall.invoke(hostIP, port, "dataxferrpc", "dataxfer", args, timeout);
		String dataString = response.getString("data");
		byte[] data = Base64.decode(dataString);
		return data;
	}

	@Override
	public TransferRateInterval DataXferRate(JSONObject header, String hostIP,
			int port, int timeout, int nTrials) {
		for ( int trial=0; trial<nTrials; trial++) {
			try {
				TransferRate.start("dataxferrpc");
				DataXfer(header, hostIP, port, timeout);
				TransferRate.stop("dataxferrpc", header.getInt("xferLength"));
			} catch (Exception e) {
				try {
					TransferRate.abort("dataxferrpc", header.getInt("xferLength"));
				} catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				System.out.println("DataXferRPC trial failed: " + e.getMessage());
			}
		
		}
		return TransferRate.get("dataxferrpc");
	}
}
