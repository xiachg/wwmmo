package au.com.codeka.warworlds.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.codeka.warworlds.App;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.RealmContext;
import au.com.codeka.warworlds.model.Realm;

/**
 * Provides the low-level interface for making requests to the API.
 */
public class RequestManager {
    final static Logger log = LoggerFactory.getLogger(RequestManager.class);
    private static Map<Integer, ConnectionPool> sConnectionPools;
    private static List<ResponseReceivedHandler> sResponseReceivedHandlers =
            new ArrayList<ResponseReceivedHandler>();
    private static List<RequestManagerStateChangedHandler> sRequestManagerStateChangedHandlers =
            new ArrayList<RequestManagerStateChangedHandler>();
    private static String sImpersonateUser;
    private static boolean sVerboseLog = true;

    static {
        sConnectionPools = new TreeMap<Integer, ConnectionPool>();
    }

    private static ConnectionPool getConnectionPool() {
        Realm realm = RealmContext.i.getCurrentRealm();
        if (realm == null) {
            return null;
        }

        synchronized (sConnectionPools) {
            if (!sConnectionPools.containsKey(realm.getID())) {
                ConnectionPool cp = configureConnectionPool(realm);
                sConnectionPools.put(realm.getID(), cp);
                return cp;
            }
        }
        return sConnectionPools.get(realm.getID());
    }

    private static ConnectionPool configureConnectionPool(Realm realm) {
        URI baseUrl = realm.getBaseUrl();
        boolean ssl = false;
        if (baseUrl.getScheme().equalsIgnoreCase("https")) {
            ssl = true;
        } else if (!baseUrl.getScheme().equalsIgnoreCase("http")) {
            // should never happen
            log.error("Invalid URI scheme \"{}\", assuming http.", baseUrl.getScheme());
        }

        return new ConnectionPool(ssl, baseUrl.getHost(), baseUrl.getPort());
    }

    public static void impersonate(String user) {
        sImpersonateUser = user;
    }

    /**
     * Performs a request with the given method to the given URL. The URL is assumed to be
     * relative to the \c baseUri that was passed in to \c configure().
     * 
     * @param method The HTTP method (GET, POST, etc)
     * @param url The URL, relative to the \c baseUri we were configured with.
     * @return A \c ResultWrapper representing the server's response (if any)
     */
    public static ResultWrapper request(String method, String url) throws ApiException {
        return request(method, url, null, null);
    }

    /**
     * Performs a request with the given method to the given URL. The URL is assumed to be
     * relative to the \c baseUri that was passed in to \c configure().
     * 
     * @param method The HTTP method (GET, POST, etc)
     * @param url The URL, relative to the \c baseUri we were configured with.
     * @param extraHeaders a mapping of additional headers to include in the request (e.g.
     *        cookies, etc)
     * @return A \c ResultWrapper representing the server's response (if any)
     */
    public static ResultWrapper request(String method, String url,
            Map<String, List<String>> extraHeaders) throws ApiException {
        return request(method, url, extraHeaders, null);
    }

