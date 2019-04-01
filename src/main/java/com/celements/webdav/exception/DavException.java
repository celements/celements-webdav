package com.celements.webdav.exception;

public class DavException extends Exception {

  private static final long serialVersionUID = 1L;

  public DavException(String msg) {
    super(msg);
  }

  public DavException(Throwable cause) {
    super(cause);
  }

  public DavException(String msg, Throwable cause) {
    super(msg, cause);
  }

}
