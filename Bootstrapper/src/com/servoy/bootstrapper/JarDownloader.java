package com.servoy.bootstrapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;

import javax.swing.JProgressBar;

public class JarDownloader implements Runnable {

	private final URL url;
	private final File solutionCacheDir;
	private JProgressBar bar;

	public JarDownloader(URL url, File solutionCacheDir,JProgressBar bar) {
		this.url = url;
		this.solutionCacheDir = solutionCacheDir;
		this.bar = bar;
		Bootstrap.increaseMaximum(bar);
	}

	@Override
	public void run() {
		try {
			URLConnection connection = url.openConnection();
			connection.addRequestProperty("accept-encoding", "pack200-gzip");
			connection.addRequestProperty("content-type", "application/x-java-archive");
			InputStream inputStream = connection.getInputStream();

			File file = new File(solutionCacheDir, url.getPath());
			file.getParentFile().mkdirs();

			OutputStream os = new FileOutputStream(file);
			try {
				if (connection.getContentEncoding() != null
						&& connection.getContentEncoding().indexOf("pack200") != -1) {
					os = new JarOutputStream(os);
					((JarOutputStream)os).setLevel(0);
					inputStream = new GZIPInputStream(inputStream);
					Pack200.newUnpacker().unpack(inputStream, ((JarOutputStream)os));
				} else {
					if (connection.getContentEncoding() != null
							&& connection.getContentEncoding().indexOf("gzip") != -1) {
						inputStream = new GZIPInputStream(inputStream);
					}
					byte[] bytes = new byte[4096];
					int read = inputStream.read(bytes);
					while (read != -1) {
						os.write(bytes, 0, read);
						read = inputStream.read(bytes);
					}
				} 
			} finally {
				os.close();
				inputStream.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			Bootstrap.increaseProgress(bar);
		}
	}

}
