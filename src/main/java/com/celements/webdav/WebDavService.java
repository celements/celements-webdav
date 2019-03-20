package com.celements.webdav;

import java.nio.file.Path;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.xwiki.component.annotation.ComponentRole;

import com.celements.auth.RemoteLogin;

@ComponentRole
public interface WebDavService {

  @NotNull
  List<Path> list(@NotNull Path path) throws WebDavException;

  @NotNull
  List<Path> list(@NotNull Path path, @NotNull RemoteLogin remoteLogin) throws WebDavException;

  @NotNull
  RemoteLogin getConfiguredWebDavRemoteLogin() throws WebDavException;

}
