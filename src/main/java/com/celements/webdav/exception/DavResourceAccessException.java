package com.celements.webdav.exception;

public class DavResourceAccessException extends DavException {

  private static final long serialVersionUID = 1L;

  public DavResourceAccessException(String msg) {
    super(msg);
  }

  public DavResourceAccessException(Throwable cause) {
    super(cause);
  }

  public DavResourceAccessException(String msg, Throwable cause) {
    super(msg, cause);
  }

}
