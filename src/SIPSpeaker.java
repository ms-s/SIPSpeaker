import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class SIPSpeaker {

	private static Properties properties;
	private static ReadLock readLock;
	private static WriteLock writeLock;
	
	public SIPSpeaker(String[] args) {
		
		// Default values
		String default_message = "default.wav";
		String message_wav = "currentmessage.wav";
		String message_text = "Welcome to the SIP Speaker this is my own answering machine. You have no new messages.";
		String sip_interface = "127.0.0.1";
		String sip_port = "5060";
		String sip_user = "sipspeaker";
		String http_interface = "127.0.0.1";
		String http_port = "80";
		
		properties = new Properties();
		properties.setProperty("default_message", default_message);
		properties.setProperty("message_wav", message_wav);
		properties.setProperty("message_text", message_text);
		properties.setProperty("sip_interface", sip_interface);
		properties.setProperty("sip_port", sip_port);
		properties.setProperty("sip_user", sip_user);
		properties.setProperty("http_interface", http_interface);
		properties.setProperty("http_port", http_port);
		
		ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		readLock = lock.readLock();
		writeLock = lock.writeLock();
		validateArgs(args);
		generateWav();
		printProperties();
}
	
	public void generateWav() {
		String default_wav=SIPSpeaker.getProperties().getProperty("default_message");
		File f=new File(default_wav);
		if(!f.exists())
		{
			System.out.println("Acquiring lock");
			SIPSpeaker.getWriteLock().lock();
			AudioFileGenerator.generateWav("Welcome to SIP Speaker. This is the default message!",properties.getProperty("default_message"));
			System.out.println("Releasing lock");
			SIPSpeaker.getWriteLock().unlock();
		}
		
		String message = SIPSpeaker.getProperties().getProperty("message_text");
		if(!message.isEmpty() && !message.replace(" ", "").isEmpty()) {
			System.out.println("Acquiring lock");
			SIPSpeaker.getWriteLock().lock();
			AudioFileGenerator.generateWav(message);
			SIPSpeaker.getProperties().setProperty("message_text", message);
			System.out.println("Releasing lock");
			SIPSpeaker.getWriteLock().unlock();
		}
	}
	
	public void printProperties() {
		for(String s : properties.stringPropertyNames()) {
			System.out.println(s + " " + properties.getProperty(s));
		}
	}
	
	public void validateArgs(String[] args) {
		boolean configSpecified = false;
		// Check if the client has specified a config file.
		for(int i = 0; i < args.length; i++) {
			String arg = args[i];
				if(arg.equals("-c")) {
					configSpecified = true;
					if(args[i+1] != null) {
						String configFile =	args[i+1];
						File f = new File(configFile);
						if(f.exists()) {
							System.out.println("User-specified config file exists - loading properties");
							// Load settings from configuration file
							try {
								Properties configProperties = new Properties();
								configProperties.load(new FileInputStream(f));
								for(String s : configProperties.stringPropertyNames()) {
									String theProp = configProperties.getProperty(s);
									if(!theProp.isEmpty() && !theProp.replace(" ", "").isEmpty()) {
										properties.setProperty(s, theProp);
									}
								}
								break;
							}
							catch(IOException e) {
								System.out.println("Unable to load configuration file. Falling back to default configuration.");
							}
						}
						else {
							System.out.println("Specified configuration file does not exist.");
							System.out.println("Acquiring lock");
							SIPSpeaker.getWriteLock().lock();
							AudioFileGenerator.generateWav("Welcome to SIP Speaker. This is the default message!",properties.getProperty("default_message"));
							System.out.println("Releasing lock");
							SIPSpeaker.getWriteLock().unlock();
						}
					}
					else {
						System.out.println("Invalid usage of -c argument (no file specified). Falling back to default configuration.");
					}
			}
		}
		if(!configSpecified) {
			// If not, see if a default config file exists and load valid entries from it
			try {
				Properties configProperties = new Properties();
				File f = new File("default.properties");
				if(f.exists()) {
					configProperties.load(new FileInputStream(f));
					for(String s : configProperties.stringPropertyNames()) {
						String theProp = configProperties.getProperty(s);
						if(!theProp.isEmpty() && !theProp.replace(" ", "").isEmpty()) {
							properties.setProperty(s, theProp);
						}
					}
				}
			}
			catch(IOException e) {
				System.out.println("Unable to load default.properties (" + e.getMessage() + ")");
			}
		}
		
		for(int i = 0; i < args.length; i++) {
			String arg = args[i];
			if(arg.equals("-user")) {
				if(args[i+1] != null) {
					String user = args[i+1];
					int atIndex = user.indexOf("@");
					if(atIndex == -1) {
						System.out.println("Invalid formatting of user argument. Falling back to default configuration.");
					}
					else {
						int sip_port = 5060;
						String sip_user = user.substring(0, atIndex);
						
						if(sip_user.isEmpty() || sip_user.replace(" ", "").isEmpty()) {
							System.out.println("Invalid user. Falling back to default (sipspeaker)");
						}
						else {
							properties.setProperty("sip_user", sip_user);
						}
						
						String sip_interface = user.substring(atIndex+1);
						if(sip_interface.contains(":")) {
							int colonIndex = sip_interface.indexOf(":");
							properties.setProperty("sip_interface", sip_interface.substring(0, colonIndex));
							try {
								sip_port = Integer.parseInt(sip_interface.substring(colonIndex+1));
								if(sip_port <= 0 || sip_port >= 65535) {
									System.out.println("Invalid port entered. Falling back to default (5060).");
								}
								else {
									properties.setProperty("sip_port", Integer.toString(sip_port));
								}
							}
							catch(NumberFormatException e) {
								System.out.println("Invalid port entered. Falling back to default (5060).");	
							}
						}
						else {
							properties.setProperty("sip_interface", sip_interface);
						}
					}
					i++;
				}
				else {
					System.out.println("Invalid usage of -u argument (no user specified). Falling back to default.");
				}
			}
			else if(arg.equals("-http")) {
				if(args[i+1] != null) {
					String address = args[i+1];
					if(address.contains(":")) {
						int colonIndex = address.indexOf(":");
						String http_interface = address.substring(0, colonIndex);
						
						properties.setProperty("http_interface", http_interface);
						
						int port = 0;
						try {
							port = Integer.parseInt(address.substring(colonIndex+1));
							System.out.println("HTTP Port: " + port);
							if(port <= 0 || port > 65535) {
								System.out.println("Invalid HTTP port. Falling back to default.");
							}
							else {
								properties.setProperty("http_port", Integer.toString(port));
							}
						}
						catch(NumberFormatException e) {
							System.out.println("Invalid HTTP port. Falling back to default.");
						}
					}
					else {
						try {
							int port = Integer.parseInt(address);
							System.out.println("HTTP Port: " + port);
							if(port <= 0 || port > 65535) {
								System.out.println("Invalid HTTP port. Falling back to default.");
							}
							else {
								properties.setProperty("http_port", Integer.toString(port));
							}
						}
						catch(NumberFormatException e) {
							// Not a port. Assume IP/Hostname
							properties.setProperty("http_interface", address);
						}
					}
					i++;
				}
				else {
					System.out.println("Invalid usage of -http argument (no address/port specified");
				}
			}
			else {
				System.out.println("Usage: SIPSpeaker [-c config_file_name] [-user sip_uri] [-http http_bind_address]");
			}
		}
	}
	
	public static ReadLock getReadLock() {
		return readLock;
	}
	
	public static WriteLock getWriteLock() {
		return writeLock;
	}
	
	public static Properties getProperties() {
		return properties;
	}
	
	public static void main(String[] args) {
		
		SIPSpeaker s = new SIPSpeaker(args);
		new SIPHandler();
	}
}
