package org.ometa.lovemonster.service;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.json.JSONObject;
import org.ometa.lovemonster.Logger;
import org.ometa.lovemonster.models.Love;
import org.ometa.lovemonster.models.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.annotation.NotThreadSafe;

/**
 * Client which makes requests to the Love Monster web service. For normal usage, this class is
 * intended to be used by using the singleton method {@link LoveMonsterClient#getInstance()} to
 * return a singleton instance. **N.B.** This singleton is not threadsafe due to dependencies on
 * {@link AsyncHttpClient}.
 *
 * {@code
 *      final LoveMonsterClient client = LoveMonsterClient.getInstance();
 *      client.retrieveRecentLoves(new LoveListResponseHandler() {... });
 * }
 *
 */
@NotThreadSafe
public class LoveMonsterClient {

    /**
     * Handler for response callbacks from the {@link LoveMonsterClient} for calls which retrieve loves.
     */
    public interface LoveListResponseHandler {
        /**
         * Invoked when the request successfully completes.  The passed loves may be empty, but cannot
         * be null.
         *
         * @param loves
         *      the resulting loves
         */
        void onSuccess(@NonNull List<Love> loves, int totalPages);

        /**
         * Handler when a request fails.
         */
        void onFail();
    }

    /**
     * Handler for response callbacks from the {@link LoveMonsterClient} for calls which return a single love.
     */
    public interface LoveResponseHandler {
        /**
         * Invoked when the request successfully completes.  The passed love will not be null.
         *
         * @param love
         *      the resulting love
         */
        void onSuccess(@NonNull Love love);

        /**
         * Handler when a request fails.
         *
         * @param errorMessages
         *      the error messages from the server
         */
        void onFail(@NonNull List<String> errorMessages);
    }

    /**
     * The singleton instance for this client. Because of {@link AsyncHttpClient}, this instance
     * is *NOT* threadsafe.
     */
    private static final LoveMonsterClient singletonInstance = new LoveMonsterClient();

    /**
     * Logger used by this class.
     */
    private static final Logger logger = new Logger(LoveMonsterClient.class);

    /**
     * Returns the singleton {@code LoveMonsterClient} instance.  Because of {@link AsyncHttpClient}, this
     * instance is *NOT* threadsafe.
     *
     * @return
     *      the singleton {@code LoveMonsterClient}
     */
    public static LoveMonsterClient getInstance() {
        return singletonInstance;
    }

    /**
     * The host to use to make requests. This field *must* have a trailing slash.
     */
    private final String host;

    /**
     * The parser used to convert responses from the server into model objects.
     */
    @NonNull
    private final ResponseParser responseParser;

    /**
     * The client used to make asynchronous http requests.
     */
    @NonNull
    private final AsyncHttpClient asyncHttpClient;

    /**
     * Private constructor used to implement the singleton. Purposely not made protected to avoid
     * breaking the singleton.
     * Defaults to use newly instantiated {@link ResponseParser} and {@link AsyncHttpClient} objects.
     */
    private LoveMonsterClient() {
            this(new ResponseParser(), new AsyncHttpClient(), "http://love.snc1/");
    }

    /**
     * Protected constructor used to create an instance. Is protected scope to allow overriding and
     * easier unit testing.
     *
     * @param responseParser
     *      the response parser to use to parse requests
     * @param asyncHttpClient
     *      the http client used to make requests
     * @param host
     *      the host to make requests to.
     * @throws IllegalArgumentException
     *      if {@code responseParser}, {@code asyncHttpClient}, or @{code host} are {@code null}
     */
    protected LoveMonsterClient(@NonNull final ResponseParser responseParser, @NonNull final AsyncHttpClient asyncHttpClient, @NonNull final String host) throws IllegalArgumentException {
        if (responseParser == null) {
            throw new IllegalArgumentException("argument `responseParser` cannot be null");
        }
        if (asyncHttpClient == null) {
            throw new IllegalArgumentException("argument `asyncHttpClient` cannot be null");
        }
        if (host == null) {
            throw new IllegalArgumentException("argument `host` cannot be null");
        }

        this.responseParser = responseParser;
        this.asyncHttpClient = asyncHttpClient;
        this.host = host;
    }

