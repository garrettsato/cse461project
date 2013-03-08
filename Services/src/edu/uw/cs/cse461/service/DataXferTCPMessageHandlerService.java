package edu.uw.cs.cse461.service;

import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.json.JSONObject;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;
import edu.uw.cs.cse461.net.tcpmessagehandler.TCPMessageHandler;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;

public class DataXferTCPMessageHandlerService extends DataXferServiceBase implements NetLoadableServiceInterface {
	private static final String TAG="DataXferTCPMessageHandlerService";


	private ServerSocket mServerSocket;
	
	public DataXferTCPMessageHandlerService() throws Exception {
		super("dataxfertcpmessagehandler");
		String serverIP = IPFinder.localIP();
		if (serverIP == null) {
			throw new Exception("IP is null");
		}
		int tcpPort = 0;
		mServerSocket = new ServerSocket();
		mServerSocket.bind(new InetSocketAddress(serverIP, tcpPort));
		mServerSocket.setSoTimeout( NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
		Log.i(TAG,  "Server socket = " + mServerSocket.getLocalSocketAddress());

		Thread tcpThread = new Thread() {
			public void run() {
				try {
					while ( !mAmShutdown ) {
						Socket sock = null;
						try {
							sock = mServerSocket.accept();  // if this fails, we want out of the while loop...
							// should really spawn a thread here, but the code is already complicated enough that we don't bother
							TCPMessageHandler tcpMessageHandlerSocket = null;
							try {
								// this loop exits when readMessageAsString() throws an IOException indicating EOF, or 
								// because it has timed out on the read
								while ( true ) {

									tcpMessageHandlerSocket = new TCPMessageHandler(sock);
									tcpMessageHandlerSocket.setTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000));
									tcpMessageHandlerSocket.setNoDelay(true);
									
									String header = tcpMessageHandlerSocket.readMessageAsString();
									if ( ! header.equalsIgnoreCase(HEADER_STR))
										throw new Exception("Bad header: '" + header + "'");
									JSONObject transferSize = tcpMessageHandlerSocket.readMessageAsJSONObject();
									if (!transferSize.has("transferSize"))
										throw new Exception("Invalid transferSize key");
									int transferSizeValue = transferSize.getInt("transferSize");
									int amtXferred = 0;
									tcpMessageHandlerSocket.sendMessage(RESPONSE_OKAY_STR);
									
									while (amtXferred < transferSizeValue) { 
										
										int msgSize = 1000;
										if (transferSizeValue - amtXferred < 1000) {
											msgSize = transferSizeValue - amtXferred;
										} 
										
										tcpMessageHandlerSocket.sendMessage(new byte[msgSize]);
										amtXferred += msgSize;
										
									}
									// now respond
									
								}
							} catch (SocketTimeoutException e) {
								Log.e(TAG, "Timed out waiting for data on tcp connection");
							} catch (EOFException e) {
								// normal termination of loop
								Log.d(TAG, "EOF on tcpMessageHandlerSocket.readMessageAsString()");

							} catch (Exception e) {
								Log.i(TAG, "Unexpected exception while handling connection: " + e.getMessage());

							} finally {
								if ( tcpMessageHandlerSocket != null ) try { tcpMessageHandlerSocket.close(); } catch (Exception e) {}
							}
						} catch (SocketTimeoutException e) {
							// this is normal.  Just loop back and see if we're terminating.
						}
					}
				} catch (Exception e) {
					Log.w(TAG, "Server thread exiting due to exception: " + e.getMessage());
					System.out.println("Server thread exiting due to exception: " + e.getMessage());

				} finally {
					if ( mServerSocket != null )  try { mServerSocket.close(); } catch (Exception e) {}
					mServerSocket = null;
				}
			}
		};
		tcpThread.start();
	}



	@Override
	public String dumpState() {
		StringBuilder sb = new StringBuilder(super.dumpState());
		sb.append("\nListening on: ");
		if ( mServerSocket != null ) sb.append(mServerSocket.toString());
		sb.append("\n");
		return sb.toString();
	}

}
