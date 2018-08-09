package com.servoy.bootstrapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.jnlp.UnavailableServiceException;

public class Bootstrap extends BaseBootstrap {

	public static void main(String[] args) throws UnavailableServiceException, IOException, URISyntaxException {
		String solutionName = args.length > 0 ? args[0] : "servoy_client"; // coming
																			// from
																			// args
																			// else
																			// default.
		System.setSecurityManager(null);
		javax.jnlp.BasicService bs = (javax.jnlp.BasicService) javax.jnlp.ServiceManager
				.lookup("javax.jnlp.BasicService"); //$NON-NLS-1$
		URL codeBaseUrl = bs.getCodeBase();
		// URL codeBaseUrl = new URL("http://localhost:8080");
		int threadPoolSize = getThreadPoolSize(args);
		loadAndStartClient( solutionName, codeBaseUrl,threadPoolSize,args);
	}
	
	private static int getThreadPoolSize(String[] args) {
		for (int i = 1; i < args.length; i++) {
			if (args[i].startsWith(THREAD_POOL_SIZE_NAME)) {
				try {
					int val = Integer.parseInt(args[i].substring(THREAD_POOL_SIZE_NAME.length()+1));
					if (val > 0)
						return val;
					System.out.println("Thread pool size must be a positive number, using default value: 8.");
				} catch (NumberFormatException e) {
					System.out.println("Thread pool size argument is not a number, using default value: 8.");
				}
			}
	
		}
		return 8;
	}

}
