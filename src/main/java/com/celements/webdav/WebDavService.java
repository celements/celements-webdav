package com.celements.webdav;

import java.nio.file.Path;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.xwiki.component.annotation.ComponentRole;

import com.github.sardine.DavResource;
import com.xpn.xwiki.doc.XWikiDocument;

@ComponentRole
public interface WebDavService {

  @NotNull
  List<DavResource> list(@NotNull Path path, @NotNull XWikiDocument cfgDoc) throws WebDavException;

}
