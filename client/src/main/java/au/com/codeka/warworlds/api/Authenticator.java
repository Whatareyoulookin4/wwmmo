package au.com.codeka.warworlds.api;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

import java.io.IOException;

import javax.annotation.Nullable;

import au.com.codeka.common.Log;
import au.com.codeka.warworlds.App;
import au.com.codeka.warworlds.MainActivity;
import au.com.codeka.warworlds.Util;
import au.com.codeka.warworlds.model.Realm;
import okhttp3.Response;

public class Authenticator {
  private static final Log log = new Log("Authenticator");

  private String authCookie;
  private boolean authenticating;

  public boolean isAuthenticated() {
    return (authCookie != null);
  }

  public void logout() {
    authCookie = null;
  }

  public String getAuthCookie() {
    return authCookie;
  }

  /**
   * Authenticates the given user with the server.
   *
   * @param activity The activity we're current attached to, this can be null in which case we will
   *                 not prompt for authorization.
   * @param realm The {@link Realm} we're connecting to.
   * @return A cookie we can use in subsequent calls to the server.
   * @throws ApiException
   */
  public boolean authenticate(@Nullable Activity activity, Realm realm) throws ApiException {
    // Make sure we don't try to authenticate WHILE WE'RE AUTHENTICATING.
    if (authenticating) {
      return true;
    }
    authenticating = true;

    realm.update();

    SharedPreferences prefs = Util.getSharedPreferences();
    final String accountName = prefs.getString("AccountName", null);
    if (accountName == null) {
      authenticating = false;
      throw new ApiException("No account has been selected yet!");
    }

    Context context = App.i;
    log.info("(re-)authenticating \"%s\" to realm %s...", accountName, realm.getDisplayName());
    String cookie = null;
    if (accountName.endsWith("@anon.war-worlds.com")) {
      // If it's an anonymous account, there's no password/authentication required. We just pass
      // the email address directly as the cookie.
      cookie = String.format("SESSION=%s", accountName.replace('@', '_'));
      authenticating = false;
    } else {
      try {
        final String scope = "oauth2:email";
        log.info("Getting token for \"%s\" & scope \"%s\"...", accountName, scope);
        String authToken = GoogleAuthUtil.getToken(context, accountName, scope);
        if (authToken == null) {
          log.error("Failed to get auth token.");
          throw new ApiException("Error getting auth token.");
        }
        log.info("Got token: %s", authToken);
        cookie = getCookie(authToken, realm);
        log.info("Authentication successful.");
      } catch (UserRecoverableAuthException e) {
        // If it's a 'recoverable' exception, we need to start the given intent and then try again.
        if (activity == null) {
          throw new ApiException("Cannot retry, no activity given.", e);
        }
        Intent intent = e.getIntent();
        activity.startActivityForResult(intent, MainActivity.AUTH_RECOVERY_REQUEST);
        log.warning("Got UserRecoverableAuthException, TODO");
      } catch (GoogleAuthException | IOException e) {
        log.error("Exception: ", e);
        throw new ApiException(e);
      } finally {
        authenticating = false;
      }
    }
    authCookie = cookie;
    return true;
  }

  /**
   * Makes a request to the server to get a cookie which we can send with each subsequent request.
   */
  public String getCookie(String authToken, Realm realm) {
    String url = realm.getBaseUrl().resolve("login?authToken=" + authToken).toString();

    String impersonate = Util.getProperties().getProperty("user.on_behalf_of", null);
    if (impersonate != null) {
      url += "&impersonate=" + impersonate;
    }
    log.info("url: " + url);

    ApiRequest request = new ApiRequest.Builder(url, "GET")
        .errorCallback((request1, error) -> log.error("Got error from login request: %d %s",
            error.getErrorCode(), error.getErrorMessage())).build();
    Response resp = RequestManager.i.sendRequestSync(request);
    if (resp != null && !resp.isSuccessful()) {
      log.error("Got invalid response: " + resp.message());
      return null;
    }
    String cookie = request.bodyString();
    if (cookie == null) {
      return null;
    }
    return "SESSION=" + cookie;
  }
}
