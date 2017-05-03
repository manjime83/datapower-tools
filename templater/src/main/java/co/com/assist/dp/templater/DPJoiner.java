package co.com.assist.dp.templater;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

public class DPJoiner {

	public static void main(String[] args) throws JDOMException, IOException {
		SAXBuilder builder = new SAXBuilder();

		XMLOutputter prettyOutputter = new XMLOutputter(
				Format.getPrettyFormat().setOmitDeclaration(true).setIndent("\t"));

		String project = "bgeneral.commons";
		// String project = "bgeneral.services";

		File input = new File("C:\\manujimenez\\workspaces\\bgeneral\\" + project + "\\src");
		File output = new File("C:\\manujimenez\\workspaces\\bgeneral\\bgeneral.services.all\\src\\" + project);
		FileUtils.deleteQuietly(output);

		Map<String, Element> dpConfigurations = new HashMap<String, Element>();

		File[] modules = input.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
		for (File module : modules) {
			File[] domains = module.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
			for (File domain : domains) {
				if (!dpConfigurations.containsKey(domain.getName())) {
					Element dpConfiguration = new Element("datapower-configuration");
					dpConfiguration.setAttribute("version", "3");

					Element configuration = new Element("configuration");
					configuration.setAttribute("domain", domain.getName());
					dpConfiguration.addContent(configuration);

					Element files = new Element("files");
					dpConfiguration.addContent(files);

					dpConfigurations.put(domain.getName(), dpConfiguration);
				}

				Element export = builder.build(new File(domain, "export.xml")).getRootElement();
				Element c = export.getChild("configuration");
				if (c != null) {
					dpConfigurations.get(domain.getName()).getChild("configuration").addContent(c.cloneContent());
				}
				Element f = export.getChild("files");
				if (f != null) {
					dpConfigurations.get(domain.getName()).getChild("files").addContent(f.cloneContent());
				}

				FileUtils.copyDirectoryToDirectory(domain, output);
			}
		}

		Set<Entry<String, Element>> entrySet = dpConfigurations.entrySet();
		for (Entry<String, Element> entry : entrySet) {
			String prettyExport = prettyOutputter.outputString(entry.getValue());
			FileUtils.writeStringToFile(new File(output, entry.getKey() + "/export.xml"), prettyExport, "UTF-8");
		}
	}

}
