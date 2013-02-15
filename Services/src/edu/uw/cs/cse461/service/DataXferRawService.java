package edu.uw.cs.cse461.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import edu.uw.cs.cse461.net.base.NetBase;
import edu.uw.cs.cse461.util.ConfigManager;
import edu.uw.cs.cse461.util.IPFinder;
import edu.uw.cs.cse461.util.Log;
import edu.uw.cs.cse461.net.base.NetLoadableInterface.NetLoadableServiceInterface;

/**
 * Transfers reasonably large amounts of data to client over raw TCP and UDP sockets.  In both cases,
 * the server simply sends as fast as it can.  The server does not implement any correctness mechanisms,
 * so, when using UDP, clients may not receive all the data sent.
 * <p>
 * Four consecutive ports are used to send fixed amounts of data of various sizes.
 * <p>
 * @author zahorjan
 *
 */
public class DataXferRawService extends DataXferServiceBase implements NetLoadableServiceInterface {
	private static final String TAG="DataXferRawService";
	
	public static final int NPORTS = 4;
	public static final int[] XFERSIZE = {1000, 10000, 100000, 1000000};

	private int mBasePort;
	
	private DatagramSocket[] mDatagramSockets;
	private ServerSocket[] mServerSockets;
	
	public DataXferRawService() throws Exception {
		super("dataxferraw");
		
		ConfigManager config = NetBase.theNetBase().config();
		mBasePort = config.getAsInt("dataxferraw.server.baseport", 0);
		if (mBasePort == 0) throw new RuntimeException("dataxferraw service can't run -- no dataxferraw.server.baseport entry in config file");
		
		// The echo raw service's IP address is the ip the entire app is running under
		String serverIP = IPFinder.localIP();
		if (serverIP == null) throw new Exception("IPFinder isn't providing the local IP address.  Can't run.");
		
		mDatagramSockets = new DatagramSocket[NPORTS];
		mServerSockets = new ServerSocket[NPORTS];
		
		for (int i = 0; i < NPORTS; i++) {
			mDatagramSockets[i] = new DatagramSocket(new InetSocketAddress(serverIP, mBasePort + i));
			mDatagramSockets[i].setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
			Log.i(TAG,  "Datagram socket [" + (mBasePort + i) + "] = " + mDatagramSockets[i].getLocalSocketAddress());		
			
			mServerSockets[i] = new ServerSocket();
			mServerSockets[i].bind(new InetSocketAddress(serverIP, mBasePort + i));
			mServerSockets[i].setSoTimeout(NetBase.theNetBase().config().getAsInt("net.timeout.granularity", 500));
			Log.i(TAG,  "Server socket [" + (mBasePort + i) + "] = " + mServerSockets[i].getLocalSocketAddress());
		}
	
		for (int i = 0; i < NPORTS; i++) {
			Runnable rUdp = new DgramThread(i);
			new Thread(rUdp).start();
			
			Runnable rTcp = new TcpThread(i);
			new Thread(rTcp).start();
		}
	}
	
	/**
	 * Returns string summarizing the status of this server.  The string is printed by the dumpservicestate
	 * console application, and is also available by executing dumpservicestate through the web interface.
	 */
	@Override
	public String dumpState() {
		return "";
	}
	
	// Code/thread handling the UDP socket
	private class DgramThread implements Runnable {
		private int mDataLen;
		private DatagramSocket mDatagramSocket;
		private final int PACKET_SIZE = 1000;
		
		public DgramThread(int sockNdx) {
			mDataLen = XFERSIZE[sockNdx];
			mDatagramSocket = mDatagramSockets[sockNdx];
		}
		
		public void run() {
			byte receiveBuf[] = new byte[HEADER_LEN];
			DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
			byte sendBuf[] = new byte[1000 + RESPONSE_OKAY_LEN];
			System.arraycopy(RESPONSE_OKAY_BYTES, 0, sendBuf, 0, RESPONSE_OKAY_LEN);

			//	Thread termination in this code is primitive.  When shutdown() is called (by the
			//	application's main thread, so asynchronously to the threads just mentioned) it
			//	closes the sockets.  This causes an exception on any thread trying to read from
			//	it, which is what provokes thread termination.
			try {
				while (!mAmShutdown) {
					try {
						mDatagramSocket.receive(receivePacket);
						if (receivePacket.getLength() < HEADER_STR.length())
							throw new Exception("Bad header: length = " + receivePacket.getLength());
						String headerStr = new String(receiveBuf, 0, HEADER_STR.length());
						if (! headerStr.equalsIgnoreCase(HEADER_STR))
							throw new Exception("Bad header: got '" + headerStr + "', wanted '" + HEADER_STR + "'");
						
						int bytesSent = 0;
						while (bytesSent < mDataLen) {
							mDatagramSocket.send(new DatagramPacket(sendBuf, sendBuf.length, receivePacket.getAddress(), receivePacket.getPort()));
							bytesSent += PACKET_SIZE;
						}
					} catch (SocketTimeoutException e) {
						// socket timeout is normal
					} catch (Exception e) {
						Log.w(TAG,  "Dgram reading thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
					}
				}
			} finally {
				if (mDatagramSocket != null) {
					mDatagramSocket.close();
					mDatagramSocket = null;
				}
			}
		}
	}
	
	private class TcpThread implements Runnable {
		private ServerSocket mServerSocket;
		private int dataLength;
		
		public TcpThread(int num) {
			mServerSocket = mServerSockets[num];
			dataLength = XFERSIZE[num];
		}
		
		public void run() {
			byte[] header = new byte[4];
			byte[] buf = new byte[1000];
			int socketTimeout = NetBase.theNetBase().config().getAsInt("net.timeout.socket", 5000);
			try {
				while ( !isShutdown() ) {
					Socket sock = null;
					try {
						// accept() blocks until a client connects.  When it does, a new socket is created that communicates only
						// with that client.  That socket is returned.
						sock = mServerSocket.accept();
						// We're going to read from sock, to get the message to echo, but we can't risk a client mistake
						// blocking us forever.  So, arrange for the socket to give up if no data arrives for a while.
						sock.setSoTimeout(socketTimeout);
						InputStream is = sock.getInputStream();
						OutputStream os = sock.getOutputStream();
						// Read the header.  Either it gets here in one chunk or we ignore it.  (That's not exactly the
						// spec, admittedly.)
						int len = is.read(header);
						if ( len != HEADER_STR.length() )
							throw new Exception("Bad header length: got " + len + " but wanted " + HEADER_STR.length());
						String headerStr = new String(header); 
						if ( !headerStr.equalsIgnoreCase(HEADER_STR) )
							throw new Exception("Bad header: got '" + headerStr + "' but wanted '" + HEADER_STR + "'");
						//send header
						os.write(RESPONSE_OKAY_STR.getBytes());
												
						//repeat write to get the desired transfer amount
						int k = dataLength/1000;
						while (k>0) {
							os.write(buf);
							k--;
						}
						
					} catch (SocketTimeoutException e) {
						// normal behavior, but we're done with the client we were talking with
					} catch (Exception e) {
						Log.i(TAG, "TCP thread caught " + e.getClass().getName() + " exception: " + e.getMessage());
					} finally {
						if ( sock != null ) try { sock.close(); sock = null;} catch (Exception e) {}
					}
				}
			} catch (Exception e) {
				Log.w(TAG, "TCP server thread exiting due to exception: " + e.getMessage());
			} finally {
				if ( mServerSocket != null ) try { mServerSocket.close(); mServerSocket = null; } catch (Exception e) {}
			}
		}
	}
}
