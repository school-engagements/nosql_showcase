package org.skycrawl.nosqlshowcase.server.root.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.skycrawl.nosqlshowcase.server.Logger;
import org.skycrawl.nosqlshowcase.server.ServletResources;
import org.skycrawl.nosqlshowcase.server.root.common.db.AbstractDataController;
import org.skycrawl.nosqlshowcase.server.root.common.db.AbstractDatabaseConnection;
import org.skycrawl.nosqlshowcase.server.root.common.db.DatabaseHandle;
import org.skycrawl.nosqlshowcase.server.root.common.sample.SampleLoader;
import org.skycrawl.nosqlshowcase.server.root.ui.dialogs.DialogCommons.IDialogResultHandler;
import org.skycrawl.nosqlshowcase.server.root.ui.dialogs.GeneralDialogs;
import org.skycrawl.nosqlshowcase.server.root.ui.notifications.MyNotifications;
import org.skycrawl.nosqlshowcase.server.root.ui.util.IMenuContext;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.vaadin.event.MouseEvents.ClickEvent;
import com.vaadin.event.MouseEvents.ClickListener;
import com.vaadin.server.ErrorMessage;
import com.vaadin.server.ThemeResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.Label;
import com.vaadin.ui.MenuBar;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.VerticalLayout;

import de.steinwedel.messagebox.Icon;
import de.steinwedel.messagebox.MessageBox;

public abstract class AbstractDatabaseUI<C extends Object, DC extends AbstractDataController<C>, CA extends AbstractDatabaseConnection<C,DC>> extends AbstractConfiguredUI
{
	private static final long	serialVersionUID	= -1397955805512266993L;
	
	private DatabaseHandle<CA> databaseHandle = null;
	
	public DatabaseHandle<CA> getDatabaseHandle()
	{
		return databaseHandle;
	}
	
	public CA getConnectionHandle()
	{
		return getDatabaseHandle().getConnectionHandle();
	}
	
	public C getConnection()
	{
		return getConnectionHandle().getConnection();
	}
	
	public DC getDataController()
	{
		return getConnectionHandle().getDataController();
	} 
	
	//---------------------------------------------------------------
	// UI BUILDING INTERFACE

	@Override
	protected void buildUI()
	{
		// first and foremost:
		addStyleName("a-db-ui");
		
		// and begin doing the designated stuff
		databaseHandle = AbstractConfiguredUIServlet.getCurrentDatabaseInfo();
		try
		{
			// first establish connection to database
			if(!getDatabaseHandle().isConnectionDefined())
			{
				throw new IllegalStateException();
			}
		}
		catch (Exception e)
		{
			ensureHealthyOrNewConnection();
			// doBuildUI(); // only for testing
			return;
		}
		
		// if connection is already established successfully
		doBuildUI();
	}
	
	private void ensureHealthyOrNewConnection()
	{
		// set some content so that exceptions don't make the dialog disappear and a notification can be displayed
		setContent(new Label());
		
		// show dialog to establish connection with
		GeneralDialogs.componentPrompt(
				String.format("Connect to %s", getDatabaseHandle().getStaticInformation().getName()),
				true,
				new DatabaseConnectForm(getDatabaseHandle()),
				new IDialogResultHandler()
		{
			@Override
			public boolean handleResult(Object[] args)
			{
				// get passed data
				String hostname = (String) args[0];
				Integer port = (Integer) args[1];

				// connect
				try
				{
					getConnectionHandle().connect(hostname, port);
					if(!getConnectionHandle().isDefined())
					{
						// throw some default exception so that catch block is executed
						throw new IllegalStateException("Connection established but not alive...");
					}
				}
				catch (Exception e)
				{
					// ensure default state
					getConnectionHandle().close();
					
					// log the exception and handle it
					Logger.logThrowable(String.format("Connection to database '%s' at hostname '%s' and port '%d' failed.",
							getDatabaseHandle().getStaticInformation().getName(),
							hostname,
							port), e
					);
					GeneralDialogs.error("Connection failed", e.getLocalizedMessage());
					
					// no, we're not done yet
					return false;
				}
				
				// initialize data controller
				try
				{
					getConnectionHandle().init();
				}
				catch (Exception e)
				{
					// ensure default state
					getConnectionHandle().close();
					
					// log the exception and handle it
					Logger.logThrowable("Data controller failed to initialize.", e);
					GeneralDialogs.error("Initialization failed", "Connection was established but failed to initialize and will close...");
					
					// no, we're not done yet
					return false;
				}
				
				// and finally, build the UI; do it separately so that any actual errors are not processed as connection errors
				doBuildUI();
				return true;
			}
		});
	}
	
