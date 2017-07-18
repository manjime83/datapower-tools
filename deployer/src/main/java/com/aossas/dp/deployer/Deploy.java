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

	private static final XMLOutputter prettyOutputter = new XMLOutputter(
			Format.getPrettyFormat().setOmitDeclaration(true).setIndent("\t"));

	private static final Namespace env = Namespace.getNamespace("env", "http://schemas.xmlsoap.org/soap/envelope/");
	private static final Namespace dp = Namespace.getNamespace("dp", "http://www.datapower.com/schemas/management");

	private static final DateFormat logDateFormat = new SimpleDateFormat("yyyyMMdd.HHmmss", Locale.US);

	public static void main(String[] args) {
		String profile = args.length > 0 ? args[0] : "deployer";

		try (InputStream is = new FileInputStream(profile + ".xml")) {
			props.loadFromXML(is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		String url = props.getProperty("aes.key");

		try {
			key = Hex.decodeHex(FileUtils.readFileToString(new File(url), "UTF-8").toCharArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		Deploy deploy = new Deploy();
		deploy.deploy();
	}

	private void deploy() {
		File wdp;
		File log;
		try {
			wdp = new File("wdp").getCanonicalFile();
			log = new File("wdplog." + logDateFormat.format(new Date(System.currentTimeMillis()))).getCanonicalFile();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		System.out.println(wdp);

		String url = props.getProperty("dp.url");
		String username = props.getProperty("dp.username");
		String password = decrypt(props.getProperty("dp.password"));

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

		File[] domains = wdp.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);

		for (File domain : domains) {
			Collection<File> zips = FileUtils.listFiles(domain, new String[] { "zip" }, false);

			for (File zip : zips) {
				Collection<String> lines = new ArrayList<>();
				try {
					byte[] bytes = FileUtils.readFileToByteArray(zip);

					Document request = buildDoImportRequest(
							props.getProperty("domain.mapping." + domain.getName(), domain.getName()), bytes);
					Element requestElement = request.getRootElement().getChild("Body", env).getChild("request", dp);
					String importRequest = prettyOutputter.outputString(requestElement);
					System.out.println(importRequest);
					lines.add(importRequest);

					try {
						Document response = HttpClient.sendRequest(url, request, headers);
						Element responseElement = response.getRootElement().getChild("Body", env).getChild("response",
								dp);
						String importResults = prettyOutputter.outputString(responseElement);
						System.out.println(importResults);
						lines.add(importResults);

						Thread.sleep(1000);
					} catch (Exception e) {
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						System.out.println(sw.toString());
						lines.add(sw.toString());
					} finally {
						System.out.println("elapsed time: " + HttpClient.getElapsedTime());
						lines.add("elapsed time: " + HttpClient.getElapsedTime());
						System.out.println();
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				File importlog = new File(log, FilenameUtils.getBaseName(zip.getName()) + ".log");
				try {
					FileUtils.writeLines(importlog, "UTF-8", lines);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
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

	protected static String decrypt(String encrypted) {
		String data = "";

		try {
			SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
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
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
			byte[] encrypted = cipher.doFinal(clearText.getBytes("UTF-8"));
			data = Hex.encodeHexString(encrypted);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return data;
	}

}