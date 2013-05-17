import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.StringTokenizer;

/**
 * WebServer.java
 * 
 * HTTP Server
 * 
 * @author Mohit Sethi
 */

public class WebServer{
	
	public WebServer(int port) {
		
		try {			
			ServerSocket server = new ServerSocket(port);
			
			for(;;) {
				Socket socket = server.accept();
				new Thread(new WebServerThread(socket)).start();
			}
		}
		catch(IOException e) {
			System.out.println(e.getMessage());
		}
	}
	
	private class WebServerThread implements Runnable {
	
		private Socket socket;
		private BufferedReader reader;
		private BufferedWriter writer;
	
		public WebServerThread(Socket socket) {
			this.socket = socket;
			
			try {
				this.socket.setSoTimeout(1000); // Time out read()s after 1 second
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			}
			catch(IOException e) {
				System.out.println(e.getMessage());
			}
		}
	
		public void run() {
			try {
				processRequest(reader.readLine());
			}
			catch(IOException e) {
				System.out.println(e.getMessage());
			}
		}
		
		public void processRequest(String request) {
			BufferedReader fileReader = null;
			String httpResponse = "";
			String filename = "";
			
			try {
				StringTokenizer tokenizer = new StringTokenizer(request);
				String requestType = "";
				if(tokenizer.hasMoreTokens()) {
					requestType = tokenizer.nextToken();
				}
				else {
					sendmalformedhttp();
					return;
				}
				
				if(requestType.equals("GET")) {
					if(!tokenizer.hasMoreTokens()) {
						filename = "/";
					}
					else {
						filename = tokenizer.nextToken();
					}
					
					if(filename.equals("/")) {
						filename += "form.html";
					}
					
					// Handle message changes
					if(filename.contains("?message=")) {
						String decoded=URLDecoder.decode(filename, "UTF-8");
						int argIndex = decoded.indexOf("?message");
						String newMsg = decoded.substring(argIndex + 9);
						
						// If deleting message, just set the property (do not generate a new audio file)
						if(newMsg.isEmpty() || newMsg.replace(" ", "").isEmpty()) {
							SIPSpeaker.getProperties().setProperty("message_text", newMsg);
						}
						else {
							setCurrentMessage(newMsg);
						}
						filename = filename.substring(0, argIndex);
					}
					
					File f = new File(filename.substring(1));
					
					if(f.exists()) {
						httpResponse += "HTTP/1.1 200 OK\r\n";
						httpResponse += "Content-Type: text/html;charset=utf-8\r\n";
						httpResponse += "\r\n";
						
						String currentMessage = getCurrentMessage();
						if(currentMessage.isEmpty() || currentMessage.replace(" ", "").isEmpty()) {
							currentMessage = "Not configured, using default message";
						}
						String msgPage = "<html><head><title>SIP Speaker Message</title></head><body>" +
										 "<a href=\"form.html\">Refresh</a><br />" +
								         "<h1>The current message is: " + currentMessage  + 
								         "</h1><br /><form method=\"GET\" action=\"form.html\">" +
								         "<input type=\"text\" name=\"message\" /><br />" +
								         "<input type=\"submit\" value=\"Change Message\" />" +
								         "<br />To delete the message, change it to a blank message." + 
								         "</body></html>";
						FileWriter fw = new FileWriter(f);
						fw.write(msgPage);
						fw.flush();
						fw.close();
						fileReader = new BufferedReader(new FileReader(f));
						String filecontents = "";
						String line = "";
						while((line = fileReader.readLine()) != null) {
							filecontents += line;
						}
						httpResponse += filecontents + "\r\n";
					}
					else {
						httpResponse += "HTTP/1.1 404 Not Found\r\n";
						httpResponse += "Content-Type: text/html;charset=iso-8859-15\r\n";
						httpResponse += "\r\n";
						httpResponse += "<html><body>Page not found (Error 404)</html></body>\r\n";
					}
				}
						sendResponse(httpResponse);
			}
			catch(IOException e) {
				System.out.println(e.getMessage());
			}
		}
		
		public void sendResponse(String response) {
			try {
				writer.write(response);
				writer.flush();
				writer.close();
			} 
			catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
		
		private void sendmalformedhttp() {
			// Returning Malformed Status message
			String malformedHttpResponse="";
			malformedHttpResponse += "HTTP/1.1 400 Bad Request\r\n";
			malformedHttpResponse += "Content-Type: text/html;charset=iso-8859-15\r\n";
			malformedHttpResponse += "\r\n";
			malformedHttpResponse += "<html><body>Bad Request (Error 400)</body></html>\r\n";
			sendResponse(malformedHttpResponse);
		}
		
		private void setCurrentMessage(String message) {
			System.out.println("Acquiring lock");
			SIPSpeaker.getWriteLock().lock();
			AudioFileGenerator.generateWav(message);
			SIPSpeaker.getProperties().setProperty("message_text", message);
			System.out.println("Releasing lock");
			SIPSpeaker.getWriteLock().unlock();
		}
		
		private String getCurrentMessage() {
			return SIPSpeaker.getProperties().getProperty("message_text");
		}
	}
}
