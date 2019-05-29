package co.com.assist.soa.deployer.wsrr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

public class LoadWSDL {

	private static final Namespace soap = Namespace.getNamespace("soap", "http://schemas.xmlsoap.org/soap/envelope/");
	private static final Namespace xsi = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
	private static final Namespace sdo = Namespace.getNamespace("sdo",
			"http://www.ibm.com/xmlns/prod/serviceregistry/6/0/ws/sdo");
	private static final Namespace com = Namespace.getNamespace("com", "commonj.sdo");
	private static final Namespace sdo1 = Namespace.getNamespace("sdo1",
			"http://www.ibm.com/xmlns/prod/serviceregistry/6/0/sdo");

	private static final XMLOutputter outputter = new XMLOutputter(
			Format.getPrettyFormat().setIndent("\t").setOmitDeclaration(true));

	public static void main(String[] args) throws Exception {
		String endpoint = "https://192.168.197.107:9443/WSRRCoreSDO/services/WSRRCoreSDOPort";
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Authorization", "Basic " + Base64.encodeBase64String("wasadmin:wasadmin".getBytes("UTF-8")));

		File wsdl = new File("C:\\Users\\manji\\Documents\\assist\\multibank\\workspace\\mb.template\\etc\\upload");
		Collection<File> files = FileUtils.listFiles(wsdl, new String[] { "wsdl" }, false);
		for (File f : files) {
			Document executeQuery = getExecuteQueryRequest(FilenameUtils.getName(f.getName()));
			System.out.println(outputter.outputString(executeQuery));
			headers.put("SOAPAction", "executeQuery");
			Document executeQueryResponse = HttpClient.sendRequest(endpoint, executeQuery, headers);
			System.out.println(outputter.outputString(executeQueryResponse));

			List<String> references = processExecuteQueryResponse(executeQueryResponse);
			for (String bsrURI : references) {
				Document delete = getDeleteRequest(bsrURI);
				System.out.println(outputter.outputString(delete));
				headers.put("SOAPAction", "delete");
				Document deleteResponse = HttpClient.sendRequest(endpoint, delete, headers);
				System.out.println(outputter.outputString(deleteResponse));
			}

			Document create = getCreateRequest(f);
			System.out.println(outputter.outputString(create));
			headers.put("SOAPAction", "create");
			Document createResponse = HttpClient.sendRequest(endpoint, create, headers);
			System.out.println(outputter.outputString(createResponse));

			FileUtils.deleteQuietly(f);
		}
	}

	public static Document getExecuteQueryRequest(String wsdlFile) {
		Document document = new Document();

		Element envelope = new Element("Envelope", soap);
		document.setRootElement(envelope);

		Element body = new Element("Body", soap);
		envelope.addContent(body);

		Element executeQuery = new Element("executeQuery", sdo);
		body.addContent(executeQuery);

		Element datagraph = new Element("datagraph", com);
		datagraph.addNamespaceDeclaration(com);
		executeQuery.addContent(datagraph);

		Element wsrr = new Element("WSRR", sdo1);
		wsrr.addNamespaceDeclaration(sdo1);
		datagraph.addContent(wsrr);

		Element root = new Element("root", sdo1);
		String rootUUID = "_" + UUID.randomUUID();
		root.setText(rootUUID);
		wsrr.addContent(root);

		Element genericObject = new Element("artefacts", sdo1);
		genericObject.addNamespaceDeclaration(xsi);
		genericObject.setAttribute(new Attribute("bsrURI", rootUUID));
		genericObject.setAttribute(new Attribute("type", "sdo1:GraphQuery", xsi));

		String queryExpression = "/WSRR/WSDLDocument[@name='" + wsdlFile + "']";
		genericObject.setAttribute(new Attribute("queryExpression", queryExpression));
		wsrr.addContent(genericObject);

		return document;
	}

	public static List<String> processExecuteQueryResponse(Document response) {
		XPathExpression<Element> xpath = XPathFactory.instance().compile(
				"/soap:Envelope/soap:Body/sdo:executeQueryResponse/results/com:datagraph", Filters.element(), null,
				soap, sdo, com);
		List<Element> datagraphs = xpath.evaluate(response);

		List<String> references = new ArrayList<String>();
		for (Element datagraph : datagraphs) {
			Element wsrr = datagraph.getChild("WSRR", sdo1);

			List<Element> artefacts = wsrr.getChildren("artefacts", sdo1);
			for (Element artefact : artefacts) {
				String bsrURI = artefact.getAttributeValue("bsrURI");
				references.add(bsrURI);
			}
		}
		return references;
	}

	private static Document getDeleteRequest(String bsrURI) {
		Document document = new Document();

		Element envelope = new Element("Envelope", soap);
		document.setRootElement(envelope);

		Element body = new Element("Body", soap);
		envelope.addContent(body);

		Element delete = new Element("delete", sdo);
		body.addContent(delete);

		Element bsrURIElement = new Element("bsrURI");
		bsrURIElement.setText(bsrURI);
		delete.addContent(bsrURIElement);

		return document;
	}

	public static Document getCreateRequest(File file) {
		Document document = new Document();

		Element envelope = new Element("Envelope", soap);
		document.setRootElement(envelope);

		Element body = new Element("Body", soap);
		envelope.addContent(body);

		Element create = new Element("create", sdo);
		create.addNamespaceDeclaration(com);
		create.addNamespaceDeclaration(sdo1);
		body.addContent(create);

		Element datagraph = new Element("datagraph", com);
		create.addContent(datagraph);

		Element wsrr = new Element("WSRR", sdo1);
		datagraph.addContent(wsrr);

		Element root = new Element("root", sdo1);
		String rootUUID = "_" + UUID.randomUUID();
		root.setText(rootUUID);
		wsrr.addContent(root);

		String content = "";
		try {
			byte[] byteArray = FileUtils.readFileToByteArray(file);
			content = Base64.encodeBase64String(byteArray);
		} catch (IOException e) {
			System.err.println(e);
		}

		Element artefacts = new Element("artefacts", sdo1);
		artefacts.setAttribute(new Attribute("bsrURI", rootUUID));
		artefacts.setAttribute(new Attribute("type", "sdo1:WSDLDocument", xsi));

		artefacts.setAttribute(new Attribute("name", FilenameUtils.getName(file.getName())));

		artefacts.setAttribute(new Attribute("location", file.getName()));
		artefacts.setAttribute(new Attribute("content", content));
		wsrr.addContent(artefacts);

		return document;
	}

}
