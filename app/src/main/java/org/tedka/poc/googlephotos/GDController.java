package org.tedka.poc.googlephotos;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Controller class to abstract the logic of interfacing with Google Drive APIs
 */
public class GDController {

    /** Hold the Google Auth token */
    private String googleAuthToken = null;

    /** DataStructure to hold search results from Google Photos */
    private ArrayList<GDModel> imageFeed = new ArrayList<>();

    /** Singleton instance of this controller **/
    private static GDController myInstance = null;

    /** URL to the paginated next set of images */
    private String nextLink = null;

    /** Tag for Logging messages */
    private String TAG = "GDController";

    /**
     * Placeholder singleton constructor
     */
    private GDController() {}

    /**
     * Return the singleton instance of Google Play Controller
     *
     * @return
     */
    public static GDController getInstance() {
        if(myInstance == null) {
            myInstance = new GDController();
        }
        return myInstance;
    }

    /**
     * Set the Auth Token to be used; this is to be set before firing the getPhotos() method
     * @param token
     */
    public void setAPIToken(String token) {
        googleAuthToken = token;
    }

    /**
     * Get the list of Image data objects
     * @return
     */
    public ArrayList<GDModel> getImageFeed() {
        return imageFeed;
    }

    /**
     * Check if more images are available to be loaded
     * @return
     */
    public boolean isMoreAvailable() {
        return (nextLink == null)? false:true;
    }

    /**
     * Get the photos response from server, and parse the JSON response and populate the
     * DataStructure that is to hold the results
     *
     * @param currentPage
     * @return
     */
    public ArrayList<GDModel> getPhotos(int currentPage) {

        ArrayList<GDModel> photos = new ArrayList<>();

        String serverResponse = fetchFromServer(getUrl(currentPage));

        if (!serverResponse.isEmpty()) {
            try {

                JSONObject response = new JSONObject(serverResponse);
                // If response doesnt have nextLink, then set the variable to null so that
                // we can detect that there are no more pages to come
                if(response.has(Constants.JSON_FIELD_NEXT_LINK)) {
                    nextLink = response.getString(Constants.JSON_FIELD_NEXT_LINK);
                } else {
                    nextLink = null;
                }
                // Extract the JSONArray containing objects representing each image.
                JSONArray itemsArray = response.getJSONArray(Constants.JSON_FIELD_ITEMS);
                Log.d(TAG,"Received " + itemsArray.length() + " photos");
                String mimeType;

                // Iterate the list of images, extract information and build the photo list
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject jObj = itemsArray.getJSONObject(i);

                    //Result will have images and videos. Skip if a video is encountered
                    mimeType = jObj.getString(Constants.JSON_FIELD_MIME_TYPE);
                    if(null != mimeType && mimeType.startsWith(Constants.JSON_FIELD_MIME_IMG)) {
                        GDModel photo = new GDModel();
                        photo.title = jObj.getString(Constants.JSON_FIELD_TITLE);
                        photo.thumbnailLink = jObj.getString(Constants.JSON_FIELD_THUMBNAIL);
                        photo.fullImageLink = jObj.getString(Constants.JSON_FIELD_SELF_LINK).trim() +
                                Constants.URL_FILE_EXTN + Constants.URL_EXTN_API_KEY;
                        photos.add(photo);
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return photos;
    }


    /**
     * Construct and return the search url
     *
     * @param currentPage
     * @return
     */
    private String getUrl(int currentPage) {
        String url = null;
        // If its the initial API inocation, construct the URL for Files API, else, use the 'nextLink'
        if(currentPage == 1) {
            url = Constants.URL_FILES + Constants.URL_FILES_FIELDS + Constants.URL_EXTN_API_KEY;
        } else {
            url = nextLink + Constants.URL_EXTN_API_KEY;
        }
        return url;
    }

    /**
     * Invoke the API URL, extract images out of the whole response, construct image objects
     * and populate them into the imageFeed ArrayList.
     * @param strURL
     * @return
     */
    private String fetchFromServer(String strURL) {
        StringBuffer response = new StringBuffer();
        HttpURLConnection urlConnection = null;
        String line = "";

        try {
            URL url = new URL(strURL);
            urlConnection = (HttpURLConnection) url.openConnection();
            try {
                // Add the AuthToken to header
                urlConnection.setRequestProperty (Constants.HEADER_NAME_AUTH,
                        Constants.HEADER_AUTH_VAL_PRFX + googleAuthToken);
            } catch (Exception e) {
                Log.e(TAG,e.getMessage());
            }
            InputStream in;
            int status = urlConnection.getResponseCode();
            Log.d(TAG,"Server response: " + status);

            if(status >= 400)
                in = urlConnection.getErrorStream();
            else
                in = urlConnection.getInputStream();

            BufferedReader rd = new BufferedReader(new InputStreamReader(in));
            while ((line = rd.readLine()) != null) {
                response.append(line);
            }
        } catch (IOException e) {
            Log.d(TAG,e.getMessage());
        } finally {
            urlConnection.disconnect();
        }
        Log.d(TAG,response.toString());

        return response.toString();
    }

    /**
     * Data model for a photo object
     */
    class GDModel {
        public String title;
        public String thumbnailLink;
        public String fullImageLink;
    }
}
