package edu.uw.cs.cse461.consoleapps.solution;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketTimeoutException;

import edu.uw.cs.cse461.consoleapps.PingInterface.PingTCPMessageHandlerInterface;
import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTime;
import edu.uw.cs.cse461.util.SampledStatistic.ElapsedTimeInterval;

public class PingTCPMessageHandler extends NetLoadableConsoleApp implements PingTCPMessageHandlerInterface {

	public PingTCPMessageHandler() throws Exception {
		super("pingtcpmessagehandler");
	}
	

	@Override
	public void run() throws Exception {
		// TODO Auto-generated method stub
		try {
			// Eclipse doesn't support System.console()
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			ConfigManager config = NetBase.theNetBase().config();

			try {

				String targetIP = config.getProperty("net.server.ip");
				if ( targetIP == null ) {
					System.out.println("No net.server.ip entry in config file.");
					System.out.print("Enter the server's ip, or empty line to exit: ");
					targetIP = console.readLine();
					if ( targetIP == null || targetIP.trim().isEmpty() ) return;
				}

				int targetTCPPort;
				System.out.print("Enter the server's TCP port, or empty line to skip: ");
				String targetTCPPortStr = console.readLine();
				if ( targetTCPPortStr == null || targetTCPPortStr.trim().isEmpty() ) targetTCPPort = 0;
				else targetTCPPort = Integer.parseInt(targetTCPPortStr);

				System.out.print("Enter number of trials: ");
				String trialStr = console.readLine();
				int nTrials = Integer.parseInt(trialStr);

				int socketTimeout = config.getAsInt("net.timeout.socket", 5000);
				
				System.out.println("Host: " + targetIP);
				System.out.println("tcp port: " + targetTCPPort);
				System.out.println("trials: " + nTrials);
				
				ElapsedTimeInterval tcpResult = null;

				if ( targetTCPPort != 0 ) {
					ElapsedTime.clear();
					tcpResult = ping(EchoServiceBase.HEADER_STR, targetIP, targetTCPPort, socketTimeout, nTrials);
				}

				if ( tcpResult != null ) System.out.println("TCP: " + String.format("%.2f msec (%d failures)", tcpResult.mean(), tcpResult.nAborted()));

			} catch (Exception e) {
				System.out.println("Exception: " + e.getMessage());
			} 
		} catch (Exception e) {
			System.out.println("PingRaw.run() caught exception: " +e.getMessage());
		}
	}

	@Override
	public ElapsedTimeInterval ping(String header, String hostIP, int port,
			int timeout, int nTrials) throws Exception {
		// TODO Auto-generated method stub
		for (int i = 0; i < nTrials; i++) {
			try {
				ElapsedTime.start("PingRaw_TCPTotal");
				doTcpPing(header, hostIP, port, timeout);
				ElapsedTime.stop("PingRaw_TCPTotal");
			} catch (SocketTimeoutException e) {
				ElapsedTime.abort("PingRaw_TCPTotal");
				System.out.println("tcpPing timed out: " + e.getMessage());
			} catch (Exception e) {
				ElapsedTime.abort("PingRaw_TCPTotal");
				System.out.println("tcpPing Exception: " + e.getMessage());
			}
		}
		
		return ElapsedTime.get("PingRaw_TCPTotal");
	}
	
	public void doTcpPing(String header, String hostIP, int tcpPort, int socketTimeout) throws Exception {
		
		Socket tcpSocket = new Socket(hostIP, tcpPort);
		TCPMessageHandler tcpMsgHandler = new TCPMessageHandler(tcpSocket);
		tcpMsgHandler.setTimeout(socketTimeout);
		System.out.println("header: " + header);
		tcpMsgHandler.sendMessage(header);		
		tcpMsgHandler.sendMessage("");
		
		String okayStr = tcpMsgHandler.readMessageAsString();
		if (!okayStr.equals(EchoServiceBase.RESPONSE_OKAY_STR)) 
			throw new Exception("Bad response okay string.");
		String emptyMsg = tcpMsgHandler.readMessageAsString();
		if (!emptyMsg.equals(""))
			throw new Exception("Bad empty response");
		
		tcpSocket.close();
	}
}