    /**
     * Performs a request with the given method to the given URL. The URL is assumed to be
     * relative to the \c baseUri that was passed in to \c configure().
     * 
     * @param method The HTTP method (GET, POST, etc)
     * @param url The URL, relative to the \c baseUri we were configured with.
     * @param extraHeaders a mapping of additional headers to include in the request (e.g.
     *        cookies, etc)
     * @return A \c ResultWrapper representing the server's response (if any)
     */
    public static ResultWrapper request(String method, String url,
            Map<String, List<String>> extraHeaders, HttpEntity body) throws ApiException {
        Connection conn = null;

        ConnectionPool cp = getConnectionPool();
        Realm realm = RealmContext.i.getCurrentRealm();
        if (cp == null || realm == null) {
            throw new ApiException("Not yet configured, cannot execute "+method+" "+url);
        }

        URI uri = realm.getBaseUrl().resolve(url);
        if (sVerboseLog) {
            log.debug("Requesting: {}", uri);
        }

        for(int numAttempts = 0; ; numAttempts++) {
            try {
                // Note: we only allow connections from the pool on the first attempt, if
                // requests fail, we force creating a new connection
                conn = cp.getConnection(numAttempts > 0);

                fireRequestManagerStateChangedHandlers();

                String requestUrl = uri.getPath();
                if (uri.getQuery() != null && uri.getQuery() != "") {
                    requestUrl += "?"+uri.getQuery();
                }
                if (sImpersonateUser != null) {
                    if (requestUrl.indexOf("?") > 0) {
                        requestUrl += "&";
                    } else {
                        requestUrl += "?";
                    }
                    requestUrl += "on_behalf_of="+sImpersonateUser;
                }
                if (sVerboseLog) {
                    log.debug(String.format("> %s %s", method, requestUrl));
                }

                BasicHttpRequest request;
                if (body != null) {
                    BasicHttpEntityEnclosingRequest beer =
                            new BasicHttpEntityEnclosingRequest(method, requestUrl);
                    beer.setEntity(body);
                    request = beer;
                } else {
                    request = new BasicHttpRequest(method, requestUrl);
                }

                String host = uri.getHost();
                if (uri.getPort() > 0 && (
                        (uri.getScheme().equals("http") && uri.getPort() != 80) ||
                        (uri.getScheme().equals("https") && uri.getPort() != 443))) {
                    host += ":"+uri.getPort();
                }
                request.addHeader("Host", host);

                if (extraHeaders != null) {
                    for(String headerName : extraHeaders.keySet()) {
                        for(String headerValue : extraHeaders.get(headerName)) {
                            request.addHeader(headerName, headerValue);
                        }
                    }
                }
                if (body != null) {
                    request.addHeader(body.getContentType());
                    request.addHeader("Content-Length", Long.toString(body.getContentLength()));
                } else if (method.equalsIgnoreCase("PUT") || method.equalsIgnoreCase("POST")) {
                    request.addHeader("Content-Length", "0");
                }

                BasicHttpResponse response = conn.sendRequest(request, body);
                if (sVerboseLog) {
                    log.debug(String.format("< %s", response.getStatusLine()));
                }
                fireResponseReceivedHandlers(request, response);

                return new ResultWrapper(conn, response);
            } catch (Exception e) {
                if (canRetry(e) && numAttempts == 0) {
                    if (sVerboseLog) {
                        log.warn("Got retryable exception making request to "+url, e);
                    }

                    // Note: the connection doesn't go back in the pool, and we'll close this
                    // one, it's probably no good anyway...
                    if (conn != null) {
                        conn.close();
                        cp.returnConnection(conn);
                    }
                } else {
                    if (numAttempts >= 5) {
                        log.error("Got "+numAttempts+" retryable exceptions (giving up) making request to"+url, e);
                    } else {
                        log.error("Got non-retryable exception making request to "+uri, e);
                    }

                    throw new ApiException("Error performing "+method+" "+url, e);
                }
            }
        }
    }

    public static RequestManagerState getCurrentState() {
        RequestManagerState state = new RequestManagerState();
        ConnectionPool cp = getConnectionPool();
        if (cp == null) {
            return state;
        }
        state.numInProgressRequests = cp.getNumBusyConnections();
        state.lastUri = cp.getLastUri();
        return state;
    }

    public static void addResponseReceivedHandler(ResponseReceivedHandler handler) {
        sResponseReceivedHandlers.add(handler);
    }

    private static void fireResponseReceivedHandlers(BasicHttpRequest request,
            BasicHttpResponse response) throws RequestRetryException {
        for(ResponseReceivedHandler handler : sResponseReceivedHandlers) {
            handler.onResponseReceived(request, response);
        }
    }

    public static void addRequestManagerStateChangedHandler(RequestManagerStateChangedHandler handler) {
        sRequestManagerStateChangedHandlers.add(handler);
    }

    public static void removeRequestManagerStateChangedHandler(RequestManagerStateChangedHandler handler) {
        sRequestManagerStateChangedHandlers.remove(handler);
    }

    private static void fireRequestManagerStateChangedHandlers() {
        for(RequestManagerStateChangedHandler handler : sRequestManagerStateChangedHandlers) {
            handler.onStateChanged();
        }
    }

    /**
     * Determines whether the given exception is re-tryable or not.
     */
    private static boolean canRetry(Exception e) {
        if (e instanceof RequestRetryException) {
            return true;
        }

        if (e instanceof ConnectException) {
            return false;
        }

        // may be others that we can't, but we'll just rety everything for now
        return true;
    }

