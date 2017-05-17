package co.com.assist.dp.tester;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.wss4j.common.WSEncryptionPart;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.apache.wss4j.dom.message.WSSecTimestamp;
import org.apache.wss4j.dom.message.WSSecUsernameToken;
import org.jdom2.Attribute;
import org.jdom2.Comment;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.ContentFilter;
import org.jdom2.input.DOMBuilder;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.DOMOutputter;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;

public class TestOperation {

	private static final Properties props = new Properties();

	static {
		try (InputStream is = ClassLoader.getSystemResourceAsStream("tester.xml")) {
			props.loadFromXML(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final XMLOutputter prettyOutputter = new XMLOutputter(
			Format.getPrettyFormat().setOmitDeclaration(true).setIndent("\t"));
	private static final XMLOutputter compactOutputter = new XMLOutputter(
			Format.getCompactFormat().setOmitDeclaration(true));

	private static final Namespace env = Namespace.getNamespace("env", "http://schemas.xmlsoap.org/soap/envelope/");
	private static final Namespace dp = Namespace.getNamespace("dp", "http://www.datapower.com/schemas/management");

	private static final DateFormat logDateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a", Locale.US);
	private static final DateFormat logFileDateFormat = new SimpleDateFormat("MMMyyyy", Locale.US);

	public static void main(String[] args) {
		args = Arrays.copyOf(FilenameUtils.normalizeNoEndSeparator(args[0], true).substring(1).split("/"), 4);
		System.out.println("args: " + Arrays.toString(args) + System.lineSeparator());

		TestOperation test = new TestOperation();

		String project = args[0];
		String type = args[1];
		String module = args[2];
		String object = args[3];

		if (type != null) {
			if (type.equals("src")) {
				test.importSource(project, module, object);
				test.executeTest(project, module, object);
			} else if (type.equals("test")) {
				test.executeTest(project, module, object);
			}
		}
	}

	private void importSource(String project, String module, String domain) {
		File bin;
		File src;
		try {
			bin = new File(project + File.separator + "bin").getCanonicalFile();
			src = new File(project + File.separator + "src").getCanonicalFile();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		String url = props.getProperty("dp.url");
		String username = props.getProperty("dp.username");
		String password = props.getProperty("dp.password");
		try {
			password = new String(Base64.decodeBase64(password), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		System.out.println("url: " + url);
		System.out.println("username: " + username);
		System.out.println();

		Map<String, String> headers = new HashMap<>();
		try {
			String basic = Base64.encodeBase64String((username + ":" + password).getBytes("UTF-8"));
			headers.put("Authorization", "Basic " + basic);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		File[] modules = src.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (module != null) {
					return file.isDirectory() && module.equals(file.getName());
				} else {
					return file.isDirectory();
				}
			}
		});

		for (File m : modules) {
			File[] domains = m.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					if (domain != null) {
						return file.isDirectory() && domain.equals(file.getName());
					} else {
						return file.isDirectory();
					}
				}
			});

			for (File d : domains) {
				byte[] bytes = zip(d);

				try {
					File export = new File(bin, d.getName() + File.separator + m.getName() + ".export.zip");
					FileUtils.writeByteArrayToFile(export, bytes);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				Document request = buildDoImportRequest(d.getName(), bytes);
				// Element requestElement = request.getRootElement().getChild("Body", env).getChild("request", dp);
				// System.out.println(prettyOutputter.outputString(requestElement));

				try {
					Document response = HttpClient.sendRequest(url, request, headers);
					Element importResultsElement = response.getRootElement().getChild("Body", env)
							.getChild("response", dp).getChild("import", dp).getChild("import-results")
							.setAttribute("module", m.getName());
					System.out.println(prettyOutputter.outputString(importResultsElement));
				} catch (Exception e) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					System.out.println(sw.toString());
				} finally {
					System.out.println("elapsed time: " + HttpClient.getElapsedTime());
					System.out.println();
				}
			}
		}
	}

	private byte[] zip(File source) {
		ByteArrayOutputStream zip = new ByteArrayOutputStream();
		ZipOutputStream zos = new ZipOutputStream(zip, Charset.forName("UTF-8"));

		Collection<File> files = FileUtils.listFiles(source, null, true);
		for (File file : files) {
			String name = FilenameUtils.separatorsToUnix(file.getPath().substring(source.getPath().length() + 1));
			ZipEntry zipEntry = new ZipEntry(name);
			try {
				zos.putNextEntry(zipEntry);
				IOUtils.copy(new FileInputStream(file), zos);
				zos.closeEntry();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		IOUtils.closeQuietly(zos);
		return zip.toByteArray();
	}

	public Document buildDoImportRequest(String domain, byte[] file) {
		Document document = new Document();

		Element envelope = new Element("Envelope", env);
		document.setRootElement(envelope);

		Element body = new Element("Body", env);
		envelope.addContent(body);

		Element request = new Element("request", dp);
		request.setAttribute(new Attribute("domain", domain));
		body.addContent(request);

		Element doImport = new Element("do-import", dp);
		doImport.setAttribute(new Attribute("source-type", "ZIP"));
		doImport.setAttribute(new Attribute("overwrite-objects", "true"));
		doImport.setAttribute(new Attribute("overwrite-files", "true"));
		request.addContent(doImport);

		Element inputfile = new Element("input-file", dp);
		inputfile.setText(Base64.encodeBase64String(file));
		doImport.addContent(inputfile);

		return document;
	}

	private void executeTest(String project, String module, String requestFileName) {
		File test;
		File log;
		try {
			test = new File(project + File.separator + "test").getCanonicalFile();
			log = new File(project + File.separator + "log").getCanonicalFile();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		File[] modules = test.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (module != null) {
					return file.isDirectory() && module.equals(file.getName());
				} else {
					return file.isDirectory();
				}
			}
		});

		if (modules == null) {
			return;
		}

		for (File m : modules) {
			File[] files = m.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					if (requestFileName != null) {
						return file.isFile() && file.getName().endsWith("request.xml")
								&& file.getName().equals(requestFileName);
					} else {
						return file.isFile() && file.getName().endsWith("request.xml");
					}
				}
			});

