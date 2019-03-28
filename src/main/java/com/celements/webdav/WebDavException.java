package com.celements.webdav;

import java.net.URL;
import java.text.MessageFormat;

import com.celements.auth.RemoteLogin;

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

  public WebDavException(URL url, RemoteLogin remoteLogin, Throwable cause) {
    this(MessageFormat.format("failed for url [{0}] with login [{1}]", url.toExternalForm(),
        remoteLogin), cause);
  }

}
