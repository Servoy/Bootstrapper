package com.servoy.bootstrapper;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ThreadPoolExecutor;

import javax.swing.JProgressBar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class JNLPParser implements Runnable {

	private final URL url;
	private final ThreadPoolExecutor threadPool;
	private final URL codeBase;
	private final File solutionCacheDir;
	private final JProgressBar bar;

	public JNLPParser(URL url, ThreadPoolExecutor threadPool, URL codeBase, File solutionCacheDir, JProgressBar bar) {
		this.url = url;
		this.threadPool = threadPool;
		this.codeBase = codeBase;
		this.solutionCacheDir = solutionCacheDir;
		this.bar = bar;
		Bootstrap.increaseMaximum(bar);
	}

	@Override
	public void run() {

		InputStreamReader isr = null;
		Document jnlpDoc = null;
		int downloadCounter = 0;
		try {
			while (downloadCounter < 5) {
				try {
					InputStream inputStream = url.openConnection().getInputStream();
					isr = new InputStreamReader(inputStream);
					DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
					DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
					jnlpDoc = docBuilder.parse(new InputSource(isr));
					try {
						isr.close();
						isr = null;
					} catch (Exception e) {
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
					if (isr != null)
						try {
							isr.close();
						} catch (Exception e) {
						}
				}
			}
			NodeList childNodes = jnlpDoc.getChildNodes();
			parseJNLP(childNodes);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} finally {
			Bootstrap.increaseProgress(bar);
		}
	}

	/**
	 * @param childNodes
	 * @throws MalformedURLException
	 */
	private void parseJNLP(NodeList childNodes) throws MalformedURLException {
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			NamedNodeMap attributes = node.getAttributes();
			if (attributes != null) {
				Node href = attributes.getNamedItem("href");
				if (href != null) {
					if (node.getNodeName().equalsIgnoreCase("jar") || node.getNodeName().equalsIgnoreCase("zip")) {
						threadPool.execute(
								new JarDownloader(new URL(codeBase, href.getNodeValue()), solutionCacheDir, bar));
					} else if (node.getNodeName().equalsIgnoreCase("extension")) {
						threadPool.execute(new JNLPParser(new URL(codeBase, href.getNodeValue()), threadPool, codeBase,
								solutionCacheDir, bar));
					}
				}
			}
			if ("resources".equals(node.getNodeName()) && node.getAttributes() != null
					&& (node.getAttributes().getNamedItem("os") != null
							|| node.getAttributes().getNamedItem("arch") != null)) {
				StringWriter buffer = new StringWriter();
				try {
					TransformerFactory transFactory = TransformerFactory.newInstance();
					Transformer transformer = transFactory.newTransformer();
					transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
					transformer.transform(new DOMSource(node), new StreamResult(buffer));
				} catch (Exception e) {
					e.printStackTrace();
				}
				System.err.println(
						"skipping resource node that is os/architecture specific, should be added to bootstrap.jnlp:\n"
								+ buffer);
			} else
				parseJNLP(node.getChildNodes());
		}
	}

}
