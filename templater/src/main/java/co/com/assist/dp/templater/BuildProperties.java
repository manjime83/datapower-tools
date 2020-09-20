package co.com.assist.dp.templater;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;

public class BuildProperties {

	private static final Namespace wsdlns = Namespace.getNamespace("wsdl", "http://schemas.xmlsoap.org/wsdl/");
	private static final Namespace soapns = Namespace.getNamespace("soap", "http://schemas.xmlsoap.org/wsdl/soap/");

	public static void main(String[] args) throws Exception {
		File wsdl = new File("C:\\Users\\manji\\Documents\\aos\\multibank\\workspace\\mb.template\\etc\\wsdl");
		Collection<File> files = FileUtils.listFiles(wsdl, new String[] { "wsdl" }, false);
		for (File f : files) {
			System.out.println(f);
			processFile(f);
		}
	}

	private static void processFile(File f) throws JDOMException, IOException {
		Collection<String> lines = new ArrayList<String>();
		Document wsdl = new SAXBuilder().build(f);

		String wsdlName = f.getName();
		String targetNamespace = wsdl.getRootElement().getAttributeValue("targetNamespace");
		String name = targetNamespace.substring(29, targetNamespace.length() - 5).replaceAll("/", ".").toLowerCase();
		lines.add("name=" + name);
		lines.add("wsdl=" + wsdlName);
		lines.add("namespace=" + targetNamespace);

		String service_name = wsdl.getRootElement().getChild("service", wsdlns).getAttributeValue("name");
		lines.add("service_name=" + service_name);

		String port_name = wsdl.getRootElement().getChild("service", wsdlns).getChild("port", wsdlns)
				.getAttributeValue("name");
		lines.add("port_name=" + port_name);

		String location = wsdl.getRootElement().getChild("service", wsdlns).getChild("port", wsdlns)
				.getChild("address", soapns).getAttributeValue("location");
		String service_uri = new URL(location).getPath();
		lines.add("service_uri=" + service_uri);

		lines.add("port=" + 7843);

		// String portType_name = wsdl.getRootElement().getChild("portType",
		// wsdlns).getAttributeValue("name");
		// lines.add("portType_name=" + portType_name);

		File properties = new File("C:\\Users\\manji\\Documents\\aos\\multibank\\workspace\\mb.template\\input",
				name + ".properties");
		FileUtils.writeLines(properties, "UTF-8", lines);

		FileUtils.forceMkdir(new File(properties.getParentFile(), name + "/test/" + name));

	}

}
