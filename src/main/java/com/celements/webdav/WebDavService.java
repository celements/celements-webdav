package com.celements.webdav;

import java.nio.file.Path;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.xwiki.component.annotation.ComponentRole;

import com.celements.auth.RemoteLogin;
import com.github.sardine.DavResource;
import com.google.common.base.Optional;

@ComponentRole
public interface WebDavService {

  @NotNull
  RemoteLogin getConfiguredWebDavRemoteLogin() throws WebDavException;

  @NotNull
  List<DavResource> list(@NotNull Path path) throws WebDavException;

  @NotNull
  List<DavResource> list(@NotNull Path path, @NotNull RemoteLogin remoteLogin)
      throws WebDavException;

  @NotNull
  Optional<DavResource> get(@NotNull Path path) throws WebDavException;

  @NotNull
  Optional<DavResource> get(@NotNull Path path, @NotNull RemoteLogin remoteLogin)
      throws WebDavException;

  @NotNull
  byte[] load(@NotNull Path filePath) throws WebDavException;

  @NotNull
  byte[] load(@NotNull Path filePath, @NotNull RemoteLogin remoteLogin) throws WebDavException;

  boolean store(@NotNull Path filePath, @NotNull byte[] content) throws WebDavException;

  boolean store(@NotNull Path filePath, @NotNull byte[] content, @NotNull RemoteLogin remoteLogin)
      throws WebDavException;

  boolean delete(@NotNull Path path) throws WebDavException;

  boolean delete(@NotNull Path path, @NotNull RemoteLogin remoteLogin) throws WebDavException;

}
