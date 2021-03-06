/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.mozilla.android.sync.test.SynchronizerHelpers.TrackingWBORepository;
import org.mozilla.android.sync.test.helpers.BaseTestStorageRequestDelegate;
import org.mozilla.android.sync.test.helpers.HTTPServerTestHelper;
import org.mozilla.android.sync.test.helpers.MockRecord;
import org.mozilla.android.sync.test.helpers.MockServer;
import org.mozilla.android.sync.test.helpers.WaitHelper;
import org.mozilla.gecko.sync.CredentialsSource;
import org.mozilla.gecko.sync.JSONRecordFetcher;
import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.crypto.KeyBundle;
import org.mozilla.gecko.sync.middleware.Crypto5MiddlewareRepository;
import org.mozilla.gecko.sync.net.BaseResource;
import org.mozilla.gecko.sync.net.SyncStorageRecordRequest;
import org.mozilla.gecko.sync.net.SyncStorageResponse;
import org.mozilla.gecko.sync.repositories.FetchFailedException;
import org.mozilla.gecko.sync.repositories.Repository;
import org.mozilla.gecko.sync.repositories.RepositorySession;
import org.mozilla.gecko.sync.repositories.Server11Repository;
import org.mozilla.gecko.sync.repositories.Server11RepositorySession;
import org.mozilla.gecko.sync.repositories.StoreFailedException;
import org.mozilla.gecko.sync.repositories.delegates.RepositorySessionCreationDelegate;
import org.mozilla.gecko.sync.repositories.domain.BookmarkRecord;
import org.mozilla.gecko.sync.repositories.domain.BookmarkRecordFactory;
import org.mozilla.gecko.sync.repositories.domain.Record;
import org.mozilla.gecko.sync.stage.SafeConstrainedServer11Repository;
import org.mozilla.gecko.sync.synchronizer.ServerLocalSynchronizer;
import org.mozilla.gecko.sync.synchronizer.Synchronizer;
import org.simpleframework.http.ContentType;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;

import ch.boye.httpclientandroidlib.HttpEntity;

public class TestServer11RepositorySession implements CredentialsSource {

  public class POSTMockServer extends MockServer {
    @Override
    public void handle(Request request, Response response) {
      try {
        String content = request.getContent();
        System.out.println("Content:" + content);
      } catch (IOException e) {
        e.printStackTrace();
      }
      ContentType contentType = request.getContentType();
      System.out.println("Content-Type:" + contentType);
      super.handle(request, response, 200, "{success:[]}");
    }
  }

  private static final int    TEST_PORT   = HTTPServerTestHelper.getTestPort();
  private static final String TEST_SERVER = "http://localhost:" + TEST_PORT + "/";
  static final String LOCAL_BASE_URL      = TEST_SERVER + "1.1/n6ec3u5bee3tixzp2asys7bs6fve4jfw/";
  static final String LOCAL_REQUEST_URL   = LOCAL_BASE_URL + "storage/bookmarks";
  static final String LOCAL_INFO_BASE_URL = LOCAL_BASE_URL + "info/";
  static final String LOCAL_COUNTS_URL    = LOCAL_INFO_BASE_URL + "collection_counts";

  // Corresponds to rnewman+atest1@mozilla.com, local.
  static final String USERNAME          = "n6ec3u5bee3tixzp2asys7bs6fve4jfw";
  static final String USER_PASS         = "n6ec3u5bee3tixzp2asys7bs6fve4jfw:password";
  static final String SYNC_KEY          = "eh7ppnb82iwr5kt3z3uyi5vr44";

  // Few-second timeout so that our longer operations don't time out and cause spurious error-handling results.
  private static final int SHORT_TIMEOUT = 10000;

  @Override
  public String credentials() {
    return USER_PASS;
  }

  private HTTPServerTestHelper data     = new HTTPServerTestHelper();

  public class MockServer11RepositorySession extends Server11RepositorySession {
    public MockServer11RepositorySession(Repository repository) {
      super(repository);
    }

    public RecordUploadRunnable getRecordUploadRunnable() {
      // TODO: implement upload delegate in the class, too!
      return new RecordUploadRunnable(null, recordsBuffer, recordGuidsBuffer, byteCount);
    }

    public void enqueueRecord(Record r) {
      super.enqueue(r);
    }

    public HttpEntity getEntity() {
      return this.getRecordUploadRunnable().getBodyEntity();
    }
  }

  public class TestSyncStorageRequestDelegate extends
      BaseTestStorageRequestDelegate {
    @Override
    public void handleRequestSuccess(SyncStorageResponse res) {
      assertTrue(res.wasSuccessful());
      assertTrue(res.httpResponse().containsHeader("X-Weave-Timestamp"));
      BaseResource.consumeEntity(res);
      data.stopHTTPServer();
    }
  }

