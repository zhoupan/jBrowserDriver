package com.machinepublishers.jbrowserdriver.support;

import java.io.File;
import java.net.InetAddress;

import com.machinepublishers.jbrowserdriver.ProxyConfig;
import com.machinepublishers.jbrowserdriver.RequestHeaders;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;
import com.machinepublishers.jbrowserdriver.UserAgent;

import lombok.Data;

@Data
public class JBrowserSupport {
 Settings.Builder settingsBuilder = Settings.builder();
 private RequestHeaders requestHeaders = RequestHeaders.CHROME;
 private org.openqa.selenium.Dimension screen = new org.openqa.selenium.Dimension(1024, 768);
 private UserAgent userAgent = UserAgent.CHROME;
 private Timezone timezone = Timezone.getDefault();
 private ProxyConfig proxy = new ProxyConfig();
 private boolean saveMedia = true;
 private boolean saveAttachments = true;
 private boolean ignoreDialogs = true;
 private boolean cache;
 private File cacheDir;
 private int cacheEntries = 10 * 1000;
 private long cacheEntrySize = 1000 * 1000;
 private boolean headless = false;
 private long ajaxWait = 150;
 private long ajaxResourceTimeout = 2000;
 private boolean blockAds;
 private boolean quickRender;
 private int maxRouteConnections = 8;
 private int maxConnections = 300;
 private String ssl;
 private boolean javascript = true;
 private int socketTimeout = -1;
 private int connectTimeout = -1;
 private int connectionReqTimeout = -1;
 private String host = "127.0.0.1";
 private String[] javaOptions;
 private String javaBinary;
 private boolean javaExportModules;
 private File userDataDirectory;
 private String csrfRequestToken;
 private String csrfResponseToken;
 private InetAddress nicAddress;
 private boolean blockMedia;

 public JBrowserSupport() {
  this.onSettings(this.settingsBuilder);
 }

 public void onSettings(Settings.Builder builder) {
  builder.userAgent(this.userAgent);
  builder.ajaxResourceTimeout(this.ajaxResourceTimeout);
  builder.requestHeaders(this.requestHeaders);
  builder.headless(this.headless);
 }

}
