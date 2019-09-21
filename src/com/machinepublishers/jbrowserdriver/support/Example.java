package com.machinepublishers.jbrowserdriver.support;

import com.machinepublishers.jbrowserdriver.JBrowserDriver;
import com.machinepublishers.jbrowserdriver.Settings;
import com.machinepublishers.jbrowserdriver.Timezone;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Example {

 public void github() {
  log.debug("github");
  // You can optionally pass a Settings object here,
  // constructed using Settings.Builder
  JBrowserDriver driver = new JBrowserDriver(Settings.builder().timezone(Timezone.AMERICA_NEWYORK).build());

  // This will block for the page load and any
  // associated AJAX requests
  driver.get("https://github.com");

  // You can get status code unlike other Selenium drivers.
  // It blocks for AJAX requests and page loads after clicks
  // and keyboard events.
  System.out.println(driver.getStatusCode());

  // Returns the page source in its current state, including
  // any DOM updates that occurred after page load
  System.out.println(driver.getPageSource());

  // Close the browser. Allows this thread to terminate.
  driver.quit();
 }

 public static void main(String[] args) {
  Example example = new Example();
  example.github();
 }
}
