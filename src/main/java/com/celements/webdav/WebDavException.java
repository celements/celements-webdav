package com.celements.webdav;

public class WebDavException extends Exception {

  private static final long serialVersionUID = -2881520545225014158L;

  public WebDavException(String msg) {
    super(msg);
  }

  public WebDavException(Throwable cause) {
    super(cause);
  }

  public WebDavException(String msg, Throwable cause) {
    super(msg, cause);
  }

}
