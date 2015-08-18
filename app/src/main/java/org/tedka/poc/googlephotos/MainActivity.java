package org.tedka.poc.googlephotos;

import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.io.IOException;
import java.util.ArrayList;

import roboguice.activity.RoboActionBarActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectResource;
import roboguice.inject.InjectView;

@ContentView(R.layout.activity_main)
public class MainActivity extends RoboActionBarActivity {

    @InjectView(R.id.tvNoAlbums)        TextView txtNoAlbums;
    @InjectView(R.id.progress)          ProgressBar progressLoadMore;
    @InjectView(R.id.photoGrid)         GridView albumGrid;
    @InjectView(R.id.statusText)        TextView statusText;
    @InjectView(R.id.full_image_box)    View expandedImageContainer;
    @InjectView(R.id.full_image)        ImageView expandedImageView;
    @InjectView(R.id.full_image_title)  TextView expandedImageTitle;
    @InjectView(R.id.info)              TextView expandedImagePrompt;
    @InjectView(R.id.btnSearch)         Button btnSearch;

    @InjectResource(android.R.integer.config_shortAnimTime) int animationDuration;

    private ImageAdapter imageAdapter;
    private ProgressDialog progressDialog;
    private Animator animator;

    public static int currentPage = 1;
    private int lastItem = 0;
    private boolean endOfResultsDisplay = false;
    private String googleAuthToken;
    private String googleAccountName;

    /** Instance of the Google Play controller */
    private GDController GDController;

    /** Tag for Logging messages */
    private String TAG = "MainActivity";


    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the UI elements, setup progressbars etc.
        initUIElements();

        // Get a reference to the Google Play controller
        GDController = GDController.getInstance();

