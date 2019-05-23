package com.celements.webdav.exception;

import java.net.URL;

public class DavResourceAlreadyExistsException extends DavResourceAccessException {

  private static final long serialVersionUID = 1L;

  public DavResourceAlreadyExistsException(URL url) {
    super("Already exists", url);
  }

}