  @Test
  public void test() throws URISyntaxException {

    BaseResource.rewriteLocalhost = false;
    data.startHTTPServer(new POSTMockServer());

    MockServer11RepositorySession session = new MockServer11RepositorySession(
        null);
    session.enqueueRecord(new MockRecord(Utils.generateGuid(), null, 0, false));
    session.enqueueRecord(new MockRecord(Utils.generateGuid(), null, 0, false));

    URI uri = new URI(LOCAL_REQUEST_URL);
    SyncStorageRecordRequest r = new SyncStorageRecordRequest(uri);
    TestSyncStorageRequestDelegate delegate = new TestSyncStorageRequestDelegate();
    delegate._credentials = USER_PASS;
    r.delegate = delegate;
    r.post(session.getEntity());
  }

  @SuppressWarnings("static-method")
  protected TrackingWBORepository getLocal(int numRecords) {
    final TrackingWBORepository local = new TrackingWBORepository();
    for (int i = 0; i < numRecords; i++) {
      BookmarkRecord outbound = new BookmarkRecord("outboundFail" + i, "bookmarks", 1, false);
      local.wbos.put(outbound.guid, outbound);
    }
    return local;
  }

  protected Exception doSynchronize(MockServer server) throws Exception {
    final String COLLECTION = "test";

    final TrackingWBORepository local = getLocal(100);
    final Server11Repository remote = new Server11Repository(TEST_SERVER, USERNAME, COLLECTION, this);
    KeyBundle collectionKey = new KeyBundle(USERNAME, SYNC_KEY);
    Crypto5MiddlewareRepository cryptoRepo = new Crypto5MiddlewareRepository(remote, collectionKey);
    cryptoRepo.recordFactory = new BookmarkRecordFactory();

    final Synchronizer synchronizer = new ServerLocalSynchronizer();
    synchronizer.repositoryA = cryptoRepo;
    synchronizer.repositoryB = local;

    data.startHTTPServer(server);
    try {
      Exception e = TestServerLocalSynchronizer.doSynchronize(synchronizer);
      return e;
    } finally {
      data.stopHTTPServer();
    }
  }

  @Test
  public void testFetchFailure() throws Exception {
    MockServer server = new MockServer(404, "error");
    Exception e = doSynchronize(server);
    assertNotNull(e);
    assertEquals(FetchFailedException.class, e.getClass());
  }

  @Test
  public void testStorePostSuccessWithFailingRecords() throws Exception {
    MockServer server = new MockServer(200, "{ modified: \" + " + Utils.millisecondsToDecimalSeconds(System.currentTimeMillis()) + ", " +
        "success: []," +
        "failed: { outboundFail2: [] } }");
    Exception e = doSynchronize(server);
    assertNotNull(e);
    assertEquals(StoreFailedException.class, e.getClass());
  }

  @Test
  public void testStorePostFailure() throws Exception {
    MockServer server = new MockServer() {
      public void handle(Request request, Response response) {
        if (request.getMethod().equals("POST")) {
          this.handle(request, response, 404, "missing");
        }
        this.handle(request, response, 200, "success");
        return;
      }
    };

    Exception e = doSynchronize(server);
    assertNotNull(e);
    assertEquals(StoreFailedException.class, e.getClass());
  }

  @Test
  public void testConstraints() throws Exception {
    MockServer server = new MockServer() {
      public void handle(Request request, Response response) {
        if (request.getMethod().equals("GET")) {
          if (request.getPath().getPath().endsWith("/info/collection_counts")) {
            this.handle(request, response, 200, "{\"bookmarks\": 5001}");
          }
        }
        this.handle(request, response, 400, "NOOOO");
      }
    };
    final JSONRecordFetcher countsFetcher = new JSONRecordFetcher(LOCAL_COUNTS_URL, this.credentials());
    final SafeConstrainedServer11Repository remote = new SafeConstrainedServer11Repository(TEST_SERVER, USERNAME, "bookmarks", this, 5000, "sortindex", countsFetcher);

    data.startHTTPServer(server);
    final AtomicBoolean out = new AtomicBoolean(false);

    // Verify that shouldSkip returns true due to a fetch of too large counts,
    // rather than due to a timeout failure waiting to fetch counts.
    try {
      WaitHelper.getTestWaiter().performWait(
          SHORT_TIMEOUT,
          new Runnable() {
            @Override
            public void run() {
              remote.createSession(new RepositorySessionCreationDelegate() {
                @Override
                public void onSessionCreated(RepositorySession session) {
                  out.set(session.shouldSkip());
                  WaitHelper.getTestWaiter().performNotify();
                }

                @Override
                public void onSessionCreateFailed(Exception ex) {
                  WaitHelper.getTestWaiter().performNotify(ex);
                }

                @Override
                public RepositorySessionCreationDelegate deferredCreationDelegate() {
                  return this;
                }
              }, null);
            }
          });
      assertTrue(out.get());
    } finally {
      data.stopHTTPServer();
    }
  }
}
