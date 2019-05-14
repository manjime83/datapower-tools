package co.com.assist.dp.tester;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.wss4j.common.WSEncryptionPart;
import org.apache.wss4j.common.crypto.CryptoFactory;
import org.apache.wss4j.common.crypto.Merlin;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.apache.wss4j.dom.message.WSSecTimestamp;
import org.apache.wss4j.dom.message.WSSecUsernameToken;
import org.jdom2.Attribute;
import org.jdom2.Comment;
import org.jdom2.Content;
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
import org.w3c.dom.Node;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;

public class TestOperation {

	protected static final Properties props = new Properties();
	protected static byte[] key;

	private static final XMLOutputter prettyOutputter = new XMLOutputter(
			Format.getPrettyFormat().setOmitDeclaration(true).setIndent("\t"));
	private static final XMLOutputter compactOutputter = new XMLOutputter(
			Format.getCompactFormat().setOmitDeclaration(true));

	private static final Namespace env = Namespace.getNamespace("env", "http://schemas.xmlsoap.org/soap/envelope/");
	private static final Namespace dp = Namespace.getNamespace("dp", "http://www.datapower.com/schemas/management");

	private static final DateFormat logDateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a", Locale.US);
	private static final DateFormat logFileDateFormat = new SimpleDateFormat("MMMyyyy", Locale.US);
	private static final DateFormat resultsFileDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

