package co.com.assist.dp.templater;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

public class WDPJoiner {

	public static void main(String[] args) throws IOException, JDOMException {
		String wdpPathname = "C:\\Users\\manji\\Documents\\assist\\experian\\Experian.ImplementacionDataPower\\04.Ejecucion\\02.Fabrica\\03.Entregas\\release.20170523.1 (framework)\\wdp";
		new WDPJoiner().merge(wdpPathname);
	}

	private static final XMLOutputter prettyOutputter = new XMLOutputter(
			Format.getPrettyFormat().setOmitDeclaration(true).setIndent("\t"));

	private static final SAXBuilder saxBuilder = new SAXBuilder();

	public void merge(String wdpPathname) throws IOException, JDOMException {
		File wdp = new File(wdpPathname);

		File[] domains = wdp.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
		for (File domain : domains) {
			Element config = new Element("datapower-configuration");
			config.setAttribute("version", "3");

			Element configuration = new Element("configuration");
			configuration.setAttribute("domain", domain.getName());
			config.addContent(configuration);

			Element files = new Element("files");
			config.addContent(files);

			FileOutputStream fos = new FileOutputStream(new File(wdp, domain.getName() + ".zip"));
			ZipOutputStream outputZip = new ZipOutputStream(fos, Charset.forName("UTF-8"));

			Collection<File> exports = FileUtils.listFiles(domain, new String[] { "zip" }, false);
			for (File export : exports) {
				ZipFile inputZip = new ZipFile(export, Charset.forName("UTF-8"));
				Enumeration<? extends ZipEntry> entries = inputZip.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					if (entry.getName().equals("export.xml")) {
						Element exportElement = saxBuilder.build(inputZip.getInputStream(entry)).getRootElement();
						Element c = exportElement.getChild("configuration");
						if (c != null) {
							configuration.addContent(c.cloneContent());
						}
						Element f = exportElement.getChild("files");
						if (f != null) {
							files.addContent(f.cloneContent());
						}
					} else {
						try {
							outputZip.putNextEntry(entry);
							IOUtils.copy(inputZip.getInputStream(entry), outputZip);
							outputZip.closeEntry();
						} catch (ZipException e) {
							System.err.println(domain.getName() + "/" + export.getName() + ": " + e.toString());
						}
					}
				}
				inputZip.close();
			}

			String prettyConfig = prettyOutputter.outputString(config);

			ZipEntry entry = new ZipEntry("export.xml");
			outputZip.putNextEntry(entry);
			IOUtils.copy(new StringReader(prettyConfig), outputZip, Charset.forName("UTF-8"));
			outputZip.closeEntry();

			outputZip.close();
		}
	}

}
