package co.com.assist.dp.templater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.text.StrSubstitutor;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

public class ApplyTemplate {

	public static void main(String[] args) throws IOException, TemplateException {
		File input = new File("assets", "input");
		File template = new File("assets", "template-client");
		File output = new File("assets", "output");
		FileUtils.deleteQuietly(output);

		Configuration cfg = new Configuration(Configuration.VERSION_2_3_26);
		cfg.setDirectoryForTemplateLoading(template);
		cfg.setDefaultEncoding("UTF-8");
		cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		cfg.setLogTemplateExceptions(false);

		Collection<File> templateFileList = FileUtils.listFiles(template, null, true);
		Collection<String> templateNameList = new ArrayList<>(templateFileList.size());
		int len = template.getPath().length() + 1;
		for (File templateFile : templateFileList) {
			templateNameList.add(FilenameUtils.normalize(templateFile.getPath().substring(len), true));
		}

		Collection<File> propertiesFileList = FileUtils.listFiles(input, new String[] { "properties" }, false);
		for (File propertiesFile : propertiesFileList) {
			String name = FilenameUtils.getBaseName(propertiesFile.getName());

			Properties properties = new Properties();
			properties.load(new FileInputStream(propertiesFile));
			Set<Entry<Object, Object>> entrySet = properties.entrySet();

			Map<String, String> dataModel = new HashMap<String, String>(properties.size());
			for (Entry<Object, Object> entry : entrySet) {
				dataModel.put(entry.getKey().toString(), entry.getValue().toString());
			}

			StrSubstitutor substitutor = new StrSubstitutor(dataModel);

			for (String templateName : templateNameList) {
				File out = FileUtils.getFile(output, name, substitutor.replace(templateName));
				FileUtils.forceMkdirParent(out);
				cfg.getTemplate(templateName).process(dataModel, new FileWriter(out));
			}

			File staticFiles = new File(input, name);
			if (staticFiles.isDirectory()) {
				FileUtils.copyDirectory(staticFiles, new File(output, name));
			}
		}

		new UpdateSources().run();
	}

}