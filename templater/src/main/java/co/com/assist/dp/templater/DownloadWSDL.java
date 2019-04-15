package co.com.assist.dp.templater;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

public class DownloadWSDL {

	private static Properties properties = new Properties();

	static {
		InputStream is = DownloadWSDL.class.getResourceAsStream("download.properties");
		try {
			properties.load(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final XMLOutputter prettyOutputter = new XMLOutputter(Format.getPrettyFormat().setIndent("\t"));

	private static final Namespace wsdl = Namespace.getNamespace("wsdl", "http://schemas.xmlsoap.org/wsdl/");
	private static final Namespace wsdlsoap = Namespace.getNamespace("wsdlsoap",
			"http://schemas.xmlsoap.org/wsdl/soap/");

	private static XPathExpression<Element> xpathService = XPathFactory.instance()
			.compile("/wsdl:definitions/wsdl:service", Filters.element(), null, wsdl);
	private static XPathExpression<Element> xpathAddress = XPathFactory.instance().compile(
			"/wsdl:definitions/wsdl:service/wsdl:port/wsdlsoap:address", Filters.element(), null, wsdl, wsdlsoap);

	public static void main(String[] args) {
		try {
			doTrustToCertificates();
		} catch (Exception e) {
			e.printStackTrace();
		}

		File wsdl = new File("wsdl");

		Set<Entry<Object, Object>> entrySet = properties.entrySet();

		for (Entry<Object, Object> entry : entrySet) {
			String url = entry.getValue().toString();

			try {
				String contents = IOUtils.toString(new URL(url), "UTF-8");

				Document document = new SAXBuilder().build(new StringReader(contents));

				String serviceName = xpathService.evaluateFirst(document.getRootElement()).getAttributeValue("name");
				Element addressElement = xpathAddress.evaluateFirst(document.getRootElement());
				URL location = new URL(addressElement.getAttributeValue("location"));
				String[] locationArray = location.getPath().substring(1).split("/");

				String componente = locationArray[0];
				String servicio = locationArray[locationArray.length - 1];

				if (url.toLowerCase().endsWith("?wsdl")) {
					url = url.substring(0, url.length() - 5);
				}

				addressElement.setAttribute("location", url);

				contents = prettyOutputter.outputString(document);

				File outfolder = new File(wsdl, componente + "." + servicio);
				FileUtils.deleteQuietly(outfolder);
				File outfile = new File(outfolder, serviceName + ".wsdl");
				FileUtils.writeStringToFile(outfile, contents, "UTF-8");
				// System.out.println(componente + "." + servicio + "=" + url);
			} catch (Exception e) {
				System.err.println(e.toString() + " (" + entry.getKey() + ")");
			}
		}
	}

	public static void doTrustToCertificates() throws Exception {
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
				return;
			}

			public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
				return;
			}
		} };

		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		HostnameVerifier hv = new HostnameVerifier() {
			public boolean verify(String urlHostName, SSLSession session) {
				if (!urlHostName.equalsIgnoreCase(session.getPeerHost())) {
					System.out.println("Warning: URL host '" + urlHostName + "' is different to SSLSession host '"
							+ session.getPeerHost() + "'.");
				}
				return true;
			}
		};
		HttpsURLConnection.setDefaultHostnameVerifier(hv);
	}

}
