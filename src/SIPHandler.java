import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.UUID;
import java.lang.Thread;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.Timer;

public class SIPHandler {
	//public static ArrayList<HostInfo> hosts;	
	public static ArrayList<SIPHandlerThread> threads; 
    private String dataReceived;
    public static ActionListener byeTimerActionListener;
	public SIPHandler() {
    	
    	// Start the web server in a new thread
    	new Thread(){
    		public void run() {
    			WebServer webServer = new WebServer(Integer.parseInt(SIPSpeaker.getProperties().getProperty("http_port")));
    		}
    	}.start();
    	
    	String sip_interface = SIPSpeaker.getProperties().getProperty("sip_interface");
    	int sip_port = Integer.parseInt(SIPSpeaker.getProperties().getProperty("sip_port"));
    	//hosts = new ArrayList<HostInfo>();
    	threads = new ArrayList<SIPHandlerThread>();
    	
    	try {
    		int rtpPort = 49514; // Initial RTP Port
    		InetAddress addr = InetAddress.getByName(sip_interface);
			DatagramSocket serverSocket = new DatagramSocket(sip_port, addr);
			for(;;) {
				byte[] receiveData = new byte[1024];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				serverSocket.receive(receivePacket);
				dataReceived = new String(receivePacket.getData());
				System.out.println(dataReceived);
	            InetAddress callerIP = receivePacket.getAddress();
				int callerPort = receivePacket.getPort();
				HostInfo host= new HostInfo(receivePacket.getAddress().toString(),receivePacket.getPort()+"");
				boolean present=false;
				int i;
				for(i=0;i<threads.size();i++)
				{
					if(host.getIp().substring(1).equals(threads.get(i).getCallerIP()) && host.getPort().equals(threads.get(i).getCallerPort())) {
						present=true;
						break;
					}
				}
				
				if(present) {
					SIPHandlerThread existingThread = threads.get(i);
					existingThread.handleMessage(dataReceived);
				} 
				else{	
					SIPHandlerThread t = new SIPHandlerThread(callerIP, callerPort, serverSocket, dataReceived, rtpPort);
					threads.add(t);
					t.start();
					t.handleMessage(dataReceived);
					//hosts.add(host);
					rtpPort += 2; // Prepare for the next RTP Port
				}
			}
    	}
			catch (IOException e) {
				System.out.println("Exception caught in socket, probably malformed request: " + e.getMessage());
			}
	}
    	
	    public static void signalTransmissionComplete(String host, String port) {
			boolean present=false;
			int i;
			for(i=0;i<threads.size();i++)
			{
				if(host.equals(threads.get(i).getCallerIP()) && port.equals(threads.get(i).getCallerPort())) {
					present=true;
					break;
				}
			}
			
			if(present) {
				SIPHandlerThread existingThread = threads.get(i);
				existingThread.transmissionComplete();
			}
    	}
    
    private class SIPHandlerThread extends Thread {

    	private String dataReceived;
    	private String sip_interface;
    	private String sip_user;
    	private String callid;
    	private int sip_port;
    	private Timer okTimer;
    	private Timer byeTimer;

    	private DatagramSocket serverSocket;
    	private InetAddress callerIP;
    	private int callerPort;
    	private String rtpPort;
    	private String sessionID;
    	private boolean expectingTerminationAck;
    	private boolean expectingTerminationOk;
    	private int nextRtpPort;
    	
    	public SIPHandlerThread(InetAddress callerIP, int callerPort, DatagramSocket serverSocket, String dataReceived, int nextRtpPort) {
    		this.callerIP = callerIP;
    		this.callerPort = callerPort;
    		this.serverSocket = serverSocket;
    		this.dataReceived = dataReceived;
    		this.sip_user = SIPSpeaker.getProperties().getProperty("sip_user");
    		this.sip_interface = SIPSpeaker.getProperties().getProperty("sip_interface");
    		this.sip_port = Integer.parseInt(SIPSpeaker.getProperties().getProperty("sip_port"));
    		this.nextRtpPort = nextRtpPort;
    		expectingTerminationAck = false;
    		expectingTerminationOk = false;
    	}
    	
    	public void transmissionComplete() {
    		sendBye();
			expectingTerminationOk = true;
    	}
    	
    	public String getCallerIP() {
    		return callerIP.toString().substring(1);
    	}
    	
    	public String getCallerPort() {
    		return Integer.toString(callerPort);
    	}
    	
    	public String getVia() {
    		String via = "";
    		via=dataReceived.substring(dataReceived.indexOf("Via:"),dataReceived.indexOf("\n",dataReceived.indexOf("Via:"))+1);
        	via = via.replace("rport", "rport=" + callerPort + ";received=" + callerIP.toString().substring(1));
    		
    		return via;
    	}
    	