    /**
     * Register this interface to be notified of every HTTP response. You can do this, for example,
     * to check for authentication errors and automatically re-authenticate.
     */
    public interface ResponseReceivedHandler {
        void onResponseReceived(BasicHttpRequest request,
                                BasicHttpResponse response)
                throws RequestRetryException;
    }

    /**
     * Represents the current "state" of the request manager, and gets passed
     * to any request manager state changed handlers.
     */
    public static class RequestManagerState {
        public int numInProgressRequests;
        public String lastUri;
    }

    /**
     * Handler that's called whenever the state of the request manager changes
     * (e.g. a new request is made, a request completes, etc).
     */
    public interface RequestManagerStateChangedHandler {
        void onStateChanged();
    }

    /**
     * Wraps the result of a request that we've made.
     */
    public static class ResultWrapper {
        private HttpResponse mResponse;
        private Connection mConnection;

        public ResultWrapper(Connection conn, HttpResponse resp) {
            mConnection = conn;
            mResponse = resp;
        }

        public HttpResponse getResponse() {
            return mResponse;
        }

        public void close() {
            // make sure we've finished with the entity...
            try {
                mResponse.getEntity().consumeContent();
            } catch (IOException e) {
                // ignore....
            }

            mConnection.getConnectionPool().returnConnection(mConnection);

            fireRequestManagerStateChangedHandlers();
        }
    }

    /**
     * A wrapper around \c HttpClientConnection that remembers the last
     * URL we requested.
     */
    private static class Connection {
        private HttpClientConnection mHttpClientConnection;
        private String mLastUri;
        private ConnectionPool mConnectionPool;

        public Connection(ConnectionPool cp, HttpClientConnection httpClientConnection) {
            mConnectionPool = cp;
            mHttpClientConnection = httpClientConnection;
            mLastUri = "";
        }

        public BasicHttpResponse sendRequest(HttpRequest request, HttpEntity body)
                        throws HttpException, IOException {
            mLastUri = request.getRequestLine().getUri();

            mHttpClientConnection.sendRequestHeader(request);
            if (body != null) {
                mHttpClientConnection.sendRequestEntity((BasicHttpEntityEnclosingRequest) request);
            }
            mHttpClientConnection.flush();

            BasicHttpResponse response = (BasicHttpResponse) mHttpClientConnection.receiveResponseHeader();
            mHttpClientConnection.receiveResponseEntity(response);

            return response;
        }

        public ConnectionPool getConnectionPool() {
            return mConnectionPool;
        }

        public HttpConnectionMetrics getMetrics() {
            return mHttpClientConnection.getMetrics();
        }

        public String getLastUri() {
            return mLastUri;
        }

        public boolean isOpen() {
            return mHttpClientConnection.isOpen();
        }

