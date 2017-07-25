package co.com.assist.dp.templater;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang3.StringUtils;

public class UpdateSources implements Runnable {

	private File templaterOutput;
	private List<String> projects;

	public static void main(String[] args) {
		File templaterOutput = new File(args[0]);
		List<String> projects;
		if (args.length > 1) {
			projects = Arrays.asList(args[1].split(","));
		} else {
			projects = new ArrayList<>();
		}
		new UpdateSources(templaterOutput, projects).run();
	}

	public UpdateSources(File templaterOutput, List<String> projects) {
		this.templaterOutput = templaterOutput;
		this.projects = projects;
	}

	@Override
	public void run() {
		File workspace = templaterOutput.getParentFile().getParentFile();
		System.out.println("Updating sources in: " + workspace);
		if (projects != null && !projects.isEmpty()) {
			System.out.println("Updating projects in: " + StringUtils.join(projects, ", "));
		}

		File[] directories = templaterOutput.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);

		for (File directory : directories) {
			if (projects != null && !projects.isEmpty() && !projects.contains(directory.getName())) {
				continue;
			}

			File project = new File(workspace, directory.getName());
			if (project.exists()) {
				try {
					FileUtils.copyDirectory(directory, project);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				System.out.println("Destination folder not found: " + project.getPath());
			}
		}
	}

}
