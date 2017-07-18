package co.com.assist.dp.templater;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

public class UpdateSources implements Runnable {

	private File templaterOutput;

	public static void main(String[] args) {
		File templaterOutput = new File(args[0]);
		new UpdateSources(templaterOutput).run();
	}

	public UpdateSources(File templaterOutput) {
		this.templaterOutput = templaterOutput;
	}

	@Override
	public void run() {
		File workspace = templaterOutput.getParentFile().getParentFile();
		System.out.println("Updating sources in: " + workspace);
		
		File[] directories = templaterOutput.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);

		for (File directory : directories) {
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
