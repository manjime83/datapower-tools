package com.aossas.dp.deployer;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

public class Deploy {

	protected static Properties props = new Properties();

	private static byte[] key;

	private static final XMLOutputter prettyOutputter = new XMLOutputter(Format.getPrettyFormat().setOmitDeclaration(true).setIndent("\t"));

	private static final Namespace env = Namespace.getNamespace("env", "http://schemas.xmlsoap.org/soap/envelope/");
	private static final Namespace dp = Namespace.getNamespace("dp", "http://www.datapower.com/schemas/management");

	private static final DateFormat logDateFormat = new SimpleDateFormat("yyyyMMdd.HHmmss", Locale.US);

	public static void main(String[] args) {
		System.out.println("args: " + Arrays.toString(args) + System.lineSeparator());

		String project = args[0];
		String target = args[1];

		try (InputStream is = new FileInputStream(target + ".xml")) {
			props.loadFromXML(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			String aesKey = props.getProperty("aes.key");
			key = Hex.decodeHex(FileUtils.readFileToString(new File(aesKey), "UTF-8").toCharArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		Deploy deploy = new Deploy();
		deploy.deploy(project);
	}

	private void deploy(String project) {
		File idg;
		File log;
		try {
			idg = new File(project, "idg").getCanonicalFile();
			log = new File(project, "idglog." + logDateFormat.format(new Date(System.currentTimeMillis()))).getCanonicalFile();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		String url = props.getProperty("dp.url");
		String username = props.getProperty("dp.username");
		String password = decrypt(props.getProperty("dp.password")).trim();
		String deploymentPolicy = props.getProperty("dp.deployment.policy");
		int wait = Integer.parseInt(props.getProperty("dp.wait", "0")) * 1000;

		System.out.println("url: " + url);
		System.out.println("username: " + username);
		// System.out.println("password: " + password);
		System.out.println();

		Map<String, String> headers = new HashMap<>();
		try {
			String basic = Base64.encodeBase64String((username + ":" + password).getBytes("UTF-8"));
			headers.put("Authorization", "Basic " + basic);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}

		File[] domains = idg.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);

		for (File domain : domains) {
			String domainName = props.getProperty("domain.mapping." + domain.getName(), domain.getName());

			Collection<File> zips = FileUtils.listFiles(domain, new String[] { "zip" }, false);

			for (File zip : zips) {
				Collection<String> lines = new ArrayList<>();
				try {
					byte[] bytes = FileUtils.readFileToByteArray(zip);

					Document request = buildDoImportRequest(domainName, deploymentPolicy, bytes);
					// Element requestElement = request.getRootElement().getChild("Body", env).getChild("request", dp);
					// String importRequest = prettyOutputter.outputString(requestElement);
					// System.out.println(importRequest);
					// lines.add(importRequest);

					try {
						Document response = HttpClient.sendRequest(url, request, headers);
						Element responseElement = response.getRootElement().getChild("Body", env).getChild("response", dp);
						String importResults = prettyOutputter.outputString(responseElement);
						System.out.println(importResults);
						lines.add(importResults);
					} catch (Exception e) {
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						System.out.println(sw.toString());
						lines.add(sw.toString());
					} finally {
						System.out.println("elapsed time: " + HttpClient.getElapsedTime());
						lines.add("elapsed time: " + HttpClient.getElapsedTime());
						System.out.println();

						try {
							Thread.sleep(wait);
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				File importlog = new File(log + File.separator + domainName, FilenameUtils.getBaseName(zip.getName()) + ".log");
				try {
					FileUtils.writeLines(importlog, "UTF-8", lines);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	public Document buildDoImportRequest(String domain, String deploymentPolicy, byte[] file) {
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
		if (deploymentPolicy != null && !deploymentPolicy.isEmpty()) {
			doImport.setAttribute(new Attribute("deployment-policy", deploymentPolicy));
			doImport.setAttribute(new Attribute("deployment-policy-variables", deploymentPolicy));
		}
		request.addContent(doImport);

		Element inputfile = new Element("input-file", dp);
		inputfile.setText(Base64.encodeBase64String(file));
		doImport.addContent(inputfile);

		return document;
	}

	protected static String decrypt(String encrypted) {
		String data = "";
		try {
			SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
			byte[] decrypted = cipher.doFinal(Hex.decodeHex(encrypted.toCharArray()));
			data = new String(decrypted);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;
	}

	protected static String encrypt(String clearText) {
		String data = "";
		try {
			SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
			byte[] encrypted = cipher.doFinal(clearText.getBytes("UTF-8"));
			data = Hex.encodeHexString(encrypted);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;
	}

}