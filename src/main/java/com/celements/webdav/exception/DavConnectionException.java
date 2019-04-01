package com.celements.webdav.exception;

public class DavConnectionException extends DavException {

  private static final long serialVersionUID = 1L;

  public DavConnectionException(String msg) {
    super(msg);
  }

  public DavConnectionException(Throwable cause) {
    super(cause);
  }

  public DavConnectionException(String msg, Throwable cause) {
    super(msg, cause);
  }

}
