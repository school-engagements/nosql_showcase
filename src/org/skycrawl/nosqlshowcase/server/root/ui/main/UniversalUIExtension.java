package org.skycrawl.nosqlshowcase.server.root.ui.main;

import java.util.logging.Level;

import org.skycrawl.nosqlshowcase.client.extensions.UniversalUIExtensionClientRpc;
import org.skycrawl.nosqlshowcase.client.extensions.UniversalUIExtensionServerRpc;
import org.skycrawl.nosqlshowcase.server.Logger;

import com.vaadin.server.AbstractExtension;
import com.vaadin.ui.UI;

/**
 * A special extension to {@link UI} that enables logging client issues on the
 * server and some other things.
 * 
 * @author SkyCrawl
 * @see {@link UniversalUIExtensionClientRpc}
 * @see {@link UniversalUIExtensionServerRpc}
 */
public class UniversalUIExtension extends AbstractExtension
{
	private static final long	serialVersionUID	= 8278201529558658998L;

	public UniversalUIExtension()
	{
		registerRpc(new UniversalUIExtensionServerRpc()
		{
			private static final long	serialVersionUID	= -5824200287684658506L;

			@Override
			public void logWarning(String message)
			{
				Logger.log(Level.WARNING, message);
			}

			@Override
			public void logThrowable(String message, String throwableStackTrace)
			{
				Logger.log(Level.SEVERE, String.format("%s\n%s", message, throwableStackTrace));
			}

			@Override
			public void logUncaughtNativeClientException()
			{
				Logger.log(Level.SEVERE, "An uncaught native client exception has been thrown. Best to launch a thorough debug.");
			}
		});
	}

	/**
	 * Exposing the inherited API.
	 */
	public void extend(UI anyUI)
	{
		super.extend(anyUI);
	}

	/**
	 * Access the client side features of this extension.
	 */
	public UniversalUIExtensionClientRpc getClientRPC()
	{
		return getRpcProxy(UniversalUIExtensionClientRpc.class);
	}
}