	public static void main(String[] args) {
		try (InputStream is = new FileInputStream(args[0] + ".xml")) {
			props.loadFromXML(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			props.setProperty("aes.key", FileUtils.readFileToString(new File(props.getProperty("aes.key")), "UTF-8"));
			key = Hex.decodeHex(props.getProperty("aes.key").toCharArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		args[1] = new File(props.getProperty("workspace.path")).toURI().relativize(new File(args[1]).toURI()).getPath();

		args = Arrays.copyOf(FilenameUtils.normalizeNoEndSeparator(args[1], true).split("/"), 4);
		System.out.println("args: " + Arrays.toString(args) + System.lineSeparator());

		TestOperation test = new TestOperation();

		String project = args[0];
		String type = args[1];
		String module = args[2];
		String object = args[3];

		if (type != null) {
			if (type.equals("src")) {
				test.importSource(project, module, object);
				int waitBeforeTest = Integer.parseInt(props.getProperty("dp.waitBeforeTest", "0")) * 1000;
				try {
					Thread.sleep(waitBeforeTest);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				test.executeTest(project, module, null);
			} else if (type.equals("bin")) {
				test.buildBinary(project, module, object);
			} else if (type.equals("test")) {
				test.executeTest(project, module, object);
			}
		}

		HttpClient.close();
	}

	private void buildBinary(String project, String domain, String object) {
		File bin;
		File src;
		try {
			bin = new File(props.getProperty("workspace.path"), project + File.separator + "bin").getCanonicalFile();
			src = new File(props.getProperty("workspace.path"), project + File.separator + "src").getCanonicalFile();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		String module = FilenameUtils.getBaseName(FilenameUtils.getBaseName(object));
		System.out.println("Zipping module: " + module);

		byte[] bytes = zip(new File(src, module + File.separator + domain));

		try {
			File export = new File(bin, domain + File.separator + object);
			FileUtils.writeByteArrayToFile(export, bytes);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void importSource(String project, String module, String domain) {
		File bin;
		File src;
		try {
			bin = new File(props.getProperty("workspace.path"), project + File.separator + "bin").getCanonicalFile();
			src = new File(props.getProperty("workspace.path"), project + File.separator + "src").getCanonicalFile();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		String url = props.getProperty("dp.url");
		String username = props.getProperty("dp.username");
		String password = decrypt(props.getProperty("dp.password"));
		int waitBetweenImports = Integer.parseInt(props.getProperty("dp.waitBetweenImports", "0")) * 1000;

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

				Document request = buildDoImportRequest(props.getProperty("domain.mapping." + d.getName(), d.getName()),
						bytes);
				// Element requestElement = request.getRootElement().getChild("Body",
				// env).getChild("request", dp);
				// System.out.println(prettyOutputter.outputString(requestElement));

				try {
					Document response = HttpClient.sendRequest(url, request, headers);
					Element responseElement = response.getRootElement().getChild("Body", env).getChild("response", dp);

					String importResults;
					if (responseElement.getChildren().iterator().next().getName().equals("result")) {
						Element resultElement = responseElement.getChild("result", dp).setAttribute("module",
								m.getName());
						importResults = prettyOutputter.outputString(resultElement);
					} else {
						try {
							Element importResultsElement = responseElement.getChild("import", dp)
									.getChild("import-results").setAttribute("module", m.getName());
							importResults = prettyOutputter.outputString(importResultsElement);
						} catch (NullPointerException e) {
							importResults = prettyOutputter.outputString(responseElement);
						}
					}

					if (importResults.contains("Authentication failure")
							|| importResults.contains("status=\"missing-file\"")
							|| importResults.contains("status=\"missing-key\"")
							|| importResults.contains("result=\"ERROR\"")
							|| importResults.contains("status=\"ERROR\"")) {
						System.err.println(importResults);
						Thread.sleep(waitBetweenImports);
					} else {
						System.out.println(importResults);
					}
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
			test = new File(props.getProperty("workspace.path"), project + File.separator + "test").getCanonicalFile();
			log = new File(props.getProperty("workspace.path"), project + File.separator + "log").getCanonicalFile();
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
			File testsFile = new File(m, "_tester.xml");
			if (testsFile.exists()) {
				Properties testsProps = new Properties();
				try (InputStream is = new FileInputStream(testsFile)) {
					testsProps.loadFromXML(is);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				String url = testsProps.getProperty("url");

				Map<String, String> default_headers = new HashMap<>();
				if (testsProps.containsKey("default.headers")) {
					String[] default_pairs = testsProps.getProperty("default.headers").split(",");
					for (String pair : default_pairs) {
						String[] keyValue = pair.split("=");
						default_headers.put(keyValue[0], keyValue[1]);
					}
				}

				Boolean signature = Boolean.valueOf(testsProps.getProperty("security.signautre", "false"));
				String username = testsProps.getProperty("security.usernametoken.username");
				String password = testsProps.getProperty("security.usernametoken.password");

				File[] files = m.listFiles(new FileFilter() {
					@Override
					public boolean accept(File file) {
						if (requestFileName != null) {
							return file.isFile() && file.getName().endsWith(".xml")
									&& !file.getName().equals("_tester.xml") && file.getName().equals(requestFileName);
						} else {
							return file.isFile() && file.getName().endsWith(".xml")
									&& !file.getName().equals("_tester.xml");
						}
					}
				});

				for (File f : files) {
					String name = FilenameUtils.removeExtension(f.getName());

					Map<String, String> headers = new HashMap<>(default_headers);
					if (testsProps.containsKey("headers." + name)) {
						String[] pairs = testsProps.getProperty("headers." + name).split(",");
						for (String pair : pairs) {
							String[] keyValue = pair.split("=");
							headers.put(keyValue[0], keyValue[1]);
						}
					}

					Document request = null;
					try {
						StrSubstitutor substitutor = new StrSubstitutor(substitutionMap());
						String str = substitutor.replace(FileUtils.readFileToString(f, "UTF-8"));
						request = new SAXBuilder().build(new StringReader(str));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}

					if (signature) {
						request = sign(request, username, password);
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
						compactResponse = e.toString() + System.lineSeparator();
					}

					System.out.println(prettyResponse);
					System.out.println("elapsed time: " + HttpClient.getElapsedTime());
					System.out.println();

					try {
						File results = new File(m,
								"results." + resultsFileDateFormat.format(HttpClient.getResquestDate()));
						FileUtils.writeStringToFile(new File(results, name + ".request.xml"), prettyRequest, "UTF-8");
						FileUtils.writeStringToFile(new File(results, name + ".response.xml"), prettyResponse, "UTF-8");
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
			} else { // deprecated
				System.err.println("deprecated method of testing..." + System.lineSeparator());

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

					List<Content> comments = request.getContent(new ContentFilter(ContentFilter.COMMENT));
					JsonObject object = Json.parse(((Comment) comments.iterator().next()).getText()).asObject();
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
								request = sign(request, usernametoken.get(0).asString(),
										usernametoken.get(1).asString());
							} else {
								request = sign(request, null, null);
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
						compactResponse = e.toString() + System.lineSeparator();
					}

					System.out.println(prettyResponse);
					System.out.println("elapsed time: " + HttpClient.getElapsedTime());
					System.out.println();

					String outputName = FilenameUtils.removeExtension(FilenameUtils.removeExtension(f.getName()));
					try {
						FileUtils.writeStringToFile(f, prettyRequest, "UTF-8");
						FileUtils.writeStringToFile(new File(m, outputName + ".response.xml"), prettyResponse, "UTF-8");
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
	}

	private Map<String, String> substitutionMap() throws Exception {
		Map<String, String> map = new HashMap<String, String>();

		map.put("UUID", UUID.randomUUID().toString());
		map.put("hostAddress", Inet4Address.getLocalHost().getHostAddress());

		Date date = new Date();

		map.put("currentTimeMillis", Long.toString(date.getTime()));

		DateFormat dateDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		map.put("date", dateDateFormat.format(date));

		DateFormat timeDateFormat = new SimpleDateFormat("HH:mm:ss");
		map.put("time", timeDateFormat.format(date));

		map.put("dateTime", map.get("date") + "T" + map.get("time"));

		return map;
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

		String keystoreFile = props.getProperty("sign.keystore.file");
		String keystorePassword = decrypt(props.getProperty("sign.keystore.password"));
		String alias = props.getProperty("sign.keystore.alias");

		WSSecSignature signature = new WSSecSignature();
		signature.setUserInfo(alias, keystorePassword);
		signature.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
		signature.setSignatureAlgorithm(WSConstants.RSA_SHA1);
		signature.setSigCanonicalization(WSConstants.C14N_EXCL_OMIT_COMMENTS);
		signature.setDigestAlgo(WSConstants.SHA1);
		signature.setUseSingleCertificate(true);

		WSSecTimestamp timestamp = new WSSecTimestamp();
		timestamp.setTimeToLive(300);
		timestamp.setPrecisionInMilliSeconds(false);
		timestamp.build(document, secHeader);
		signature.getParts().add(new WSEncryptionPart(WSConstants.TIMESTAMP_TOKEN_LN, WSConstants.WSU_NS, ""));

		if (user != null && password != null) {
			WSSecUsernameToken usernameToken = new WSSecUsernameToken();
			usernameToken.setPasswordType(WSConstants.PASSWORD_TEXT);
			usernameToken.setUserInfo(user, password);
			usernameToken.build(document, secHeader);

			signature.getParts().add(new WSEncryptionPart(WSConstants.USERNAME_TOKEN_LN, WSConstants.WSSE_NS, ""));
		}

		signature.getParts().add(new WSEncryptionPart(WSConstants.ELEM_BODY, WSConstants.URI_SOAP11_ENV, ""));

		Properties crypto = new Properties();
		crypto.put("org.apache.wss4j.crypto.provider", Merlin.class.getName());
		crypto.put(Merlin.PREFIX + Merlin.KEYSTORE_FILE, keystoreFile);
		crypto.put(Merlin.PREFIX + Merlin.KEYSTORE_PASSWORD, keystorePassword);
		crypto.put(Merlin.PREFIX + Merlin.KEYSTORE_ALIAS, alias);

		try {
			signature.build(document, CryptoFactory.getInstance(crypto), secHeader);
			Node sv = signature.getSignatureElement().getElementsByTagNameNS(WSConstants.SIG_NS, "SignatureValue")
					.item(0);
			sv.setTextContent(sv.getTextContent().replaceAll("\\p{Cntrl}", ""));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return new DOMBuilder().build(document);
	}

	protected static String decrypt(String encrypted) {
		String data = "";

		try {
			SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
			byte[] decrypted = cipher.doFinal(Hex.decodeHex(encrypted.toCharArray()));
			data = new String(decrypted).trim();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return data;
	}

}
