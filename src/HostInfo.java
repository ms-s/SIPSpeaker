public class HostInfo {
	
	private String ip;
	private String port;
	
	public HostInfo(String ip, String port){
		this.setIp(ip);
		this.setPort(port);		
	}
	
	public void setPort(String port) {
		this.port = port;
	}
	
	public String getPort() {
		return port;
	}
	
	public void setIp(String ip) {
		this.ip = ip;
	}
	
	public String getIp() {
		return ip;
	}
}
