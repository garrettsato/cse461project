package edu.uw.cs.cse461.consoleapps.solution;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import edu.uw.cs.cse461.consoleapps.DataXferInterface.DataXferRPCInterface;
import edu.uw.cs.cse461.net.base.NetLoadable.NetLoadableConsoleApp;
import edu.uw.cs.cse461.net.rpc.RPCCall;
import edu.uw.cs.cse461.service.EchoRPCService;
import edu.uw.cs.cse461.service.EchoServiceBase;
import edu.uw.cs.cse461.util.Base64;
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
		
	}

	@Override
	public byte[] DataXfer(JSONObject header, String hostIP, int port,
			int timeout) throws JSONException, IOException {
		// TODO Auto-generated method stub
		JSONObject args = new JSONObject().put(EchoRPCService.HEADER_KEY, header);
		System.out.println(args);
		JSONObject response = RPCCall.invoke(hostIP, port, "dataxferrpc", "dataxfer", args);
		System.out.println(response + ": " + timeout);
//		if (!response.getString(EchoRPCService.HEADER_KEY).equals(EchoServiceBase.RESPONSE_OKAY_STR)) {
//			throw new IOException("Got bad response header");
//		}
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