        public void close() {
            try {
                mHttpClientConnection.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * A pool of connections to the server. So we don't have to reconnect over-and-over.
     */
    private static class ConnectionPool {
        private final static Logger log = LoggerFactory.getLogger(ConnectionPool.class);
        private Stack<Connection> mFreeConnections;
        private List<Connection> mBusyConnections;
        private SocketFactory mSocketFactory;
        private String mHost;
        private HostnameVerifier mHostnameVerifier;
        private int mPort;
        private static SSLContext mSslContext;

        public ConnectionPool(boolean ssl, String host, int port) {
            mFreeConnections = new Stack<Connection>();
            mBusyConnections = new ArrayList<Connection>();
            if (ssl) {
                if (mSslContext != null) {
                    mSocketFactory = mSslContext.getSocketFactory();
                } else {
                    mSocketFactory = SSLSocketFactory.getDefault();
                }
                mHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
            } else {
                mSocketFactory = SocketFactory.getDefault();
            }
            mHost = host;

            mPort = port;
            if (port <= 0) {
                mPort = (ssl ? 443 : 80);
            }

            if (ssl) {
                setupRootCa();
            }
        }

        public int getNumBusyConnections() {
            synchronized(mBusyConnections) {
                return mBusyConnections.size();
            }
        }

        public String getLastUri() {
            synchronized(mBusyConnections) {
                if (mBusyConnections.isEmpty()) {
                    return "";
                }

                Connection conn = mBusyConnections.get(0);
                return conn.getLastUri();
            }
        }

        /**
         * Gets an already-open socket or creates a new one.
         * @throws IOException 
         * @throws UnknownHostException 
         */
        public Connection getConnection(boolean forceCreate)
                throws UnknownHostException, IOException {
            Connection conn = null;
            if (!forceCreate) {
                synchronized (mFreeConnections) {
                    while (!mFreeConnections.isEmpty()) {
                        conn = mFreeConnections.pop();

                        if (sVerboseLog) {
                            HttpConnectionMetrics metrics = conn.getMetrics();
                            log.debug(String.format("Got connection [%s] from free pool (%d requests," +
                                                    " %d responses, %d bytes sent, %d bytes received).",
                                      conn,
                                      metrics.getRequestCount(), metrics.getResponseCount(),
                                      metrics.getSentBytesCount(), metrics.getReceivedBytesCount()));
                        }
                    }
                }
            } else if (sVerboseLog) {
                log.debug("Didn't look in connection pool: forceCreate = true");
            }

            if (conn == null) {
                conn = createConnection();
            }

            synchronized (mBusyConnections) {
                mBusyConnections.add(conn);
            }

            return conn;
        }

        public void returnConnection(Connection conn) {
            synchronized (mBusyConnections) {
                mBusyConnections.remove(conn);
            }
            
            if (!conn.isOpen()) {
                return;
            }

            synchronized (mFreeConnections) {
                mFreeConnections.push(conn);

                if (sVerboseLog) {
                    HttpConnectionMetrics metrics = conn.getMetrics();
                    log.debug(String.format("Returned connection [%s] to free pool (%d requests," +
                                            " %d responses, %d bytes sent, %d bytes received).",
                              conn,
                              metrics.getRequestCount(), metrics.getResponseCount(),
                              metrics.getSentBytesCount(), metrics.getReceivedBytesCount()));
                }
            }
        }

        /**
         * Creates a new connection to the server.
         * @throws IOException 
         * @throws UnknownHostException 
         */
        private Connection createConnection() throws UnknownHostException, IOException {
            Socket s = mSocketFactory.createSocket(mHost, mPort);

            // Verify that the certicate hostname is for mail.google.com
            // This is due to lack of SNI support in the current SSLSocket.
            if (mHostnameVerifier != null) {
                SSLSocket sslSocket = (SSLSocket) s;
                SSLSession sslSession = sslSocket.getSession();
                if (!mHostnameVerifier.verify(mHost, sslSession)) {
                    throw new SSLHandshakeException("Expected " + mHost + ", found "
                            + sslSession.getPeerPrincipal());
                }
            }

            BasicHttpParams params = new BasicHttpParams();
            DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
            conn.bind(s, params);

            if (sVerboseLog) {
                log.debug(String.format("Connection [%s] to %s:%d created.",
                                        conn, s.getInetAddress().toString(), mPort));
            }
            return new Connection(this, conn);
        }

        /**
         * Loads the Root CA from memory and uses that instead of the system-installed one. This is more
         * secure (because it can't be spoofed if a CA is compromized). It's also compatible with more
         * devices (since not all devices have the RapidSSL CA installed, it seems).
         */
        private static void setupRootCa() {
            log.info("Setting up root certificate to our own custom CA");
            try {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                InputStream ins = App.i.getResources().openRawResource(R.raw.server_keystore);
                Certificate ca;
                try {
                    ca = cf.generateCertificate(ins);
                } finally {
                    ins.close();
                }

                // Create a KeyStore containing our trusted CAs
                String keyStoreType = KeyStore.getDefaultType();
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                keyStore.load(null, null);
                keyStore.setCertificateEntry("ca", ca);

                // Create a TrustManager that trusts the CAs in our KeyStore
                String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                TrustManagerFactory tmf;
                tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                tmf.init(keyStore);

                // Create an SSLContext that uses our TrustManager
                mSslContext = SSLContext.getInstance("TLS");
                mSslContext.init(null, tmf.getTrustManagers(), null);
            } catch (Exception e) {
                log.error("Error setting up SSLContext", e);
            }
        }
    }
}
