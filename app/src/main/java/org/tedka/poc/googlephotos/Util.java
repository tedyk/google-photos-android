package org.tedka.poc.googlephotos;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.UrlConnectionDownloader;

import java.io.IOException;
import java.net.HttpURLConnection;

public class Util {

    /**
     * Check if network is available.
     * @param context
     * @return
     */
    public static boolean isDeviceOnline(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }


    /**
     * An instance of Picasso, customized to have Auth header added
     */
    private static Picasso authPicasso = null;

    /**
     * Get a reference to Picasso, modified to add Authorization header to network calls
     * @param ctx
     * @param authToken
     * @return
     */
    public static Picasso getAuthPicasso(Context ctx, final String authToken) {
        if(authPicasso == null) {
            Picasso.Builder builder = new Picasso.Builder(ctx);

            builder.downloader(new UrlConnectionDownloader(ctx) {
                @Override
                protected HttpURLConnection openConnection(Uri uri) throws IOException {
                    HttpURLConnection connection = super.openConnection(uri);
                    connection.setRequestProperty (Constants.HEADER_NAME_AUTH,
                            Constants.HEADER_AUTH_VAL_PRFX + authToken);
                    return connection;
                }
            });

            authPicasso = builder.build();
        }
        return authPicasso;
    }

}
