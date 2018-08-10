package com.servoy.bootstrapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;

import javax.swing.JProgressBar;

public class JarDownloader implements Runnable {

	protected final URL url;
	protected final File solutionCacheDir;
	protected final JProgressBar bar;

	public JarDownloader(URL url, File solutionCacheDir, JProgressBar bar) {
		this.url = url;
		this.solutionCacheDir = solutionCacheDir;
		this.bar = bar;
		BaseBootstrap.increaseMaximum(bar);
	}

	@Override
	public void run() {
		OutputStream os = null;
		InputStream inputStream = null;
		int downloadCounter = 0;
		try {
			File file = new File(solutionCacheDir, url.getPath());
			file.getParentFile().mkdirs();
			while (downloadCounter < 5) {
				try {
					os = new FileOutputStream(file);

					URLConnection connection = url.openConnection();
					connection.addRequestProperty("accept-encoding", "pack200-gzip");
					connection.addRequestProperty("content-type", "application/x-java-archive");
					inputStream = connection.getInputStream();

					if (connection.getContentEncoding() != null
							&& connection.getContentEncoding().indexOf("pack200") != -1) {
						os = new JarOutputStream(os);
						((JarOutputStream) os).setLevel(0);
						inputStream = new GZIPInputStream(inputStream);
						Pack200.newUnpacker().unpack(inputStream, ((JarOutputStream) os));
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
					downloadCounter = 5;
				} catch (Exception e) {
					e.printStackTrace();
					downloadCounter++;
					if (downloadCounter < 5) {
						try {
							System.err.println("trying to download again: " + url);
							Thread.sleep(2000*downloadCounter);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
					}
					else {
						System.err.println("After 5 download tries this url failed to download, contact server adminstrator: " + url);
					}
				} finally {
					if (os != null)
						try {
							os.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					if (inputStream != null)
						try {
							inputStream.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
				}
			}
		} finally {
			BaseBootstrap.increaseProgress(bar);
		}
	}

}
