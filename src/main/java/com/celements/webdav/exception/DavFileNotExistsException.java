package com.celements.webdav.exception;

import java.net.URL;

public class DavFileNotExistsException extends DavResourceAccessException {

  private static final long serialVersionUID = 1L;

  public DavFileNotExistsException(URL url) {
    super("Not file", url);
  }

}
