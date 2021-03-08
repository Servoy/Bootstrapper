package com.servoy.bootstrapper;

import java.awt.BorderLayout;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class BaseBootstrap {

	public static final String THREAD_POOL_SIZE_NAME = "bootstrapthreadpoolsize";

	public static final String BOOTSTRAPPER_HOME_DIR_PROPERTY = " jnlp.bootstrapper.home.dir";

	/**
	 * @param args
	 * @param solutionName
	 * @param codeBaseUrl
	 * @param threadPoolSize
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static void loadAndStartClient(String solutionName, URL codeBaseUrl, int threadPoolSize, String[] args)
			throws MalformedURLException, IOException, FileNotFoundException {
		System.out.println("loading solution: " + solutionName + " with code base: " + codeBaseUrl);
		URL jnlpUrl = new URL(codeBaseUrl, "servoy-client/" + solutionName + ".jnlp");

		final StringBuilder serverContent = new StringBuilder(4096);
		InputStream inputStream = jnlpUrl.openConnection().getInputStream();
		InputStreamReader isr = new InputStreamReader(inputStream);
		char[] buf;
		int read;
		try {
			buf = new char[4096];
			read = isr.read(buf);
			while (read != -1) {
				serverContent.append(buf, 0, read);
				read = isr.read(buf);
			}
		} finally {
			isr.close();
		}
		boolean isSame = false;
		StringBuilder clientContent = new StringBuilder();
		// load from cache
		String cacheName = (codeBaseUrl.getHost() + codeBaseUrl.getPort()).replace('.', '_');
		String libCacheProperty = System.getProperty("jnlp.bootstrapper.home.dir");
		final File libCacheDir;
		if (libCacheProperty != null) {
			libCacheDir = new File(libCacheProperty, "libCache/" + cacheName + "/"); //$NON-NLS-1$
		} else {
			libCacheDir = new File(System.getProperty("user.home"), ".servoy/libCache/" + cacheName + "/"); //$NON-NLS-1$
		}
		System.out.println("storing the cache in " + libCacheDir + " (" + libCacheProperty + ")");
		File mainJNLP = new File(libCacheDir, "main.jnlp");
		if (libCacheDir.exists() && mainJNLP.exists()) {
			FileReader fr = new FileReader(mainJNLP);
			try {
				read = fr.read(buf);
				while (read != -1) {
					clientContent.append(buf, 0, read);
					read = fr.read(buf);
				}
			} finally {
				fr.close();
			}
			int index = serverContent.indexOf("<resources>");
			int index2 = serverContent.lastIndexOf("</resources>");
			int index3 = clientContent.indexOf("<resources>");
			int index4 = clientContent.lastIndexOf("</resources>");
			isSame = index > 0 && index2 > 0 && index3 > 0 && index4 > 0
					&& serverContent.substring(index, index2).equals(clientContent.substring(index3, index4));
		} else {
			libCacheDir.mkdirs();
		}
		if (!isSame) {
			JFrame frame = new JFrame();
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setAlwaysOnTop(true);
			frame.setResizable(false);
			JProgressBar bar = new JProgressBar();
			bar.setMaximum(0);
			frame.getContentPane().setLayout(new BorderLayout());
			frame.getContentPane().add(bar, BorderLayout.CENTER);
			frame.setTitle("Loading resources from the server");
			frame.setSize(400, 60);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);

			System.out.println("The jnlp files resources are not the same, downloading it");
			ArrayList<File> files = new ArrayList<>();
			listFiles(libCacheDir, files);
			for (File file : files) {
				file.delete();
			}
			// Store the current timezone so that after download we can set it
			// back
			// Pack200 resets this to UTC when used multi threaded.
			TimeZone currentTimeZone = TimeZone.getDefault();
			ThreadPoolExecutor threadPool = new ThreadPoolExecutor(threadPoolSize > 1 ? threadPoolSize / 2 : 1,
					threadPoolSize, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
			threadPool.execute(new JNLPParser(jnlpUrl, threadPool, codeBaseUrl, libCacheDir, bar));
			do {
				try {
					Thread.sleep(4000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} while (threadPool.getActiveCount() + threadPool.getQueue().size() > 0);
			threadPool.shutdown();
			FileWriter fw = new FileWriter(mainJNLP);
			try {
				fw.write(serverContent.toString());
			} finally {
				fw.close();
			}
			frame.dispose();
			TimeZone.setDefault(currentTimeZone);
		} else {
			System.out.println("The jnlp files resources are the same, starting it from cache");
		}

		ArrayList<File> files = new ArrayList<>();
		listFiles(libCacheDir, files);

		ArrayList<URL> urls = new ArrayList<>();
		for (File file : files) {
			urls.add(file.toURI().toURL());
		}

		ClassLoader webstartClassLoader = BaseBootstrap.class.getClassLoader();
		final URLClassLoader classloader = new URLClassLoader(urls.toArray(new URL[urls.size()]), webstartClassLoader) {
			protected String findLibrary(String libname) {
				 String mapLibraryName = System.mapLibraryName(libname);
				 File lib = new File(libCacheDir, mapLibraryName);
				 if (lib.exists())
					try {
						return lib.getCanonicalPath();
					} catch (IOException e) {
						e.printStackTrace();
					}
				 return null;
			}
		};

		// replace all thread context classloader with the new url one
		Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
		for (Entry<Thread, StackTraceElement[]> stack : allStackTraces.entrySet()) {
			try {
				stack.getKey().setContextClassLoader(classloader);
			} catch (Throwable t) {
				// just ignore if the context classloader can't be set for this
				// one.
			}
		}
		try {
			Thread.currentThread().setContextClassLoader(classloader);
			Class<?> clz = Class.forName("com.servoy.j2db.smart.J2DBClient", true, classloader);
			Method method = clz.getMethod("main", String[].class);
			method.invoke(null, new Object[] { getArguments(serverContent.toString(), args) });
		} catch (Exception e) {
			e.printStackTrace();
		}
		final boolean[] contextOK = new boolean[1];
		final Runnable contextClassLoaderTester = new Runnable() {
			@Override
			public void run() {
				if (Thread.currentThread().getContextClassLoader() != classloader) {
					Thread.currentThread().setContextClassLoader(classloader);
					contextOK[0] = false;
				} else {
					contextOK[0] = true;
				}
			}
		};
		try {
			SwingUtilities.invokeAndWait(contextClassLoaderTester);
			do {
				try {
					Thread.sleep(1000);
					SwingUtilities.invokeAndWait(contextClassLoaderTester);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} while (!contextOK[0]);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static String[] getArguments(String contents, String[] args)
			throws SAXException, IOException, ParserConfigurationException {
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(new InputSource(new StringReader(contents)));
		NodeList argumentsList = doc.getElementsByTagName("argument");
		ArrayList<String> arguments = new ArrayList<>();
		for (int i = 0; i < argumentsList.getLength(); i++) {
			String textContent = argumentsList.item(i).getTextContent();
			if (textContent.startsWith(THREAD_POOL_SIZE_NAME))
				continue;
			arguments.add(textContent);
		}
		for (int i = 0; i < args.length; i++) {
			if (args[i].startsWith(THREAD_POOL_SIZE_NAME))
				continue;
			arguments.add(args[i]);
		}
		return arguments.toArray(new String[arguments.size()]);
	}

	static void listFiles(final File root, final List<File> files) {
		File[] listed = root.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if (pathname.isDirectory()) {
					listFiles(pathname, files);
					return false;
				}
				String upperCase = pathname.getName().toUpperCase();
				return upperCase.endsWith("JAR") || upperCase.endsWith("ZIP");
			}
		});
		if (listed != null) {
			for (File file : listed) {
				files.add(file);
			}
		}
	}

	public static void increaseMaximum(final JProgressBar bar) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				bar.setMaximum(bar.getMaximum() + 1);
			}
		});
	}

	public static void increaseProgress(final JProgressBar bar) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				bar.setValue(bar.getValue() + 1);
			}
		});
	}
}