			for (File f : files) {
				Document request = null;
				try {
					request = new SAXBuilder().build(f);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}

				JsonObject object = getJsonObject(request);
				String url = object.get("url").asString();

				Map<String, String> headers = new HashMap<>();
				if (object.get("headers") != null) {
					JsonObject members = object.get("headers").asObject();
					for (Member member : members) {
						headers.put(member.getName(), member.getValue().asString());
					}
				}

				if (object.get("security") != null) {
					JsonObject members = object.get("security").asObject();
					if (members.get("signature").asBoolean()) {
						if (members.get("usernametoken") != null) {
							JsonArray usernametoken = members.get("usernametoken").asArray();
							request = sign(request, usernametoken.get(0).asString(), usernametoken.get(1).asString());
						} else {
							request = sign(request);
						}
					}
				}

				String prettyRequest = prettyOutputter.outputString(request);
				String compactRequest = compactOutputter.outputString(request);
				System.out.println(prettyRequest);

				String prettyResponse;
				String compactResponse;
				try {
					Document response = HttpClient.sendRequest(url, request, headers);
					prettyResponse = prettyOutputter.outputString(response);
					compactResponse = compactOutputter.outputString(response);
				} catch (Exception e) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					prettyResponse = sw.toString();
					compactResponse = e.toString();
				}

				System.out.println(prettyResponse);
				System.out.println("elapsed time: " + HttpClient.getElapsedTime());
				System.out.println();

				try {
					FileUtils.writeStringToFile(f, prettyRequest, "UTF-8");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				String outputName = FilenameUtils.removeExtension(FilenameUtils.removeExtension(f.getName()));
				File output = new File(m, outputName + ".response.xml");
				try {
					FileUtils.writeStringToFile(output, prettyResponse, "UTF-8");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				File logFile = new File(log,
						m.getName() + "." + logFileDateFormat.format(HttpClient.getResquestDate()) + ".log");
				StringBuilder sb = new StringBuilder();
				if (logFile.exists()) {
					sb.append(System.lineSeparator());
				}
				sb.append("<!-- ");
				sb.append("request date: ").append(logDateFormat.format(HttpClient.getResquestDate())).append(", ");
				sb.append("elapsed time: ").append(HttpClient.getElapsedTime());
				sb.append(" -->").append(System.lineSeparator());
				sb.append(compactRequest);
				sb.append(compactResponse);

				try {
					FileUtils.writeStringToFile(logFile, sb.toString(), "UTF-8", true);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private Document sign(Document request) {
		return sign(request, null, null);
	}

	private Document sign(Document request, String user, String password) {
		org.w3c.dom.Document document;
		try {
			document = new DOMOutputter().output(request);
		} catch (JDOMException e) {
			throw new RuntimeException(e);
		}

		WSSecHeader secHeader = new WSSecHeader(document);
		try {
			org.w3c.dom.Element securityHeader = secHeader.insertSecurityHeader();
			while (securityHeader.hasChildNodes()) {
				securityHeader.removeChild(securityHeader.getFirstChild());
			}
		} catch (WSSecurityException e) {
			throw new RuntimeException(e);
		}

		WSSecTimestamp timestamp = new WSSecTimestamp();
		timestamp.setTimeToLive(300);
		timestamp.setPrecisionInMilliSeconds(false);
		timestamp.build(document, secHeader);

		WSSecSignature signature = new WSSecSignature();
		signature.setUserInfo("assist.sign", "assist");
		signature.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
		signature.setSignatureAlgorithm(WSConstants.RSA_SHA1);
		signature.setSigCanonicalization(WSConstants.C14N_EXCL_OMIT_COMMENTS);
		signature.setDigestAlgo(WSConstants.SHA1);
		signature.setUseSingleCertificate(true);
		signature.getParts().add(new WSEncryptionPart("Timestamp",
				"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd", "Content"));
		if (user != null || password != null) {
			WSSecUsernameToken usernameToken = new WSSecUsernameToken();
			usernameToken.setPasswordType(WSConstants.PASSWORD_TEXT);
			usernameToken.setUserInfo(user, password);
			usernameToken.build(document, secHeader);

			signature.getParts().add(new WSEncryptionPart("UsernameToken",
					"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd", "Content"));
		}
		signature.getParts().add(new WSEncryptionPart("Body", "http://schemas.xmlsoap.org/soap/envelope/", "Content"));
		try {
			signature.build(document, CryptoFactory.getInstance(), secHeader);
		} catch (WSSecurityException e) {
			throw new RuntimeException(e);
		}

		return new DOMBuilder().build(document);
	}

	public JsonObject getJsonObject(Document document) {
		Comment comment = (Comment) document.getContent(new ContentFilter(ContentFilter.COMMENT)).iterator().next();
		return Json.parse(comment.getText()).asObject();
	}

}