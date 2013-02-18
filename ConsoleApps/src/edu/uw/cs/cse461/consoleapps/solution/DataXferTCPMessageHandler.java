package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferTCPMessageHandlerInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.DataXferRawService;
import edu.uw.cs.cse461.service.DataXferServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRate;
import edu.uw.cs.cse461.util.SampledStatistic.TransferRateInterval;

public class DataXferTCPMessageHandler extends NetLoadableConsoleApp implements DataXferTCPMessageHandlerInterface {

	public DataXferTCPMessageHandler() {
		super("dataxfertcpmessagehandler");
	}

	@Override
	public byte[] DataXfer(String header, String hostIP, int port, int timeout,
			int xferLength) throws JSONException, IOException {
		// TODO Auto-generated method stub
		int amtXferred = 0;
		byte[] receivedBytes = new byte[xferLength];
		Socket sock = new Socket(hostIP, port); 
		TCPMessageHandler tcpMsgHandler = new TCPMessageHandler(sock);
		tcpMsgHandler.setTimeout(timeout);
		System.out.println("header: " + header);
		tcpMsgHandler.sendMessage(header);
		JSONObject transferSize = new JSONObject().put("transferSize", xferLength);
		System.out.println("xferLength: " + xferLength);
		tcpMsgHandler.sendMessage(transferSize);
		
		String okayStr = tcpMsgHandler.readMessageAsString();
		if (!okayStr.equals(DataXferServiceBase.RESPONSE_OKAY_STR)) 
			throw new IOException("Server did not respond with the okay string");
		
		while (amtXferred < xferLength) {
			System.out.println("currenet amtXferred: " + amtXferred);
			byte[] receiveBuf = tcpMsgHandler.readMessageAsBytes();
			for (int i = 0; i < receiveBuf.length; i++) {
				receivedBytes[amtXferred + i] = receiveBuf[i];
			}
			amtXferred += receiveBuf.length;
		}
		return receivedBytes;
	}

	@Override
	public TransferRateInterval DataXferRate(String header, String hostIP,
			int port, int timeout, int xferLength, int nTrials) {
		// TODO Auto-generated method stub

		for ( int trial=0; trial<nTrials; trial++) {
			try {
				TransferRate.start("tcp");
				DataXfer(header, hostIP, port, timeout, xferLength);
				TransferRate.stop("tcp", xferLength);
			} catch (Exception e) {
				TransferRate.abort("tcp", xferLength);
				System.out.println("TCP trial failed: " + e.getMessage());
			}
		
		}
		return TransferRate.get("tcp");
	}

	@Override
	public void run() throws Exception {
		// TODO Auto-generated method stub
		try {

			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

			ConfigManager config = NetBase.theNetBase().config();
			String server = config.getProperty("net.server.ip");
			if ( server == null ) {
				System.out.print("Enter a host ip, or exit to exit: ");
				server = console.readLine();
				if ( server == null ) return;
				if ( server.equals("exit")) return;
			}

			int basePort = config.getAsInt("dataxferraw.server.baseport", -1);
			if ( basePort == -1 ) {
				System.out.print("Enter port number, or empty line to exit: ");
				String portStr = console.readLine();
				if ( portStr == null || portStr.trim().isEmpty() ) return;
				basePort = Integer.parseInt(portStr);
			}
			
			int socketTimeout = config.getAsInt("net.timeout.socket", -1);
			if ( socketTimeout < 0 ) {
				System.out.print("Enter socket timeout (in msec.): ");
				String timeoutStr = console.readLine();
				socketTimeout = Integer.parseInt(timeoutStr);
				
			}

			System.out.print("Enter number of trials: ");
			String trialStr = console.readLine();
			int nTrials = Integer.parseInt(trialStr);

			for ( int index=0; index<DataXferRawService.NPORTS; index++ ) {

				TransferRate.clear();
				System.out.println("I should not be here\n\n\n\n\n\n");
				
				int port = basePort + index;
				int xferLength = DataXferRawService.XFERSIZE[index];

				System.out.println("\n" + xferLength + " bytes");

				//-----------------------------------------------------
				// TCP transfer
				//-----------------------------------------------------

				TransferRateInterval tcpStats = DataXferRate(DataXferServiceBase.HEADER_STR, server, port, socketTimeout, xferLength, nTrials);

				System.out.println("\nTCP: xfer rate = " + String.format("%9.0f", tcpStats.mean() * 1000.0) + " bytes/sec.");
				System.out.println("TCP: failure rate = " + String.format("%5.1f", tcpStats.failureRate()) +
						           " [" + tcpStats.nAborted()+ "/" + tcpStats.nTrials() + "]");

			}
			
		} catch (Exception e) {
			System.out.println("Unanticipated exception: " + e.getMessage());
		}
	}

}