    	public String getCallSequence() {
    		return dataReceived.substring(dataReceived.indexOf("CSeq:"),dataReceived.indexOf("\n",dataReceived.indexOf("CSeq"))+1);
    	}
    	
    	public String getFrom() {
    		return dataReceived.substring(dataReceived.indexOf("From:"),dataReceived.indexOf("\n",dataReceived.indexOf("From:"))+1);
    	}
    	
    	public String getTo() {
    		return "To: \""+sip_user+"\"<sip:"+sip_interface+">;tag="+sessionID+"\r\n";
    	}
    	
    	public String getServer() {
    		return "Server: Mohit v1.0\r\n";
    	}
    	
    	public void sendPacket(String packet) {
    		DatagramPacket dPacket = new DatagramPacket(packet.getBytes(),packet.getBytes().length, callerIP, callerPort);
        	
			try {
				serverSocket.send(dPacket);
			}
			catch(IOException e) {
				e.printStackTrace();
			}
    	}
    	
    	public void sendOK() {
    		String okresponse = "";
			okresponse+="SIP/2.0 200 OK\r\n";
			okresponse+=getVia();
			okresponse+="Content-Length: 0\r\n";
			okresponse+=callid;
			okresponse+=getCallSequence();
			okresponse+=getFrom();
			okresponse+=getTo();
			okresponse+=getServer();
			okresponse+="\r\n";
			sendPacket(okresponse);
			System.out.println("Sent OK");
    	}
    	
    	public void sendRequestTerminated() {
    		String req = "";
    		req += "SIP/2.0 487 Request Terminated\r\n";
    		req += getVia();
    		req += "Content-Length: 0\r\n";
    		req += callid;
    		req += getCallSequence().replace("CANCEL", "INVITE");
    		req += getFrom();
    		req += getTo();
    		req += getServer();
    		req += "\r\n";
    		sendPacket(req);
    		System.out.println("Sent request terminated");
    		expectingTerminationAck = true;
    	}
    	
    	public void sendBadRequest() {
    		String error = "SIP/2.0 400 Bad Request\r\n";
        	error += getVia();
        	error += "Contact: <sip:"+sip_interface+":"+sip_port+">\r\n";
        	error += callid;
        	error += getFrom();
        	error += getTo();
        	error += getServer();
        	error += getCallSequence();
        	error += "Reason: Invalid user\r\n";
        	error += "Content-Length: 0\r\n";
        	error += "\r\n";
    	}
    	
