package co.com.assist.dp.templater;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

public class UpdateSources implements Runnable {

	public static void main(String[] args) {
		new UpdateSources().run();
	}

	@Override
	public void run() {
		File workspace = new File("/home/mjimenez/eclipse/workspaces/experian");
		File output = new File("assets/output");
		File[] directories = output.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);

		for (File directory : directories) {
			File project = new File(workspace, directory.getName());
			if (project.exists()) {
				try {
					FileUtils.copyDirectory(directory, project);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
