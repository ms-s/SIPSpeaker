import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.Processor;
import javax.media.control.TrackControl;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.rtp.RTPManager;
import javax.media.rtp.SendStream;
import javax.media.rtp.SessionAddress;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class RTPSender {

	 private String ip;
	 private int port;
	 private int rtpPort;
	 private String callerSipPort;
	 
	 private Processor processor = null;
	 private RTPManager manager;
	 private DataSource dataOutput = null;

	public RTPSender(String ip, String port, int rtpPort, String callerSipPort) throws IOException{
		this.ip = ip;
		this.port = Integer.parseInt(port);
		this.rtpPort = rtpPort;
		this.callerSipPort = callerSipPort;
	}
	
	public void startTransmission() {
		// Create processor
		System.out.println("Starting transmission");
		DataSource ds = null;
		// Check to see if a message exists
		String filename = "";
		String message_text = SIPSpeaker.getProperties().getProperty("message_text");
		if(message_text.isEmpty()) {
			filename = SIPSpeaker.getProperties().getProperty("default_message");
		}
		else {
			filename = SIPSpeaker.getProperties().getProperty("message_wav");
		}

		File f = new File(filename);
		SIPSpeaker.getReadLock().lock();
		System.out.println("Acquired lock");
		MediaLocator locator = new MediaLocator("file://" + f.getAbsolutePath());;
		
		try {
			ds = Manager.createDataSource(locator);
		}
		catch(Exception e) {
			System.out.println("Error creating data source: " + e.getMessage());
		}
		
		try {
			processor = Manager.createProcessor(ds);
		}
		catch(Exception e) {
			System.out.println("Error creating processor: " + e.getMessage());
		}
		
		// Configure the processor and wait for the state to update
		System.out.println("Configuring processor");
		processor.configure();
		
		while(processor.getState() != Processor.Configured) {
			try {
				Thread.sleep(50);
				System.out.println("Waiting for processor to configure...");
			}
			catch(InterruptedException e) {
				
			}
		}
		
		TrackControl[] controls = processor.getTrackControls();
		System.out.println("Number of controls: " + controls.length);
		ContentDescriptor cd = new ContentDescriptor(ContentDescriptor.RAW_RTP);
		processor.setContentDescriptor(cd);
		
		// Assuming only one track
		TrackControl tc = controls[0];
		Format[] formats = tc.getSupportedFormats();
		System.out.println("Supported formats: " + formats.length);
		int i = 0;
		for(; i < formats.length; i++) {
			if(formats[i].getEncoding().toString().equals("gsm/rtp"))
				break;
		}
		System.out.println("Using format : " + formats[i].toString());
		tc.setFormat(formats[i]);
		
		// Realize the processor and wait for the state to update
		System.out.println("Realizing processor");
		processor.realize();
		
		//TODO: Could this need to be Controller.Realized?
		while(processor.getState() != Processor.Realized) {
			try {
				Thread.sleep(20);
				//System.out.println("Waiting for processor to realize...");
			}
			catch(InterruptedException e) {
				
			}
		}
		
		dataOutput = processor.getDataOutput();
		// Create session
		SendStream sendStream;
		
		System.out.println("Establishing session");
		try {
			manager = RTPManager.newInstance();
			InetAddress inetAddr = InetAddress.getByName(ip);
			InetAddress localAddr = InetAddress.getByName(SIPSpeaker.getProperties().getProperty("sip_interface"));
			//System.setProperty("java.net.preferIPv6Addresses", "false");
			//System.setProperty("java.net.preferIPv4Stack", "true");
			SessionAddress local = new SessionAddress(localAddr, rtpPort);
			System.out.println("Local: " + local.toString());
			SessionAddress destination = new SessionAddress(inetAddr, port);
			manager.initialize(local);
			manager.addTarget(destination);
			System.out.println("Session created");
			// TODO: Safe to only assume one stream here?
			sendStream = manager.createSendStream(dataOutput, 0);
			sendStream.start();
		}
		catch(Exception e) {
			System.out.println("Error creating/starting the RTP session: " + e.getMessage());
		}
		
		System.out.println("Transmitting");
		processor.start();
		System.out.println("Processor started");
		double seconds = processor.getDuration().getSeconds();
		System.out.println("Sleeping for duration of " + seconds);
		try {
			Thread.sleep((long)(seconds+2)* 1000);
		}
		catch(InterruptedException e) {
			
		}
		stopTransmission();
		SIPSpeaker.getReadLock().unlock();
		System.out.println("Released lock");
		SIPHandler.signalTransmissionComplete(ip, callerSipPort);
	}
	
	public void stopTransmission() {
		System.out.println("Stopped RTP transmission");
		processor.stop();
		processor.close();
		manager.removeTargets("Session ended.");
		manager.dispose();
	}
}
