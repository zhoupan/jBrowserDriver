package com.machinepublishers.jbrowserdriver.support;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.machinepublishers.jbrowserdriver.JBrowserDriver;

/**
 * The Class JBrowserDrivers.
 * 
 * @author zhoupan.
 */
public class JBrowserDrivers {

 /** The cache. */
 Map<Object, JBrowserDriver> cache = new java.util.concurrent.ConcurrentHashMap<>();

 /**
  * Gets the.
  *
  * @param key      the key
  * @param supplier the supplier
  * @return the j browser driver
  */
 public JBrowserDriver get(Object key, Supplier<JBrowserDriver> supplier) {
  if (cache.containsKey(key)) {
   return cache.get(key);
  } else {
   JBrowserDriver value = supplier.get();
   if (value != null) {
    cache.put(key, value);
   }
   return value;
  }
 }

 /**
  * Removes the.
  *
  * @param key the key
  */
 public void remove(Object key) {
  JBrowserDriver value = this.cache.get(key);
  if (value != null) {
   value.quit();
  }
  this.cache.remove(key);
 }

 /**
  * clear.
  */
 public void clear() {
  cache.values().forEach(new Consumer<JBrowserDriver>() {

   @Override
   public void accept(JBrowserDriver t) {
    t.quit();
   }
  });

  cache.clear();
 }
}
