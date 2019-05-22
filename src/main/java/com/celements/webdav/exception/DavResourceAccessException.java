package com.celements.webdav.exception;

import com.github.sardine.impl.SardineException;

public class DavResourceAccessException extends DavException {

  private static final long serialVersionUID = 1L;

  public DavResourceAccessException(String msg) {
    super(msg);
  }

  public DavResourceAccessException(String msg, SardineException cause) {
    super(msg, cause);
  }

  @Override
  public synchronized SardineException getCause() {
    return (SardineException) super.getCause();
  }

}