    	public void sendBye() {
    		String via = "";
    		String req = "";
        	req += "BYE sip:"+callerIP.toString().substring(1)+":"+callerPort+" SIP/2.0\r\n";
        	via=getVia();
        	via=via.replace(callerIP.toString().substring(1), sip_interface);
        	via=via.substring(0,via.length()-4);
        	via+="22\r\n";
        	req += via;
        	req+="Max-Forwards: 70\r\n";
        	String from = getFrom();
        	from = from.replace("From", "To");
        	req+=from;
        	req+="From: \"unknown\"<sip:"+sip_interface+">;tag="+sessionID+"\r\n";
        	req+=callid;
        	req+="CSeq: 231 BYE\r\n";
        	req+="Content-Length: 0\r\n";
        	req+="User-Agent: SJPhone/1.60.299a/L (SJ Labs)\r\n";
        	req+="\r\n";
        	DatagramPacket sendBYE = new DatagramPacket(req.getBytes(),req.getBytes().length, callerIP, callerPort);
        	try {
				serverSocket.send(sendBYE);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			System.out.println("Sent bye");
    	}
    	
    	public void handleMessage(String message) {
    		dataReceived = message;
    		try {
	    		if(message.startsWith("INVITE")) {
	    			String response="";
	    			String to = "";
	                String from="";
	                String cseq="";
	                String trying="";
	                String okresponse="";
	                String server = getServer();
	                boolean validUser = false;
	                
	                // Validate User
	                String firstLine = dataReceived.substring(0, dataReceived.indexOf("\r\n"));
	                StringTokenizer tokenizer = new StringTokenizer(firstLine);
	                tokenizer.nextToken();
	                if(tokenizer.hasMoreTokens()) {
	                	String userString = tokenizer.nextToken();
	                	String userProp = SIPSpeaker.getProperties().getProperty("sip_user");
	                	String interfaceProp = SIPSpeaker.getProperties().getProperty("sip_interface");
	                	String portProp = SIPSpeaker.getProperties().getProperty("sip_port");
	                	if(userString.contains(";")) {
	                		userString = userString.substring(0, userString.indexOf(";"));
	                	}
	                	if(userString.substring(userString.indexOf(":")+1).contains(":")) {
	                		if(userString.equals("sip:" + userProp+"@"+interfaceProp+":"+portProp)) {
	                			validUser = true;
	                		}
	                	}
	                	else {
	                		System.out.println("Non-colon userstring = " + userString);
	                		if(userString.equals("sip:" + userProp+"@"+interfaceProp)) {
	                			validUser = true;
	                		}
	                	}
	                }
	                
	            	String sourcesdp=dataReceived.substring(dataReceived.indexOf("\r\n\r\n")+4,dataReceived.length());
	            	String sourceowner=dataReceived.substring(dataReceived.indexOf("o=")+4,dataReceived.indexOf("\n",dataReceived.indexOf("o=")));
	            	
	            	sessionID = UUID.randomUUID().toString();
	            	// Pull out RTP port
	            	int i = sourcesdp.indexOf("audio ");
	            	rtpPort = sourcesdp.substring(i+6, sourcesdp.indexOf(" ", i+6));
	            	System.out.println("Set RTP port to " + rtpPort);
	            	
	            	String sdp="";
	            	sdp+="v=0\r\n";
	            	sdp+="o=- "+sourceowner.substring(0,sourceowner.indexOf(" "))+" "+sourceowner.substring(0,sourceowner.indexOf(" "))+" IN IP4 " + sip_interface + "\r\n";
	            	sdp+="s=SJphone\r\n";
	            	sdp+="c=IN IP4 " + sip_interface + "\r\n";
	            	sdp+="t=0 0\r\n";
	            	sdp+="a=direction:active\r\n";
	            	sdp+="m=audio 49512 RTP/AVP 3 101\r\n";
	            	sdp+="a=rtpmap:3 GSM/800\r\n";
	            	sdp+="a=rtpmap:101 telephone-event/8000\r\n";
	            	sdp+="a=fmtp:101 0-11,16\r\n";
	            	
	            	to = getTo();
	            	trying+="SIP/2.0 100 Trying\r\n";
	            	response+="SIP/2.0 180 Ringing\r\n";
	            	okresponse+="SIP/2.0 200 OK\r\n";
	            	
	            	response+=getVia();
	            	trying+=getVia();
	            	okresponse+=getVia();
	            	from=getFrom();
	            	response+=from;
	            	trying+=from;
	            	okresponse+=from;
	            	response+=to;
	            	trying+=to;
	            	okresponse+=to;
	            	response+="Contact: <sip:>\r\n";
	            	okresponse+="Contact: <sip:"+sip_interface+":"+sip_port+">\r\n";
	            	callid=dataReceived.substring(dataReceived.indexOf("Call-ID:"),dataReceived.indexOf("\n",dataReceived.indexOf("Call-ID:"))+1);
	            	response+=callid;
	            	trying+=callid;
	            	okresponse+=callid;
	            	cseq=getCallSequence();
	            	response+=cseq;
	            	trying+=cseq;
	            	okresponse+=cseq;
	            	okresponse+="Content-Length: "+sdp.getBytes().length+"\r\n";
	            	okresponse+="Content-Type: application/sdp\r\n";
	            	response+="Content-Length: 0\r\n";
	            	trying+="Content-Length: 0\r\n";
	            	response+=server;
	            	okresponse+=server;
	            	okresponse+="\r\n";
	            	response+="\r\n";
	            	trying+=server;
	            	trying+="\r\n";
	            	
	            	okresponse+=sdp;
	            	
	            	DatagramPacket sendPacket = new DatagramPacket(response.getBytes(),response.getBytes().length, callerIP, callerPort);
	            	DatagramPacket sendTrying = new DatagramPacket(trying.getBytes(),trying.getBytes().length, callerIP, callerPort);
	            	
	            	try {
	            		serverSocket.send(sendTrying);
	            		if(validUser) {
	            			serverSocket.send(sendPacket);
	            		}
	            	}
	            	catch(IOException e) {
	            		e.printStackTrace();
	            	}
	            	
	            	System.out.println("Sent trying");
	        	
	            	if(!validUser) {
	                	String error = "SIP/2.0 404 Not Found\r\n";
	                	error += getVia();
	                	error += "Contact: <sip:"+sip_interface+":"+sip_port+">\r\n";
	                	error += callid;
	                	error += from;
	                	error += to;
	                	error += server;
	                	error += getCallSequence();
	                	error += "Reason: Invalid user\r\n";
	                	error += "Content-Length: 0\r\n";
	                	error += "\r\n";
	                	
	                	DatagramPacket send404 = new DatagramPacket(error.getBytes(),error.getBytes().length, callerIP, callerPort);
	                	
	                	try {
	                		serverSocket.send(send404);
	                	}
	                	catch(IOException e) {
	                		e.printStackTrace();
	                	}
	                	
	                	System.out.println("Sent 404");
	                	expectingTerminationAck = true;
	                	
	                	return;
	                }
	            	
	            	final String finalOkResponse = okresponse;
	            	ActionListener l = new ActionListener() {
	            		public void actionPerformed(ActionEvent e) {
	            			DatagramPacket sendOK = new DatagramPacket(finalOkResponse.getBytes(),finalOkResponse.getBytes().length, callerIP, callerPort);
	                    	
	            			try {
	            				serverSocket.send(sendOK);
	            			}
	            			catch(IOException ex) {
	            				ex.printStackTrace();
	            			}
	                    	
	            			System.out.println("Sent OK");
	      	          	}};
	            	okTimer = new javax.swing.Timer(5000, l);
	            	okTimer.setRepeats(false);
	            	okTimer.start();
	    		}
	    		else if(message.startsWith("ACK")) {
	    			if(expectingTerminationAck) {
	    				removeThisThread();
	    				expectingTerminationAck = false;
	    				return;
	    			}
	    			String via = "";
	    			String from = "";
	    			String callid = "";
   		
				// Determine how long we need to wait before hanging up (length of the audio file)
		        			File f = null;
		        	AudioInputStream ais = null;
		        	// Are we playing the "current" message or the default message
		        	if(SIPSpeaker.getProperties().getProperty("message_text").isEmpty()) {
		        		f = new File(SIPSpeaker.getProperties().getProperty("default_message"));
		        	}
		        	else {
		        		f = new File(SIPSpeaker.getProperties().getProperty("message_wav"));
		        	}
		        	
		        	try {
		        		ais = AudioSystem.getAudioInputStream(f);
		        	}
		        	catch(Exception e) {
		        		System.out.println("Error determining message length: " + e.getMessage());
		        	}
		        	
		        	AudioFormat format = ais.getFormat();
		    		long frames = ais.getFrameLength();
		    		double duration = (frames+0.0) / format.getFrameRate();
		    		
		    		new Thread(){
		    			public void run() {
		    				// Sleep for the length of the message plus two seconds, then hang up
				    		RTPSender s = null;
							try {
								System.out.println("Creating RTP sender with params: " + callerIP.toString().substring(1) + ", " + rtpPort + ", " + nextRtpPort);
								s = new RTPSender(callerIP.toString().substring(1), rtpPort, nextRtpPort, Integer.toString(callerPort));
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							s.startTransmission();
		    			}
		    		}.start();
	    		}
	    		else if(message.startsWith("SIP/2.0 200 OK")) {
	    			System.out.println("Received OK");
	    			if(expectingTerminationOk) {
	    				removeThisThread();
	    				expectingTerminationOk = false;
	    				return;
	    			}
	    		}
	    		else if(message.startsWith("CANCEL")) {
	    			System.out.println("CANCEL received");
	    			sendOK();
	    			sendRequestTerminated();
	    			if(okTimer != null) {
	    				if(okTimer.isRunning()) {
	    					okTimer.stop();
	    				}
	    			}
	    			if(byeTimer != null) {
	    				if(byeTimer.isRunning()) {
	    					byeTimer.stop();
	    				}
	    			}
	    		}
	    		else if(message.startsWith("BYE")) {
	    			// Send OK
	    			System.out.println("BYE received");
	    			sendOK();
	    			if(okTimer != null) {
	    				if(okTimer.isRunning()) {
	    					okTimer.stop();
	    				}
	    			}
	    			if(byeTimer != null) {
	    				if(byeTimer.isRunning()) {
	    					byeTimer.stop();
	    				}
	    			}
	    			removeThisThread();
	    		}
    		}
    		catch(Exception e) {
    			System.out.println("Bad request detected");
    			//sendBadRequest();
    		}
    	}
    	
    	public void removeThisThread() {
    		int i = 0;
    		for(i = 0; i < SIPHandler.threads.size(); i++) {
    			if(callerIP.toString().substring(1).equals(SIPHandler.threads.get(i).getCallerIP()) && (callerPort+"").equals(SIPHandler.threads.get(i).getCallerPort())) {
    				SIPHandler.threads.remove(i);
    				System.out.println("Removed thread");
    				break;
    			}
    		}
    	}
    	
		@Override
		public void run() {	
			System.out.println("New thread created");	
		}
	}
}
