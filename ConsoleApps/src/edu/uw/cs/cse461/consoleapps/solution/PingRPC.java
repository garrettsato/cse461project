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

			int timeout = config.getAsInt("net.timeout.socket", 500);
			
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

			while ( true ) {
				try {

					System.out.print("Enter message to be echoed, or empty line to exit: ");
					String msg = console.readLine();
					if ( msg.isEmpty() ) return;
					
					// send message
					JSONObject header = new JSONObject().put(EchoRPCService.HEADER_TAG_KEY, EchoRPCService.HEADER_STR);
					JSONObject args = new JSONObject().put(EchoRPCService.HEADER_KEY, header)
													  .put(EchoRPCService.PAYLOAD_KEY, msg);
					JSONObject response = RPCCall.invoke(targetIP, targetRPCPort, "echorpc", "echo", args, timeout );
					if ( response == null ) throw new IOException("RPC failed; response is null");
					
					// examine response
					JSONObject rcvdHeader = response.optJSONObject(EchoRPCService.HEADER_KEY);
					if ( rcvdHeader == null || !rcvdHeader.has(EchoRPCService.HEADER_TAG_KEY)||
							!rcvdHeader.getString(EchoRPCService.HEADER_TAG_KEY).equalsIgnoreCase(EchoServiceBase.RESPONSE_OKAY_STR))
						throw new IOException("Bad response header: got '" + rcvdHeader.toString() +
								               "' but wanted a JSONOBject with key '" + EchoRPCService.HEADER_TAG_KEY + "' and string value '" +
								               	EchoServiceBase.RESPONSE_OKAY_STR + "'");
					
					if ( response.has(EchoRPCService.PAYLOAD_KEY) )System.out.println(response.getString(EchoRPCService.PAYLOAD_KEY));
					else System.out.println("No payload returned!?  (No payload sent?)");
					
				} catch (Exception e) {
					System.out.println("????????");
					System.out.println("Exception: " + e.getMessage());
				} 
			}
		} catch (Exception e) {
			System.out.println("EchoRPC.run() caught exception: " +e.getMessage());
		}
	}

	@Override
	public ElapsedTimeInterval ping(JSONObject header, String hostIP, int port,
			int timeout, int nTrials) throws Exception {
		// TODO Auto-generated method stub
		for (int i = 0; i < nTrials; i++) {
			try {
				ElapsedTime.start("PingRCPTotal");
				doRcpPing(header, hostIP, port, timeout);
				ElapsedTime.stop("PingRCPTotal");
			} catch (SocketTimeoutException e) {
				ElapsedTime.abort("PingRCPTotal");
				System.out.println("PingRCPTotal timed out: " + e.getMessage());
			} catch (Exception e) {
				ElapsedTime.abort("PingRCPTotal");
				System.out.println("PingRCPTotal Exception: " + e.getMessage());
			}
		}
		return ElapsedTime.get("PingRCPTotal");
	}
	
	public void doRcpPing(JSONObject header, String hostIP, int port, int timeout) throws Exception {
		JSONObject args = new JSONObject().put(EchoRPCService.HEADER_KEY, header)
				  .put(EchoRPCService.PAYLOAD_KEY, "");
		JSONObject response = RPCCall.invoke(hostIP, port, "echorpc", "echo", args, timeout);
		System.out.println(response + ": " + timeout);
		if (!response.getString(EchoRPCService.HEADER_KEY).equals(EchoServiceBase.RESPONSE_OKAY_STR)) {
			throw new Exception("Got bad response header");
		}
	}

}

