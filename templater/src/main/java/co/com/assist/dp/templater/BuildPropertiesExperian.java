package co.com.assist.dp.templater;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;

public class BuildPropertiesExperian {

	private static final Namespace wsdlns = Namespace.getNamespace("wsdl", "http://schemas.xmlsoap.org/wsdl/");
	private static final Namespace soapns = Namespace.getNamespace("soap", "http://schemas.xmlsoap.org/wsdl/soap/");

	public static void main(String[] args) throws Exception {
		File wsdl = new File("C:\\Users\\manji\\workspaces\\experian\\utils\\wsdl");
		Collection<File> files = FileUtils.listFiles(wsdl, new String[] { "wsdl" }, true);
		for (File f : files) {
			processFile(f);
		}
	}

	private static void processFile(File f) throws JDOMException, IOException {
		Collection<String> lines = new ArrayList<String>();
		Document wsdl = new SAXBuilder().build(f);

		String wsdlName = f.getName();
		lines.add("wsdl=" + wsdlName);

		String portType_name = wsdl.getRootElement().getChild("portType", wsdlns).getAttributeValue("name");
		lines.add("portType_name=" + portType_name);

		String service_name = wsdl.getRootElement().getChild("service", wsdlns).getAttributeValue("name");
		lines.add("service_name=" + service_name);

		String port_name = wsdl.getRootElement().getChild("service", wsdlns).getChild("port", wsdlns)
				.getAttributeValue("name");
		lines.add("port_name=" + port_name);

		String location = wsdl.getRootElement().getChild("service", wsdlns).getChild("port", wsdlns)
				.getChild("address", soapns).getAttributeValue("location");
		URL uri = new URL(location);
		lines.add("uri=" + uri.getPath());

		String targetNamespace = wsdl.getRootElement().getAttributeValue("targetNamespace");
		lines.add("namespace=" + targetNamespace);

		String[] locationArray = uri.getPath().substring(1).split("/");
		String componente = locationArray[0];
		String servicio = locationArray[locationArray.length - 1];

		String project_name = componente + "." + servicio;
		lines.add("project_name=" + project_name);

		Set<String> operations = new LinkedHashSet<String>();
		List<Element> operationList = wsdl.getRootElement().getChild("portType", wsdlns).getChildren("operation",
				wsdlns);
		for (Element operation : operationList) {
			String operationName = operation.getAttributeValue("name");
			operations.add(operationName);
		}
		lines.add("operations=" + String.join(",", operations));

		String balancer;
		int port;
		if (uri.getHost().equals("172.24.14.62")) {
			balancer = "jboss.lbg";
			port = 80;
		} else if (uri.getHost().equals("172.24.14.129")) {
			balancer = "tomcat.lbg";
			port = uri.getPort();
		} else {
			System.out.println(uri.getHost() + " " + uri.getPort());
			balancer = "";
			port = 0;
		}
		lines.add("balancer=" + balancer);
		lines.add("port=" + port);

		File properties = new File("assets/input", project_name + ".properties");
		FileUtils.writeLines(properties, "UTF-8", lines);

		String url = uri.getProtocol() + "://" + balancer + ":" + port + uri.getPath();

		createResources(f, project_name, service_name, url, operations);
	}

	private static void createResources(File f, String project_name, String service_name, String url,
			Set<String> operations) throws IOException {
		File output = new File("assets/input", project_name);
		if (output.exists()) {
			FileUtils.cleanDirectory(output);
		}

		File wsdl1 = new File(output, "src/002." + project_name + "/secure-gateway/local/wsdl");
		FileUtils.copyDirectoryToDirectory(f.getParentFile(), wsdl1);

		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		sb.append("<route>\r\n");
		sb.append("\t<url>").append(url).append("</url>\r\n");
		sb.append("</route>");
		String xml = sb.toString();

		for (String operation : operations) {
			File routes = new File(output, "src/002." + project_name + "/secure-gateway/local/routes/" + project_name
					+ "/" + service_name + "/" + operation + ".route.xml");
			FileUtils.writeStringToFile(routes, xml, "UTF-8");
		}

		sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
		sb.append("<aaa:AAAInfo xmlns:aaa=\"http://www.datapower.com/AAAInfo\">\r\n");
		sb.append("\t<aaa:FormatVersion>1</aaa:FormatVersion>\r\n");
		sb.append("\t<aaa:Authenticate>\r\n");
		sb.append("\t\t<aaa:IPNetwork>0.0.0.0/0</aaa:IPNetwork>\r\n");
		sb.append("\t\t<aaa:OutputCredential>all</aaa:OutputCredential>\r\n");
		sb.append("\t</aaa:Authenticate>\r\n");
		sb.append("</aaa:AAAInfo>");
		xml = sb.toString();

		File aaa = new File(output, "src/002." + project_name + "/secure-gateway/local/aaa/" + project_name + "/"
				+ service_name + ".aaa.xml");
		FileUtils.writeStringToFile(aaa, xml, "UTF-8");

	}

}
