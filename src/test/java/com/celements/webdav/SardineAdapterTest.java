package com.celements.webdav;

import static com.celements.common.test.CelementsTestUtils.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.configuration.ConfigurationSource;

import com.celements.auth.RemoteLogin;
import com.celements.common.test.AbstractComponentTest;
import com.celements.configuration.CelementsFromWikiConfigurationSource;
import com.github.sardine.DavResource;
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
  public void test() throws Exception {
    assertNotNull(sardineAdapter);
    // test_remote();
  }

  // this test will create a live connection to the defined remote login
  // do not add to automatic test suite
  public void test_remote() throws Exception {
    RemoteLogin remoteLogin = getNextcloudRemoteLogin();
    String test = "test.txt";
    byte[] content = IOUtils.toByteArray(Resources.getResource(test).openStream());
    replayDefault();
    assertNotNull(sardineAdapter.getSardine(remoteLogin));
    verifyDefault();

    boolean stored = sardineAdapter.store(Paths.get(test), content, remoteLogin);
    List<DavResource> listed = sardineAdapter.list(Paths.get("/"), remoteLogin);
    byte[] loaded = sardineAdapter.load(Paths.get(test), remoteLogin);
    boolean deleted = sardineAdapter.delete(Paths.get(test), remoteLogin);

    DavResource davTest = null;
    for (DavResource dav : listed) {
      System.out.println(dav.getPath() + " - " + dav.getContentType());
      if (dav.getName().equals(test)) {
        davTest = dav;
      }
    }
    assertNotNull("test file not in listing", davTest);
    assertEquals("invalid data loaded", content.length, loaded.length);
    for (int i = 0; i < content.length; i++) {
      assertEquals("invalid data loaded", content[i], loaded[i]);
    }
    assertTrue("did not store", stored);
    assertTrue("did not delete", deleted);
  }

  private RemoteLogin getNextcloudRemoteLogin() throws MalformedURLException {
    RemoteLogin remoteLogin = new RemoteLogin();
    remoteLogin.setUsername("Testing");
    remoteLogin.setPassword(""); // XXX set password for testing
    remoteLogin.setUrl("https://nx2627.your-next.cloud/remote.php/webdav");
    String cacerts = "your-next.cloud.jks"; // contains cert for your-next.cloud
    getConfigurationSource().setProperty("celements.security.cacerts", cacerts);
    expect(getWikiMock().getResource(cacerts)).andReturn(Resources.getResource(cacerts));
    return remoteLogin;
  }

}
