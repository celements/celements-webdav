package com.celements.webdav;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.apache.commons.configuration.ConfigurationException;
import org.xwiki.component.annotation.ComponentRole;

import com.celements.auth.RemoteLogin;
import com.celements.webdav.exception.DavConnectionException;
import com.celements.webdav.exception.DavResourceAccessException;
import com.github.sardine.DavResource;
import com.google.common.base.Optional;

@ComponentRole
public interface WebDavService {

  @NotNull
  RemoteLogin getConfiguredRemoteLogin() throws ConfigurationException;

  @NotNull
  WebDavConnection connect() throws DavConnectionException, MalformedURLException,
      ConfigurationException;

  @NotNull
  WebDavConnection connect(@NotNull RemoteLogin remoteLogin) throws DavConnectionException,
      MalformedURLException;

  interface WebDavConnection extends AutoCloseable {

    @NotNull
    List<DavResource> list(@NotNull Path path) throws IOException, DavResourceAccessException;

    @NotNull
    Optional<DavResource> get(@NotNull Path path) throws IOException;

    @NotNull
    byte[] load(@NotNull Path filePath) throws IOException, DavResourceAccessException;

    void createDirectory(@NotNull Path dirPath) throws IOException, DavResourceAccessException;

    void create(@NotNull Path filePath, @NotNull byte[] content) throws IOException,
        DavResourceAccessException;

    void update(@NotNull Path filePath, @NotNull byte[] content) throws IOException,
        DavResourceAccessException;

    void createOrUpdate(@NotNull Path filePath, @NotNull byte[] content) throws IOException,
        DavResourceAccessException;

    void delete(@NotNull Path path) throws IOException, DavResourceAccessException;

  }

}
