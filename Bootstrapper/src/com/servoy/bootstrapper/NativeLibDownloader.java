package com.servoy.bootstrapper;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.swing.JProgressBar;

public class NativeLibDownloader extends JarDownloader {

	public NativeLibDownloader(URL url, File solutionCacheDir, JProgressBar bar) {
		super(url, solutionCacheDir, bar);
		BaseBootstrap.increaseMaximum(bar);
	}

	public void run() {
		try {
			super.run();
			
			File file = new File(solutionCacheDir, url.getPath());
			if (file.exists()) {
				UnzipUtility.unzip(file.getCanonicalPath(), solutionCacheDir.getCanonicalPath());
				file.delete();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			BaseBootstrap.increaseProgress(bar);
		}
	}
}
