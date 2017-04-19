package identical.hash;

import java.util.HashMap;
import java.util.Map;

public class Node {

	private String domain;

	private String ip;

	private Map<String, Object> data = new HashMap<String, Object>();

	public Node(String domain, String ip) {
		this.domain = domain;
		this.ip = ip;
	}

	public <T> void put(String key, T value) {
		data.put(key, value);
	}

	public void remove(String key) {
		data.remove(key);
	}

	public <T> T get(String key) {
		return (T) data.get(key);
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public void setData(Map<String, Object> data) {
		this.data = data;
	}
}