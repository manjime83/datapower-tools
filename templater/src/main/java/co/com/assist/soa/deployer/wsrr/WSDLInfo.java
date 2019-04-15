package co.com.assist.soa.deployer.wsrr;

import java.util.List;

public class WSDLInfo {

	private String name;

	private String targetNamespace;

	private List<String> services;

	public WSDLInfo() {
		super();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTargetNamespace() {
		return targetNamespace;
	}

	public void setTargetNamespace(String targetNamespace) {
		this.targetNamespace = targetNamespace;
	}

	public List<String> getServices() {
		return services;
	}

	public void setServices(List<String> services) {
		this.services = services;
	}

}
