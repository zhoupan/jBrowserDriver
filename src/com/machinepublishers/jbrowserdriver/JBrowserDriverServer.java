/* 
 * jBrowserDriver (TM)
 * Copyright (C) 2014-2017 jBrowserDriver committers
 * https://github.com/MachinePublishers/jBrowserDriver
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.machinepublishers.jbrowserdriver;

import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.HasInputDevices;
import org.openqa.selenium.internal.FindsByClassName;
import org.openqa.selenium.internal.FindsByCssSelector;
import org.openqa.selenium.internal.FindsById;
import org.openqa.selenium.internal.FindsByLinkText;
import org.openqa.selenium.internal.FindsByName;
import org.openqa.selenium.internal.FindsByTagName;
import org.openqa.selenium.internal.FindsByXPath;

import com.sun.javafx.webkit.Accessor;
import com.sun.webkit.WebPage;
import com.sun.webkit.network.CookieManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class JBrowserDriverServer extends RemoteObject implements JBrowserDriverRemote, WebDriver, JavascriptExecutor, FindsById, FindsByClassName,
 FindsByLinkText, FindsByName, FindsByCssSelector, FindsByTagName, FindsByXPath, HasInputDevices, HasCapabilities, TakesScreenshot {
 private static final AtomicInteger childPort = new AtomicInteger();
 private static final AtomicReference<SocketFactory> socketFactory = new AtomicReference<SocketFactory>();
 private static Registry registry;

 public void onConfigStreamHandler() {
  URL.setURLStreamHandlerFactory(new StreamHandler());
 }

 public void onConfigCookieStore() {
  CookieManager.setDefault(new CookieStore());
 }

 final AtomicReference<Context> context = new AtomicReference<Context>();

 public JBrowserDriverServer() {

 }

 @Override
 public void setUp(final Settings settings) {
  SettingsManager.register(settings);
  context.set(new Context());
 }

 @Override
 public void storeCapabilities(final Capabilities capabilities) {
  context.get().capabilities.set(capabilities);
 }

 /**
  * Optionally call this method if you want JavaFX initialized and the browser
  * window opened immediately. Otherwise, initialization will happen lazily.
  */
 public void init() {
  log.debug("init");
  context.get().init(this);
 }

 /**
  * Reset the state of the browser. More efficient than quitting the browser and
  * creating a new instance.
  * 
  * @param settings New settings to take effect, superseding the original ones
  */
 public void reset(final Settings settings) {
  AppThread.exec(() -> {
   context.get().item().engine.get().getLoadWorker().cancel();
   return null;
  });
  Accessor.getPageFor(context.get().item().engine.get()).stop();
  ((CookieStore) CookieManager.getDefault()).clear();
  StatusMonitor.instance().clear();
  LogsServer.instance().clear(null);
  SettingsManager.register(settings);
  context.get().reset(this);
 }

 /**
  * Reset the state of the browser. More efficient than quitting the browser and
  * creating a new instance.
  */
 public void reset() {
  reset(SettingsManager.settings());
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public String getPageSource() {
  init();
  WebElement element = ElementServer.create(context.get().item()).findElementByTagName("html");
  if (element != null) {
   String outerHtml = element.getAttribute("outerHTML");
   if (outerHtml != null && !outerHtml.isEmpty()) {
    String docType = null;
    try {
     docType = (String) executeScript("return document.doctype ? new XMLSerializer().serializeToString(document.doctype) : '';");
    } catch (Exception e) {
     /*
      * ignore -- not sure if there are cases where serialization will fail, and
      * losing doctype doesn't seem important enough to go to fallbacks
      */
    }
    docType = docType == null || docType.isEmpty() ? "" : docType + "\n";
    return docType + outerHtml;
   }
  }
  return AppThread.exec(context.get().item().statusCode, () -> {
   WebPage page = Accessor.getPageFor(context.get().item().engine.get());
   String html = page.getHtml(page.getMainFrame());
   if (html != null && !html.isEmpty()) {
    return html;
   }
   try {
    StringWriter stringWriter = new StringWriter();
    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.transform(new DOMSource(context.get().item().engine.get().getDocument()), new StreamResult(stringWriter));
    return stringWriter.toString();
   } catch (Throwable t) {
   }
   return page.getInnerText(page.getMainFrame());
  });
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public String getCurrentUrl() {
  init();
  return AppThread.exec(context.get().item().statusCode, () -> context.get().item().view.get().getEngine().getLocation());
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public void pageWait() {
  context.get().item().httpListener.get().resetStatusCode();
  getStatusCode();
 }

 public int getStatusCode() {
  return getStatusCode(context.get().timeouts.get().getPageLoadTimeoutMS());
 }

 private int getStatusCode(long waitMS) {
  init();
  int statusCode;
  synchronized (context.get().item().statusCode) {
   long start = System.currentTimeMillis();
   while ((statusCode = context.get().item().statusCode.get()) <= 0) {
    try {
     long nextWait = waitMS - (System.currentTimeMillis() - start);
     if (nextWait >= 0) {
      context.get().item().statusCode.wait(nextWait);
     } else if (waitMS == 0) {
      context.get().item().statusCode.wait();
     } else {
      break;
     }
    } catch (InterruptedException e) {
    }
   }
  }
  return statusCode;
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public String getTitle() {
  init();
  return AppThread.exec(context.get().item().statusCode, () -> context.get().item().view.get().getEngine().getTitle());
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public void get(final String url) {
  log.debug("get:{}", url);
  init();
  long start = System.currentTimeMillis();
  try {
   AppThread.exec(context.get().item().statusCode, context.get().timeouts.get().getPageLoadTimeoutMS(), () -> {
    context.get().item().httpListener.get().resetStatusCode();
    context.get().item().engine.get().load(url);
    return null;
   });
   long end = System.currentTimeMillis();
   if (context.get().timeouts.get().getPageLoadTimeoutMS() == 0) {
    getStatusCode();
   } else {
    long waitMS = context.get().timeouts.get().getPageLoadTimeoutMS() - (end - start);
    if (waitMS > 0) {
     getStatusCode(waitMS);
    }
   }
  } finally {
   if (context.get().item().statusCode.get() == 0) {
    AppThread.exec(() -> {
     context.get().item().engine.get().getLoadWorker().cancel();
     throw new TimeoutException("Timeout of " + context.get().timeouts.get().getPageLoadTimeoutMS() + "ms reached.");
    }, context.get().timeouts.get().getPageLoadTimeoutMS());
   }
  }
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElement(By by) {
  init();
  return ElementServer.create(context.get().item()).findElement(by);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElements(By by) {
  init();
  return ElementServer.create(context.get().item()).findElements(by);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElementById(String id) {
  init();
  return ElementServer.create(context.get().item()).findElementById(id);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElementsById(String id) {
  init();
  return ElementServer.create(context.get().item()).findElementsById(id);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElementByXPath(String expr) {
  init();
  return ElementServer.create(context.get().item()).findElementByXPath(expr);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElementsByXPath(String expr) {
  init();
  return ElementServer.create(context.get().item()).findElementsByXPath(expr);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElementByLinkText(final String text) {
  init();
  return ElementServer.create(context.get().item()).findElementByLinkText(text);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElementByPartialLinkText(String text) {
  init();
  return ElementServer.create(context.get().item()).findElementByPartialLinkText(text);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElementsByLinkText(String text) {
  init();
  return ElementServer.create(context.get().item()).findElementsByLinkText(text);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElementsByPartialLinkText(String text) {
  init();
  return ElementServer.create(context.get().item()).findElementsByPartialLinkText(text);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElementByClassName(String cssClass) {
  init();
  return ElementServer.create(context.get().item()).findElementByClassName(cssClass);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElementsByClassName(String cssClass) {
  init();
  return ElementServer.create(context.get().item()).findElementsByClassName(cssClass);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElementByName(String name) {
  init();
  return ElementServer.create(context.get().item()).findElementByName(name);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElementsByName(String name) {
  init();
  return ElementServer.create(context.get().item()).findElementsByName(name);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElementByCssSelector(String expr) {
  init();
  return ElementServer.create(context.get().item()).findElementByCssSelector(expr);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElementsByCssSelector(String expr) {
  init();
  return ElementServer.create(context.get().item()).findElementsByCssSelector(expr);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public ElementServer findElementByTagName(String tagName) {
  init();
  return ElementServer.create(context.get().item()).findElementByTagName(tagName);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public List findElementsByTagName(String tagName) {
  init();
  return ElementServer.create(context.get().item()).findElementsByTagName(tagName);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public Object executeAsyncScript(String script, Object... args) {
  init();
  return ElementServer.create(context.get().item()).executeAsyncScript(script, args);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public Object executeScript(String script, Object... args) {
  init();
  return ElementServer.create(context.get().item()).executeScript(script, args);
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public KeyboardServer getKeyboard() {
  init();
  return context.get().keyboard.get();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public MouseServer getMouse() {
  init();
  return context.get().mouse.get();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public Capabilities getCapabilities() {
  init();
  return context.get().capabilities.get();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public void close() {
  init();
  context.get().removeItem();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public String getWindowHandle() {
  init();
  return context.get().itemId();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public Set<String> getWindowHandles() {
  init();
  return context.get().itemIds();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public OptionsServer manage() {
  init();
  return context.get().options.get();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public LogsServer logs() {
  return LogsServer.instance();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public NavigationServer navigate() {
  init();
  return context.get().navigation.get();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public void quit() {
  getStatusCode();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public TargetLocatorServer switchTo() {
  init();
  return context.get().targetLocator.get();
 }

 /**
  * {@inheritDoc}
  */
 @Deprecated
 public void kill() {
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public <X> X getScreenshotAs(final OutputType<X> outputType) throws WebDriverException {
  return outputType.convertFromPngBytes(getScreenshot());
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public byte[] getScreenshot() throws WebDriverException {
  init();
  return context.get().robot.get().screenshot();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public File cacheDir() {
  return StreamConnection.cacheDir();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public File attachmentsDir() {
  return StreamConnection.attachmentsDir();
 }

 /**
  * {@inheritDoc}
  */
 @Override
 public File mediaDir() {
  return StreamConnection.mediaDir();
 }
}