    /**
     * Retrieves recent loves asynchronously. Takes a {@link LoveListResponseHandler} which will be
     * invoked on response completion.
     *
     * @param loveListResponseHandler
     *      the response handler to use on response completion
     * @param page
     *      the page of results to send
     */
    public void retrieveRecentLoves(@NonNull final LoveListResponseHandler loveListResponseHandler, final int page) {
        retrieveRecentLoves(loveListResponseHandler, page, null);
    }

    /**
     * Retrieves recent loves asynchronously. Takes a {@link LoveListResponseHandler} which will be
     * invoked on response completion. Will filter results if {@code user} is specified, returning
     * *all* {@link Love}s sent to or sent by the specified user.
     *
     *  @param loveListResponseHandler
     *      the response handler to use on response completion
     * @param page
     *      the page of results to send
     * @param user
     *      the user to use for filtering results.  if null, no filtering will occur
     */
    public void retrieveRecentLoves(@NonNull final LoveListResponseHandler loveListResponseHandler, final int page, @Nullable final User user) {
        retrieveRecentLoves(loveListResponseHandler, page, user, null);
    }

    /**
     * Retrieves recent loves asynchronously. Takes a {@link LoveListResponseHandler} which will be
     * invoked on response completion. Will filter results if {@code user} is specified, returning
     * *all* {@link Love}s sent to or sent by the specified user, unless {@code userLoveAssociation}
     * is specified which will further constrain the results to loves *either* sent to or sent by the
     * specified user.
     *
     *  @param loveListResponseHandler
     *      the response handler to use on response completion
     * @param page
     *      the page of results to send
     * @param user
     *      the user to use for filtering results.  if null, no filtering will occur
     * @param userLoveAssociation
     *      used to indicate how results should be filtered for a user.  if null or
     *      {@link User.UserLoveAssociation#all}, then all loves sent or received to that
     *      user will be returned.  if {@link User.UserLoveAssociation#lover}, then only
     *      loves sent *by* the user (i.e. where the specified user is the lover) will be returned.
     *      likewise, if {@link User.UserLoveAssociation#lovee} is specified, then only
     *      loves *received* by the user (i.e. where the user is the lovee) will be returned.
     *      this value is only valid to be passed if {@code user} is also passed, but you may omit
     *      this value even if user is passed.
     * @throws IllegalArgumentException
     *      if {@code userLoveAssociation} is passed, but {@code user} is not passed
     */
    public void retrieveRecentLoves(@NonNull final LoveListResponseHandler loveListResponseHandler, final int page, @Nullable final User user, @Nullable final User.UserLoveAssociation userLoveAssociation) throws IllegalArgumentException {
        if (userLoveAssociation != null && user == null) {
            throw new IllegalArgumentException("cannot specify a `userLoveAssociation` without a `user`");
        }

        final String url = buildUrl("api/v1/loves");
        final RequestParams params = buildParams();
        params.put("page", page);
        if (user != null) {
            params.put("user_id", user.username);
            if (userLoveAssociation == User.UserLoveAssociation.lovee) {
                params.put("filter", "to");
            } else if (userLoveAssociation == User.UserLoveAssociation.lover) {
                params.put("filter", "from");
            }
        }

        logger.debug("method=retrieveRecentLoves url=" + url + " params=" + params.toString());

        try {
            asyncHttpClient.get(url, params, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(final int statusCode, final Header[] headers, final JSONObject response) {
                    final String responseBody;
                    int totalPages = 0;

                    if (response == null) {
                        responseBody = "null";
                    } else {
                        responseBody = response.toString();
                        final JSONObject metaJsonObject = response.optJSONObject("meta");
                        if (metaJsonObject != null) {
                            totalPages = metaJsonObject.optInt("total_pages", 0);
                        }
                    }

                    logger.debug("method=retrieveRecentLoves url=" + url + " handler=onSuccess statusCode=" + statusCode + " response=" + responseBody);
                    loveListResponseHandler.onSuccess(responseParser.parseLoveList(response), totalPages);
                }

                @Override
                public void onFailure(int statusCode, final Header[] headers, final Throwable throwable, final JSONObject errorResponse) {
                    final String responseBody;
                    if (errorResponse == null) {
                        responseBody = "null";
                    } else {
                        responseBody = errorResponse.toString();
                    }

                    logger.debug("method=retrieveRecentLoves url=" + url + "  handler=onFailure statusCode=" + statusCode + " response=" + responseBody, throwable);
                    loveListResponseHandler.onFail();
                }
            });
        } catch (final Exception e){
            logger.debug("method=retrieveRecentLoves url=" + url, e);
            loveListResponseHandler.onFail();
        }
    }

    /**
     * Creates a new love on the server. If an error occurs, error messages will be passed to the
     * failure handler. Otherwise, the created love will be returned on the success handler.
     *
     * @param love
     *      the love to creeate
     * @param loveResponseHandler
     *      the handler for the response
     * @throws IllegalArgumentException
     *      if the specified love is null
     */
    public void makeLove(@NonNull final Love love, @NonNull final LoveResponseHandler loveResponseHandler) throws IllegalArgumentException {
        if (love == null) {
            throw new IllegalArgumentException("argument `love` cannot be null");
        }

        final String url = buildUrl("api/v1/loves");
        final RequestParams params = buildParams();
        params.put("reason", love.reason);
        params.put("message", love.message);
        if (love.lovee != null) {
            params.put("to", love.lovee.username);
        }
        if (love.lover != null) {
            params.put("from", love.lover.username);
        }
        params.put("private_message", love.isPrivate);

        logger.debug("method=makeLove url=" + url + " params=" + params.toString());

        try {
            asyncHttpClient.post(url, params, new JsonHttpResponseHandler() {
                @Override
                public void onSuccess(final int statusCode, final Header[] headers, final JSONObject response) {
                    final String responseBody;
                    if (response == null) {
                        responseBody = "null";
                    } else {
                        responseBody = response.toString();
                    }

                    logger.debug("method=makeLove url=" + url + " handler=onSuccess statusCode=" + statusCode + " response=" + responseBody);
                    loveResponseHandler.onSuccess(love);
                }

                @Override
                public void onFailure(int statusCode, final Header[] headers, final Throwable throwable, final JSONObject errorResponse) {
                    final String responseBody;
                    final List<String> errorMessages = new ArrayList<>();

                    if (errorResponse == null) {
                        responseBody = "null";
                    } else {
                        responseBody = errorResponse.toString();
                        final String errors = errorResponse.optString("errors", null);
                        if (errors != null) {
                            errorMessages.add(errors);
                        }
                    }

                    if (throwable != null) {
                        errorMessages.add(throwable.getLocalizedMessage());
                    }

                    logger.debug("method=makeLove url=" + url + "  handler=onFailure statusCode=" + statusCode + " response=" + responseBody, throwable);
                    loveResponseHandler.onFail(errorMessages);
                }
            });
        } catch (final Exception e){
            logger.debug("method=makeLove url=" + url, e);
            loveResponseHandler.onFail(Arrays.asList(e.getLocalizedMessage()));
        }
    }

    /**
     * Returns the authenticated in {@link User}.
     * @return
     */
    public User getAuthenticatedUser() {
        return new User("anthony@groupon.com", "anthony");
    }

    /**
     * Builds the full url from the specified path.
     *
     * @param path
     *      the path for the url
     * @return
     *      the full url
     */
    private String buildUrl(final String path) {
        return host + path;
    }

    /**
     * Builds request params to be used in making requests.
     * @return
     *      the default request params
     */
    private RequestParams buildParams() {
        final RequestParams params = new RequestParams();
        params.put("clientId", "androidapp");
        return  params;
    }
}
