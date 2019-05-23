package com.celements.webdav.exception;

import java.net.URL;

import com.github.sardine.impl.SardineException;

public class DavResourceAccessException extends DavException {

  private static final long serialVersionUID = 1L;

  private final URL url;

  protected DavResourceAccessException(String msg, URL url) {
    super(msg + " - " + url);
    this.url = url;
  }

  public DavResourceAccessException(String msg, URL url, SardineException cause) {
    super(msg + " - " + url, cause);
    this.url = url;
  }

  public URL getUrl() {
    return url;
  }

  @Override
  public synchronized SardineException getCause() {
    return (SardineException) super.getCause();
  }

}
