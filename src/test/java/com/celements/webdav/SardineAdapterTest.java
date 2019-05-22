package com.celements.webdav;

import static com.celements.common.test.CelementsTestUtils.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.context.Execution;

import com.celements.auth.RemoteLogin;
import com.celements.common.test.AbstractComponentTest;
import com.celements.configuration.CelementsFromWikiConfigurationSource;
import com.celements.webdav.SardineAdapter.SardineConnection;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.google.common.io.Resources;
import com.xpn.xwiki.web.Utils;

public class SardineAdapterTest extends AbstractComponentTest {

  private SardineAdapter sardineAdapter;

  @Before
  public void prepareTest() throws Exception {
    registerComponentMock(ConfigurationSource.class, CelementsFromWikiConfigurationSource.NAME,
        getConfigurationSource());
    sardineAdapter = (SardineAdapter) Utils.getComponent(WebDavService.class);
    assertNotNull(sardineAdapter);
  }

  @Test
  public void test_buildCompleteUrl() throws Exception {
    URL baseUrl = new URL("http://celements.com/");
    try (SardineConnection conn = sardineAdapter.new SardineConnection(
        createMockAndAddToDefault(Sardine.class), baseUrl)) {
      assertEquals("http://celements.com/dir",
          conn.buildCompleteUrl(Paths.get("dir")).toExternalForm());
      assertEquals("http://celements.com/dir/sub",
          conn.buildCompleteUrl(Paths.get("/", "dir", "sub")).toExternalForm());
    }
  }

  @Test
  public void test_buildCompleteUrl_withBasePath() throws Exception {
    URL baseUrl = new URL("http://celements.com/main");
    try (SardineConnection conn = sardineAdapter.new SardineConnection(
        createMockAndAddToDefault(Sardine.class), baseUrl)) {
      assertEquals("http://celements.com/main/dir/sub",
          conn.buildCompleteUrl(Paths.get("dir", "sub")).toExternalForm());
      assertEquals("http://celements.com/main/dir/sub",
          conn.buildCompleteUrl(Paths.get("/", "main", "dir", "sub")).toExternalForm());
    }
  }

  @Test
  public void test_connect() throws Exception {
    RemoteLogin remoteLogin = getNextcloudRemoteLogin();
    Sardine sardineMock = createMockAndAddToDefault(Sardine.class);
    Utils.getComponent(Execution.class).getContext()
        .setProperty(sardineAdapter.getSardineExecutionContextKey(remoteLogin), sardineMock);
    expect(sardineMock.exists(remoteLogin.getUrl())).andReturn(true);

    replayDefault();
    assertNotNull(sardineAdapter.connect(remoteLogin));
    verifyDefault();
  }

  @Test
  public void test() throws Exception {
    assertNotNull(sardineAdapter);
    // test_remote();
  }

  // this test will create a live connection to the defined remote login
  // do not add to automatic test suite
  public void test_remote() throws Exception {
    RemoteLogin remoteLogin = getNextcloudRemoteLogin();
    Path test = Paths.get("test.txt");
    byte[] content = IOUtils.toByteArray(Resources.getResource(test.toString()).openStream());

    replayDefault();
    try (SardineConnection webDav = sardineAdapter.connect(remoteLogin)) {
      assertNotNull(webDav);

      // create & get
      assertFalse(webDav.get(test).isPresent());
      webDav.create(test, content);
      try {
        assertTrue(webDav.get(test).isPresent());
        // list
        List<DavResource> listed = webDav.list(Paths.get("/"));
        DavResource testResource = null;
        for (DavResource resource : listed) {
          System.out.println(resource.getPath() + " - " + resource.getContentType());
          if (test.equals(Paths.get(resource.getName()))) {
            testResource = resource;
          }
        }
        assertNotNull("test file not in listing", testResource);
        assertFalse("test file shouldnt be dir", testResource.isDirectory());
        assertEquals("different mime type", "text/plain", testResource.getContentType());
        // update & load
        assertTrue("invalid data loaded", Arrays.equals(content, webDav.load(test)));
        content[1] = 0;
        assertFalse("content unchanged", Arrays.equals(content, webDav.load(test)));
        webDav.update(test, content);
        assertTrue("update did not work", Arrays.equals(content, webDav.load(test)));
      } finally { // delete
        webDav.delete(test);
        assertFalse(webDav.get(test).isPresent());
      }

      // createDir & delete
      Path dir = Paths.get("dir");
      webDav.createDirectory(dir);
      try {
        Optional<DavResource> dirResource = webDav.get(dir);
        assertTrue(dirResource.isPresent());
        assertTrue(dirResource.get().isDirectory());
        webDav.createOrUpdate(dir.resolve(test), content);
      } finally {
        webDav.delete(dir);
        assertFalse(webDav.get(dir).isPresent());
      }
    }
    verifyDefault();
  }

  private RemoteLogin getNextcloudRemoteLogin() throws MalformedURLException {
    RemoteLogin remoteLogin = new RemoteLogin();
    remoteLogin.setUsername("Testing");
    remoteLogin.setPassword(""); // XXX set password for testing
    remoteLogin.setUrl("https://nx2627.your-next.cloud/remote.php/webdav");
    String cacerts = "your-next.cloud.jks"; // contains cert for your-next.cloud
    getConfigurationSource().setProperty("celements.security.cacerts", cacerts);
    expect(getWikiMock().getResource(cacerts)).andReturn(Resources.getResource(cacerts)).anyTimes();
    return remoteLogin;
  }

}