	private void doBuildUI()
	{
		/*
		 * Build header.
		 */
		
		Image img_dbLogo = new Image(null, new ThemeResource(getDatabaseHandle().getStaticInformation().getBannerURL()));
		img_dbLogo.setSizeUndefined();
		
		Label lbl_supportedVersion = new Label(getDatabaseHandle().getStaticInformation().getSupportedVersions().getConciseString());
		lbl_supportedVersion.setSizeUndefined();
		lbl_supportedVersion.setCaption("Supported versions:");
		
		Label lbl_currentVersion = new Label();
		lbl_currentVersion.setSizeUndefined();
		lbl_currentVersion.setCaption("Connected to version:");
		
		final String connectedDBVersion = getConnectionHandle().getDBVersion();
		if(connectedDBVersion == null)
		{
			lbl_currentVersion.setValue("unknown");
			lbl_currentVersion.setComponentError(new ErrorMessage()
			{
				private static final long	serialVersionUID	= 9093248735997789342L;

				@Override
				public String getFormattedHtmlMessage()
				{
					return "Mini-app may not be working correctly.";
				}
				
				@Override
				public ErrorLevel getErrorLevel()
				{
					return ErrorLevel.WARNING;
				}
			});
		}
		else
		{
			lbl_currentVersion.setValue(connectedDBVersion);
			Optional<String> theOne = Iterables.tryFind(getDatabaseHandle().getStaticInformation().getSupportedVersions().getAllVersions(), new Predicate<String>()
			{
				@Override
			    public boolean apply(String supportedVersion)
				{
					return connectedDBVersion.startsWith(supportedVersion);
				}
			});
			if(!theOne.isPresent())
			{
				lbl_currentVersion.setComponentError(new ErrorMessage()
				{
					private static final long	serialVersionUID	= 9093248735997789342L;

					@Override
					public String getFormattedHtmlMessage()
					{
						return "Mini-app may not be working correctly.";
					}
					
					@Override
					public ErrorLevel getErrorLevel()
					{
						return ErrorLevel.WARNING;
					}
				});
			}
		}
		
		Label lbl_client = new Label(getDatabaseHandle().getStaticInformation().getSupportedClient());
		lbl_client.setSizeUndefined();
		lbl_client.setCaption("Connected with client:");
		
		FormLayout fLayout_dbInfo = new FormLayout();
		fLayout_dbInfo.setSizeUndefined();
		fLayout_dbInfo.setStyleName("bannerArea-dbInfo");
		fLayout_dbInfo.setMargin(false);
		fLayout_dbInfo.addComponent(lbl_supportedVersion);
		fLayout_dbInfo.addComponent(lbl_currentVersion);
		fLayout_dbInfo.addComponent(lbl_client);
		
		HorizontalLayout hLayout_header = new HorizontalLayout();
		hLayout_header.setSizeUndefined();
		hLayout_header.setStyleName("bannerArea");
		hLayout_header.setWidth("100%");
		hLayout_header.addComponent(img_dbLogo);
		hLayout_header.setComponentAlignment(img_dbLogo, Alignment.MIDDLE_LEFT);
		hLayout_header.addComponent(fLayout_dbInfo);
		hLayout_header.setComponentAlignment(fLayout_dbInfo, Alignment.MIDDLE_RIGHT);
		hLayout_header.setExpandRatio(fLayout_dbInfo, 1);
		
		/*
		 * Prepare the menu and mini-app layout/component for later use.
		 */
		
		final MenuBar menu = new MenuBar();
		menu.setSizeUndefined();
		menu.setWidth("100%");
		
		final IMenuContext menuContextProvider = new IMenuContext()
		{
			@Override
			public MenuItem getMenuItemOrCreateNew(String caption)
			{
				for(MenuItem menuItem : menu.getItems())
				{
					if(menuItem.getText().equals(caption))
					{
						return menuItem;
					}
				}
				return menu.addItem(caption, null);
			}
		};
		
		final MiniApp<DC> miniApp = new MiniApp<DC>();
		miniApp.setSizeFull();
		miniApp.setStyleName("miniApp");
		miniApp.refresh(menuContextProvider, getDataController());;
		
		/*
		 * Build the menu.
		 */
		
		MenuItem actionMenu = menu.addItem("Mini-app", null);
		actionMenu.setStyleName("miniApp");
		actionMenu.addItem("Refresh", new MenuBar.Command()
		{
			private static final long	serialVersionUID	= 8071968688398948524L;

			@Override
			public void menuSelected(MenuItem selectedItem)
			{
				miniApp.refresh(menuContextProvider, getDataController());
			}
		});
		actionMenu.addItem("Erase data", new MenuBar.Command()
		{
			private static final long	serialVersionUID	= 8071968688398948524L;

			@Override
			public void menuSelected(MenuItem selectedItem)
			{
				GeneralDialogs.confirm("Confirm action", "Are you sure? This will delete all data from database.", new IDialogResultHandler()
				{
					@Override
					public boolean handleResult(Object[] args)
					{
						clearDatabase();
						return true;
					}
				});
			}
		});
		actionMenu.addItem("Load sample data", new MenuBar.Command()
		{
			private static final long	serialVersionUID	= 8071968688398948524L;

			@Override
			public void menuSelected(MenuItem selectedItem)
			{
				GeneralDialogs.confirm("Confirm action", "Are you sure? This will overwrite all database data.", new IDialogResultHandler()
				{
					@Override
					public boolean handleResult(Object[] args)
					{
						if(clearDatabase())
						{
							loadSampleData();
						}
						return true;
					}
				});
			}
		});
		
		/*
		 * Bring it all together.
		 */
		
		VerticalLayout masterLayout = new VerticalLayout();
		masterLayout.setSizeFull();
		masterLayout.setStyleName("masterLayout");
		
		masterLayout.addComponent(hLayout_header);
		masterLayout.addComponent(menu);
		masterLayout.addComponent(miniApp);
		masterLayout.setExpandRatio(miniApp, 1);
		
		setPageCroppedAndHorizontallyCentered(true);
		setContent(masterLayout);
	}
	