        // Initialize all the image handling stuff - cache, grid etc.
        initImageHandling();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Choosing Google Account to be used");
        chooseGoogleAccount();
    }

    /**
     * Initialize all the UI elements
     */
    private void initUIElements() {
        progressLoadMore.setVisibility(View.GONE);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Fetching; please wait...");
        progressDialog.setCancelable(false);

        statusText.setText("Preparing API parameters...");
        btnSearch.setEnabled(false);
    }

    /**
     * Initialize all the Image Handling items - cache, grid, adapter etc.
     */
    private void initImageHandling() {

        imageAdapter = new ImageAdapter(this, GDController);
        albumGrid.setAdapter(imageAdapter);
        albumGrid.setFastScrollEnabled(true);

        final int imageThumbSize = getResources().getDimensionPixelSize(R.dimen.photo_thumbnail_size);
        final int imageThumbSpacing = getResources().getDimensionPixelSize(R.dimen.photo_thumbnail_spacing);


        // Determine final width of the GridView and calculate number of columns and its width.
        albumGrid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (imageAdapter.getNumColumns() == 0) {
                    final int numColumns = (int) Math.floor(albumGrid.getWidth() / (imageThumbSize + imageThumbSpacing));
                    if (numColumns > 0) {
                        final int columnWidth = (albumGrid.getWidth() / numColumns) - imageThumbSpacing;
                        imageAdapter.setNumColumns(numColumns);
                        imageAdapter.setItemHeight(columnWidth);

                    }
                }
            }
        });

        albumGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View v, int pos, long arg3) {
                // Zoom the image for a full screen view
                performImageZoom(v, pos);
            }
        });

        albumGrid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                /**************************************************************************
                 * Pausing of image download (to ensure smoother scrolling) when flinging
                 * is implicitly taken care in Picasso, so no custom handling required here
                 **************************************************************************/
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

                final int _lastItem = firstVisibleItem + visibleItemCount;
                if (_lastItem > 0 && totalItemCount > 0) {
                    if (_lastItem == GDController.getImageFeed().size() &&
                            !endOfResultsDisplay && lastItem != _lastItem) {
                        lastItem = _lastItem;
                        // Last item is fully visible.
                        fetchAndShowImages();
                    }
                }
            }
        });
    }

    /**
     * This method is invoked when the 'SEARCH' button is tapped. This kicks off a
     * image search with the keyword provided, and loads results into the imageGrid
     *
     * @param view
     */
    public void fireSearch(View view) {
        startNewSearch();
    }

    /**
     * Reset any existing data and start a new search
     */
    private void startNewSearch() {
        GDController.getImageFeed().clear();
        currentPage = 1;
        fetchAndShowImages();
    }

    /**
     * This method does the actual heavy-lifting of displaying the search results on the
     * screen.
     */
    private void fetchAndShowImages() {
        statusText.setVisibility(View.GONE);
        btnSearch.setVisibility(View.GONE);

        if (currentPage == 1) {
            GDController.getImageFeed().clear();
            endOfResultsDisplay = false;
            lastItem = 0;
            progressDialog.show();
        } else {
            progressLoadMore.setVisibility(View.VISIBLE);
        }

        if (Util.isDeviceOnline(getApplicationContext())) {

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    if(currentPage ==1 || (currentPage > 1 && GDController.isMoreAvailable())) {
                        ArrayList<GDController.GDModel> photoResults;
                        // get the photo search results
                        photoResults = GDController.getPhotos(currentPage);
                        if (photoResults.size() > 0)
                            GDController.getImageFeed().addAll(photoResults);
                        else
                            endOfResultsDisplay = true;

                        currentPage++;
                    } else {
                        endOfResultsDisplay = true;
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    if (GDController.getImageFeed().size() > 0) {
                        imageAdapter.notifyDataSetChanged();
                        // Obtain current position to maintain scroll position
                        int currentPosition = albumGrid.getFirstVisiblePosition();

                        // Set new scroll position
                        albumGrid.smoothScrollToPosition(currentPosition + 1, 0);
                    } else
                        txtNoAlbums.setVisibility(View.VISIBLE);

                    progressDialog.dismiss();
                    progressLoadMore.setVisibility(View.GONE);
                }
            }.execute();
        } else {
            Toast.makeText(this, R.string.no_internet, Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
            progressLoadMore.setVisibility(View.GONE);
        }

    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case Constants.REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    isGooglePlayServicesAvailable();
                }
                break;
            case Constants.REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    googleAccountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    fetchAuthToken();
                } else if (resultCode == RESULT_CANCELED) {
                    Log.d(TAG,"Google account unspecified");
                }
                break;
            case Constants.REQUEST_AUTHORIZATION:
                if (resultCode != RESULT_OK) {
                    chooseGoogleAccount();
                }
                break;
            case Constants.REQ_SIGN_IN_REQUIRED:
                if(resultCode == RESULT_OK) {
                    // We had to sign in - now we can finish off the token request.
                    fetchAuthToken();
                }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void setReadyToLoadImages() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText("Ready!");
                btnSearch.setEnabled(true);
            }
        });
    }

    private void fetchAuthToken() {
        if (googleAccountName != null) {
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(Constants.PREF_ACCOUNT_NAME, googleAccountName);
            editor.commit();

            if (Util.isDeviceOnline(getApplicationContext())) {
                new AsyncTask(){

                    @Override
                    protected Object doInBackground(Object[] objects) {
                        try {
                            Log.d(TAG,"Requesting token for account: " +
                                    googleAccountName);
                            googleAuthToken = GoogleAuthUtil.getToken(getApplicationContext(),
                                    googleAccountName, Constants.GPHOTOS_SCOPE);

                            Log.d(TAG, "Received Token: " + googleAuthToken);
                            GDController.setAPIToken(googleAuthToken);
                            setReadyToLoadImages();
                        } catch (IOException e) {
                            Log.e(TAG, e.getMessage());
                        } catch (UserRecoverableAuthException e) {
                            startActivityForResult(e.getIntent(), Constants.REQ_SIGN_IN_REQUIRED);
                        } catch (GoogleAuthException e) {
                            Log.e(TAG, e.getMessage());
                        }
                        return null;
                    }
                }.execute();
            } else {
                Toast.makeText(this, "Device not online", Toast.LENGTH_LONG).show();
            }
        } else {
            chooseGoogleAccount();
        }
    }

    /**
     * Starts an activity in Google Play Services so the user can pick an
     * account. The 4th parameter (force choose) has been set as false so that
     * this is displayed only if multiple accounts are detected on the device
     */
    private void chooseGoogleAccount() {

        String[] accountTypes = new String[]{"com.google"};
        Intent intent = AccountPicker.newChooseAccountIntent(null, null,
                accountTypes, false, null, null, null, null);

        Log.d(TAG,"Starting activity for Choosing Account");
        startActivityForResult(intent, Constants.REQUEST_ACCOUNT_PICKER);
    }

    /**
     * Check that Google Play services APK is installed and up to date. Will
     * launch an error dialog for the user to update Google Play Services if
     * possible.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        final int connectionStatusCode =
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (GooglePlayServicesUtil.isUserRecoverableError(connectionStatusCode)) {
            // Display a dialog showing the connection error
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
                            connectionStatusCode, MainActivity.this,
                            Constants.REQUEST_GOOGLE_PLAY_SERVICES);
                    dialog.show();
                }
            });
            return false;
        } else if (connectionStatusCode != ConnectionResult.SUCCESS ) {
            return false;
        }
        return true;
    }

    /**
     * This method handles displaying the big image, along with the animations involved, when
     * tapped on a thumbnail
     *
     * @param thumbView
     * @param pos
     */
    private void performImageZoom(final View thumbView, int pos) {
        // If there's an animation in progress, cancel it process this.
        if (animator != null) {
            animator.cancel();
        }
        Log.d(TAG, "Loading full image: " + GDController.getImageFeed().get(pos).fullImageLink);
        progressDialog.show();
        Util.getAuthPicasso(this, googleAuthToken)
                .load(GDController.getImageFeed().get(pos).fullImageLink)
                .placeholder(R.drawable.thumbnail_placeholder)
                .error(R.drawable.thumbnail_placeholder)
                .fit()
                .centerInside()
                .into(expandedImageView, new com.squareup.picasso.Callback() {
                    @Override
                    public void onSuccess() {
                        progressDialog.dismiss();
                    }

                    @Override
                    public void onError() {
                        progressDialog.dismiss();
                    }
                });
        expandedImageTitle.setText(GDController.getImageFeed().get(pos).title);

        // Calculate the starting and ending bounds for the zoomed-in image.
        final Rect startBounds = new Rect();
        final Rect finalBounds = new Rect();
        final Point globalOffset = new Point();

        /**************************************************************************
         * The start bounds are the global visible rectangle of the thumbnail,
         * and final bounds are the global visible rectangle of the container view.
         * Set the container view's offset as the origin for the bounds, since
         * that's the origin for the positioning animation properties (X, Y).
         **************************************************************************/
        thumbView.getGlobalVisibleRect(startBounds);
        findViewById(R.id.container).getGlobalVisibleRect(finalBounds, globalOffset);
        startBounds.offset(-globalOffset.x, -globalOffset.y);
        finalBounds.offset(-globalOffset.x+32, -globalOffset.y+32);

        /**************************************************************************
         * Adjust the start bounds to be the same aspect ratio as the final bounds
         * using the "center crop" technique. This prevents undesirable stretching
         * during the animation. Also calculate the start scaling factor (the end
         * scaling factor is always 1.0).
         **************************************************************************/
        float startScale;
        if ((float) finalBounds.width() / finalBounds.height() > (float) startBounds.width() / startBounds.height()) {
            // Extend start bounds horizontally
            startScale = (float) startBounds.height() / finalBounds.height();
            float startWidth = startScale * finalBounds.width();
            float deltaWidth = (startWidth - startBounds.width()) / 2;
            startBounds.left -= deltaWidth;
            startBounds.right += deltaWidth;
        } else {
            // Extend start bounds vertically
            startScale = (float) startBounds.width() / finalBounds.width();
            float startHeight = startScale * finalBounds.height();
            float deltaHeight = (startHeight - startBounds.height()) / 2;
            startBounds.top -= deltaHeight;
            startBounds.bottom += deltaHeight;
        }

        // Hide the thumbnail and show the zoomed-in view.
        thumbView.setAlpha(0f);
        expandedImageContainer.setVisibility(View.VISIBLE);

        // Construct and run the animation
        expandedImageContainer.setPivotX(0f);
        expandedImageContainer.setPivotY(0f);
        AnimatorSet set = new AnimatorSet();
        set.play(ObjectAnimator.ofFloat(expandedImageContainer, View.X, startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(expandedImageContainer, View.Y, startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(expandedImageContainer, View.SCALE_X, startScale, 1f))
                .with(ObjectAnimator.ofFloat(expandedImageContainer, View.SCALE_Y, startScale, 1f));
        set.setDuration(animationDuration);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                expandedImageTitle.setVisibility(View.VISIBLE);
                expandedImagePrompt.setVisibility(View.VISIBLE);
                animator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                animator = null;
            }
        });
        set.start();
        animator = set;

        // Upon clicking the zoomed-in image, it zooms back down to the
        // original bounds and show the thumbnail instead of the expanded image.
        final float startScaleFinal = startScale;
        expandedImageContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (animator != null) {
                    animator.cancel();
                }

                // Animate the four positioning/sizing properties in parallel,
                // back to their original values.
                AnimatorSet set = new AnimatorSet();
                set.play(ObjectAnimator.ofFloat(expandedImageContainer, View.X, startBounds.left))
                        .with(ObjectAnimator.ofFloat(expandedImageContainer, View.Y, startBounds.top))
                        .with(ObjectAnimator.ofFloat(expandedImageContainer, View.SCALE_X, startScaleFinal))
                        .with(ObjectAnimator.ofFloat(expandedImageContainer, View.SCALE_Y, startScaleFinal));
                set.setDuration(animationDuration);
                set.setInterpolator(new DecelerateInterpolator());
                set.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        thumbView.setAlpha(1f);
                        expandedImageContainer.setVisibility(View.GONE);
                        animator = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        thumbView.setAlpha(1f);
                        expandedImageContainer.setVisibility(View.GONE);
                        animator = null;
                    }
                });
                expandedImageTitle.setVisibility(View.GONE);
                expandedImagePrompt.setVisibility(View.GONE);
                set.start();
                animator = set;
            }
        });
    }

}