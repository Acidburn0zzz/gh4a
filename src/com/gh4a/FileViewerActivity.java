/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;

import org.eclipse.egit.github.core.Blob;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.DataService;
import org.eclipse.egit.github.core.util.EncodingUtils;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.gh4a.utils.FileUtils;
import com.gh4a.utils.StringUtils;

/**
 * The DiffViewer activity.
 */
public class FileViewerActivity extends BaseActivity {

    /** The user login. */
    protected String mUserLogin;

    /** The repo name. */
    protected String mRepoName;

    /** The object sha. */
    protected String mObjectSha;
    private String mPath;
    private String mBranchName;
    protected String mName;
    private Blob mBlob;

    /**
     * Called when the activity is first created.
     * 
     * @param savedInstanceState the saved instance state
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.web_viewer);
        setUpActionBar();

        mUserLogin = getIntent().getStringExtra(Constants.Repository.REPO_OWNER);
        mRepoName = getIntent().getStringExtra(Constants.Repository.REPO_NAME);
        mObjectSha = getIntent().getStringExtra(Constants.Object.OBJECT_SHA);
        mPath = getIntent().getStringExtra(Constants.Object.PATH);
        mBranchName = getIntent().getStringExtra(Constants.Repository.REPO_BRANCH);
        mName = getIntent().getStringExtra(Constants.Object.NAME);
        
        TextView tvViewInBrowser = (TextView) findViewById(R.id.tv_in_browser);
        tvViewInBrowser.setVisibility(View.GONE);
        
        TextView tvHistoryFile = (TextView) findViewById(R.id.tv_view);
        tvHistoryFile.setText(getResources().getString(R.string.object_view_history));
        tvHistoryFile.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View view) {
                Intent intent = new Intent().setClass(FileViewerActivity.this, CommitHistoryActivity.class);
                intent.putExtra(Constants.Repository.REPO_OWNER, mUserLogin);
                intent.putExtra(Constants.Repository.REPO_NAME, mRepoName);
                intent.putExtra(Constants.Object.OBJECT_SHA, mBranchName);
                intent.putExtra(Constants.Object.PATH, mPath);
                
                startActivity(intent);
            }
        });
        
        
        TextView tvViewRaw = (TextView) findViewById(R.id.tv_view_raw);
        tvViewRaw.setVisibility(View.VISIBLE);
        tvViewRaw.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View view) {
                TextView tvViewRaw = (TextView) view;
                if ("Raw".equals(tvViewRaw.getText())) {
                    new LoadContentTask(FileViewerActivity.this).execute(false);
                }
                else {
                    new LoadContentTask(FileViewerActivity.this).execute(true);
                }
            }
        });
        
        TextView tvDownload = (TextView) findViewById(R.id.tv_download);
        tvDownload.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View view) {
                String filename = mPath;
                int idx = mPath.lastIndexOf("/");
                
                if (idx != -1) {
                    filename = filename.substring(filename.lastIndexOf("/") + 1, filename.length());
                }

                String data = new String(EncodingUtils.fromBase64(mBlob.getContent()));
                boolean success = FileUtils.save(filename, data);
                if (success) {
                    showMessage("File saved at " + Environment.getExternalStorageDirectory().getAbsolutePath() + "/download/" + filename, false);
                }
                else {
                    showMessage("Unable to save the file", false);
                }
            }
        });
        
        new LoadContentTask(this).execute(true);
    }

    /**
     * An asynchronous task that runs on a background thread to load tree list.
     */
    private static class LoadContentTask extends AsyncTask<Boolean, Integer, Blob> {

        /** The target. */
        private WeakReference<FileViewerActivity> mTarget;

        /** The exception. */
        private boolean mException;

        /** The show in browser. */
        private boolean mShowInBrowser;
        
        private boolean mHighlight;

        /**
         * Instantiates a new load tree list task.
         * 
         * @param activity the activity
         */
        public LoadContentTask(FileViewerActivity activity) {
            mTarget = new WeakReference<FileViewerActivity>(activity);
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Blob doInBackground(Boolean... params) {
            if (mTarget.get() != null) {
                mHighlight = params[0];
                try {
                    FileViewerActivity activity = mTarget.get();
                    GitHubClient client = new GitHubClient();
                    client.setOAuth2Token(mTarget.get().getAuthToken());
                    DataService dataService = new DataService(client);
                    
                    // only show mimetype text/* and xml to WebView, else open
                    // default browser
//                    if (activity.mMimeType.startsWith("text")
//                            || activity.mMimeType.equals("application/xml")
//                            || activity.mMimeType.equals("application/sh")
//                            || activity.mMimeType.equals("application/xhtml+xml")) {
                        mShowInBrowser = false;
                        
                        return dataService.getBlob(new RepositoryId(activity.mUserLogin,
                                activity.mRepoName), activity.mObjectSha);
//                    }
//                    else {
//                        mShowInBrowser = true;
//                        return null;
//                    }
    
                }
                catch (IOException e) {
                    Log.e(Constants.LOG_TAG, e.getMessage(), e);
                    mException = true;
                    return null;
                }
            }
            else {
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Blob result) {
            if (mTarget.get() != null) {
                if (mException) {
                    mTarget.get().showError();
                }
                else {
                    if (mShowInBrowser) {
                        String url = "https://github.com/" + mTarget.get().mUserLogin + "/"
                                + mTarget.get().mRepoName + "/raw/" + mTarget.get().mBranchName + "/"
                                + mTarget.get().mPath;
                        mTarget.get().getApplicationContext().openBrowser(mTarget.get(), url);
                        mTarget.get().finish();
                    }
                    else {
                        mTarget.get().mBlob = result;
                        try {
                            mTarget.get().fillData(result, mHighlight);
                        } catch (IOException e) {
                            mTarget.get().showError();
                        }
                    }
                }
            }
        }
    }

    /**
     * Fill data into UI components.
     * 
     * @param is the is
     * @throws IOException 
     */
    protected void fillData(Blob blob, boolean highlight) throws IOException {
        String data = new String(EncodingUtils.fromBase64(blob.getContent()));
        TextView tvViewRaw = (TextView) findViewById(R.id.tv_view_raw);
        if (highlight) {
            tvViewRaw.setText("Raw");
        }
        else {
            tvViewRaw.setText("Highlight");
        }
        
        WebView webView = (WebView) findViewById(R.id.web_view);

        WebSettings s = webView.getSettings();
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        s.setUseWideViewPort(false);
        s.setAllowFileAccess(true);
        s.setBuiltInZoomControls(true);
        s.setLightTouchEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setPluginsEnabled(false);
        s.setSupportZoom(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptEnabled(true);

        // webView.setWebViewClient(new WebChrome2());
        webView.getSettings().setUseWideViewPort(true);

        String content;
        //try {
            //content = StringUtils.convertStreamToString(is);
            String highlighted = StringUtils.highlightSyntax(data, highlight, mName);
            webView.setWebViewClient(webViewClient);
            webView.loadDataWithBaseURL("file:///android_asset/", highlighted, "text/html", "utf-8", "");
        //}
        //catch (IOException e) {
        //    Log.e(Constants.LOG_TAG, e.getMessage(), e);
        //    showError();
        //}
    }

    private WebViewClient webViewClient = new WebViewClient() {

        @Override
        public void onPageFinished(WebView webView, String url) {
        }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }
    };

}