	private boolean clearDatabase()
	{
		try
		{
			getDataController().clearDatabase();
			return true;
		}
		catch (Exception e)
		{
			Logger.logThrowable("Failed to clear database: ", e);
			MyNotifications.showError("Failed to clear database", e.getLocalizedMessage(), null);
			return false;
		}
	}
	
	private void loadSampleData()
	{
		try
		{
			final SampleLoader<DC> websiteLoader = new SampleLoader<DC>(getDataController()); 
			try (BufferedReader br = new BufferedReader(new InputStreamReader(ServletResources.getResourceAsStream(ServletResources.SAMPLE_DATA))))
			{
				String url;
				while ((url = br.readLine()) != null)
				{
					websiteLoader.load(url);
				}
			}
			finally
			{
				if(websiteLoader.getLoadResult().loadWasACompleteSuccess())
				{
					MyNotifications.showSuccess(null, "Sample data successfully loaded.", null);
				}
				else
				{
					MyNotifications.showWarning("Sample data load result", "Click to display details...", new ClickListener()
					{
						private static final long	serialVersionUID	= 8456228417991600455L;

						@Override
						public void click(ClickEvent event)
						{
							TabSheet report = new TabSheet();
							report.setSizeFull();
							if(!websiteLoader.getLoadResult().getMalformedURLs().isEmpty())
							{
								report.addTab(getComponentFor(websiteLoader.getLoadResult().getMalformedURLs()), "Malformed URLs");
							}
							if(!websiteLoader.getLoadResult().getUrlsWithInvalidResponse().isEmpty())
							{
								report.addTab(getComponentFor(websiteLoader.getLoadResult().getUrlsWithInvalidResponse()), "Invalid response URLs");
							}
							if(!websiteLoader.getLoadResult().getFailedToSaveURLs().isEmpty())
							{
								report.addTab(getComponentFor(websiteLoader.getLoadResult().getFailedToSaveURLs()), "Not saved URLs");
							}
							if(!websiteLoader.getLoadResult().getIgnoredURLs().isEmpty())
							{
								report.addTab(getComponentFor(websiteLoader.getLoadResult().getIgnoredURLs()), "Ignored URLs");
							}
							
							MessageBox mb = GeneralDialogs.componentDialog("Sample data load report", Icon.WARN, report);
							mb.setWidth("600px");
							mb.setHeight("400px");
						}
						
						private Component getComponentFor(List<String> urlList)
						{
							TextArea result = new TextArea(null, StringUtils.join(urlList.iterator(), '\n'));
							result.setSizeFull();
							result.setWordwrap(false);
							return result;
						}
					});
				}
			}
		}
		catch (Exception e)
		{
			Logger.logThrowable("Failed to load sample data: ", e);
			MyNotifications.showError("Failed to load sample data", e.getLocalizedMessage(), null);
			MyNotifications.showInfo(null, "Cleaning database...", null);
			clearDatabase();
		}
	}
}