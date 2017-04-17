package com.gh4a.widget;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.ListPopupWindow;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.gh4a.DefaultClient;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.activities.UserActivity;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.AvatarHandler;
import com.gh4a.utils.UiUtils;

import org.eclipse.egit.github.core.Reaction;
import org.eclipse.egit.github.core.Reactions;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ReactionService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class ReactionBar extends LinearLayout implements PopupWindow.OnDismissListener {
    public interface ReactionDetailsProvider {
        List<Reaction> loadReactionDetailsInBackground(Object item) throws IOException;
        void addReactionInBackground(GitHubClient client,
                Object item, String content) throws IOException;
    }

    private TextView mPlusOneView;
    private TextView mMinusOneView;
    private TextView mLaughView;
    private TextView mHoorayView;
    private TextView mConfusedView;
    private TextView mHeartView;

    private ReactionDetailsProvider mProvider;
    private Object mReferenceItem;
    private ListPopupWindow mPopup;
    private long mLastPopupDismissTime;

    public ReactionBar(Context context) {
        this(context, null);
    }

    public ReactionBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReactionBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setOrientation(HORIZONTAL);
        inflate(context, R.layout.reaction_bar, this);

        mPlusOneView = (TextView) findViewById(R.id.plus_one);
        mMinusOneView = (TextView) findViewById(R.id.minus_one);
        mLaughView = (TextView) findViewById(R.id.laugh);
        mHoorayView = (TextView) findViewById(R.id.hooray);
        mConfusedView = (TextView) findViewById(R.id.confused);
        mHeartView = (TextView) findViewById(R.id.heart);

        setReactions(null);
    }

    public void setReactions(Reactions reactions) {
        if (reactions != null && reactions.getTotalCount() > 0) {
            updateView(mPlusOneView, reactions.getPlusOne());
            updateView(mMinusOneView, reactions.getMinusOne());
            updateView(mLaughView, reactions.getLaugh());
            updateView(mHoorayView, reactions.getHooray());
            updateView(mConfusedView, reactions.getConfused());
            updateView(mHeartView, reactions.getHeart());
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }

    public void setReactionDetailsProvider(ReactionDetailsProvider provider, Object item) {
        mProvider = provider;
        mReferenceItem = item;
        setClickable(provider != null);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        if (mPopup != null) {
            mPopup.dismiss();
        }
        return super.onSaveInstanceState();
    }

    @Override
    public boolean performClick() {
        // Touching the reaction bar will both dismiss the popup and cause performClick() to be
        // called (from a handler). In that case we don't want to show the popup again, which is
        // why we have this timeout in place.
        long timeSinceLastDismiss = System.currentTimeMillis() - mLastPopupDismissTime;
        if (mPopup != null) {
            mPopup.dismiss();
            return true;
        } else if (mProvider == null || timeSinceLastDismiss < 100) {
            return super.performClick();
        }

        mPopup = new ListPopupWindow(getContext());
        mPopup.setOnDismissListener(this);
        mPopup.setContentWidth(
                getResources().getDimensionPixelSize(R.dimen.reaction_details_popup_width));
        mPopup.setAnchorView(this);

        final ReactionDetailsAdapter adapter = new ReactionDetailsAdapter(getContext(), mPopup);
        mPopup.setAdapter(adapter);
        mPopup.show();

        new FetchReactionTask(mProvider, mReferenceItem) {
            @Override
            protected void onPostExecute(List<Reaction> reactions) {
                if (reactions != null) {
                    adapter.setReactions(reactions);
                } else {
                    mPopup.dismiss();
                }
            }
        }.execute();
        return true;
    }

    @Override
    public void onDismiss() {
        mPopup = null;
        mLastPopupDismissTime = System.currentTimeMillis();
    }

    private void updateView(TextView view, int count) {
        if (count > 0) {
            view.setText(String.valueOf(count));
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private static class ReactionDetailsAdapter extends BaseAdapter implements View.OnClickListener {
        private Context mContext;
        private ListPopupWindow mParent;
        private LayoutInflater mInflater;
        private List<Reaction> mReactions;
        private HashMap<String, Integer> mIconLookup = new HashMap<>();

        public ReactionDetailsAdapter(Context context, ListPopupWindow popup) {
            mContext = context;
            mParent = popup;
            mInflater = LayoutInflater.from(context);

            mIconLookup.put(Reaction.CONTENT_PLUS_ONE,
                    UiUtils.resolveDrawable(context, R.attr.reactionPlusOneIcon));
            mIconLookup.put(Reaction.CONTENT_MINUS_ONE,
                    UiUtils.resolveDrawable(context, R.attr.reactionMinusOneIcon));
            mIconLookup.put(Reaction.CONTENT_CONFUSED,
                    UiUtils.resolveDrawable(context, R.attr.reactionConfusedIcon));
            mIconLookup.put(Reaction.CONTENT_HEART,
                    UiUtils.resolveDrawable(context, R.attr.reactionHeartIcon));
            mIconLookup.put(Reaction.CONTENT_HOORAY,
                    UiUtils.resolveDrawable(context, R.attr.reactionHoorayIcon));
            mIconLookup.put(Reaction.CONTENT_LAUGH,
                    UiUtils.resolveDrawable(context, R.attr.reactionLaughIcon));
        }

        public void setReactions(List<Reaction> reactions) {
            mReactions = reactions;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mReactions != null ? mReactions.size() : 1;
        }

        @Override
        public int getItemViewType(int position) {
            return mReactions != null ? 0 : 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public Object getItem(int position) {
            return mReactions != null ? mReactions.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (mReactions == null) {
                return convertView != null
                        ? convertView
                        : mInflater.inflate(R.layout.reaction_details_progress, parent, false);
            }

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.row_reaction_details, parent, false);
            }

            Reaction reaction = mReactions.get(position);
            Reaction prevReaction = position == 0 ? null : mReactions.get(position - 1);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            ImageView avatar = (ImageView) convertView.findViewById(R.id.avatar);
            TextView name = (TextView) convertView.findViewById(R.id.name);

            if (prevReaction == null
                    || !TextUtils.equals(reaction.getContent(), prevReaction.getContent())) {
                icon.setImageResource(mIconLookup.get(reaction.getContent()));
            } else {
                icon.setImageDrawable(null);
            }
            AvatarHandler.assignAvatar(avatar, reaction.getUser());
            name.setText(ApiHelpers.getUserLogin(mContext, reaction.getUser()));
            convertView.setTag(reaction.getUser());
            convertView.setOnClickListener(this);

            return convertView;
        }

        @Override
        public void onClick(View view) {
            User user = (User) view.getTag();
            mParent.dismiss();
            mContext.startActivity(UserActivity.makeIntent(mContext, user));
        }
    }

    private static class FetchReactionTask extends AsyncTask<Void, Void, List<Reaction>> {
        private ReactionDetailsProvider mProvider;
        private Object mItem;

        public FetchReactionTask(ReactionDetailsProvider provider, Object item) {
            mProvider = provider;
            mItem = item;
        }

        @Override
        protected List<Reaction> doInBackground(Void... voids) {
            try {
                List<Reaction> reactions =
                        mProvider.loadReactionDetailsInBackground(mItem);
                Collections.sort(reactions, new Comparator<Reaction>() {
                    @Override
                    public int compare(Reaction lhs, Reaction rhs) {
                        int result = lhs.getContent().compareTo(rhs.getContent());
                        if (result == 0) {
                            result = rhs.getCreatedAt().compareTo(lhs.getCreatedAt());
                        }
                        return result;
                    }
                });
                return reactions;
            } catch (IOException e) {
                return null;
            }
        }
    }

    public static class AddReactionDialog extends AlertDialog implements
            DialogInterface.OnClickListener {
        private View mContentView;
        private ReactionDetailsProvider mProvider;
        private SparseIntArray mOldReactionIds = new SparseIntArray();
        private Object mItem;

        private static final @IdRes int[] VIEW_IDS = {
            R.id.plus_one, R.id.minus_one, R.id.laugh,
            R.id.hooray, R.id.heart, R.id.confused
        };
        private static final String[] CONTENTS = {
            Reaction.CONTENT_PLUS_ONE, Reaction.CONTENT_MINUS_ONE,
            Reaction.CONTENT_LAUGH, Reaction.CONTENT_HOORAY,
            Reaction.CONTENT_HEART, Reaction.CONTENT_CONFUSED
        };

        public AddReactionDialog(@NonNull Context context,
                ReactionDetailsProvider provider, Object item) {
            super(context);

            mProvider = provider;
            mItem = item;

            mContentView = View.inflate(context, R.layout.add_reaction_dialog, null);
            setView(mContentView);

            setButton(BUTTON_POSITIVE, context.getString(R.string.save), this);
            setButton(BUTTON_NEGATIVE, context.getString(R.string.cancel), this);
        }

        @Override
        protected void onStart() {
            super.onStart();

            mContentView.findViewById(R.id.progress).setVisibility(View.VISIBLE);
            mContentView.findViewById(R.id.action_container).setVisibility(View.GONE);

            new FetchReactionTask(mProvider, mItem) {
                @Override
                protected void onPostExecute(List<Reaction> reactions) {
                    if (reactions == null) {
                        dismiss();
                        return;
                    }
                    String ownLogin = Gh4Application.get().getAuthLogin();
                    for (Reaction reaction : reactions) {
                        if (!ApiHelpers.loginEquals(reaction.getUser(), ownLogin)) {
                            continue;
                        }
                        for (int i = 0; i < CONTENTS.length; i++) {
                            if (TextUtils.equals(CONTENTS[i], reaction.getContent())) {
                                final @IdRes int resId = VIEW_IDS[i];
                                ((CheckableImageView) mContentView.findViewById(resId)).setChecked(true);
                                mOldReactionIds.put(resId, reaction.getId());
                                break;
                            }
                        }
                    }

                    mContentView.findViewById(R.id.progress).setVisibility(View.GONE);
                    mContentView.findViewById(R.id.action_container).setVisibility(View.VISIBLE);
                }
            }.execute();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which != BUTTON_POSITIVE) {
                return;
            }

            final List<String> reactionsToAdd = new ArrayList<>();
            final List<Integer> reactionsToDelete = new ArrayList<>();

            for (int i = 0; i < VIEW_IDS.length; i++) {
                final @IdRes int resId = VIEW_IDS[i];
                final int oldReactionId = mOldReactionIds.get(resId);
                CheckableImageView view = (CheckableImageView) mContentView.findViewById(resId);
                if (view.isChecked() && oldReactionId == 0) {
                    reactionsToAdd.add(CONTENTS[i]);
                } else if (!view.isChecked() && oldReactionId != 0) {
                    reactionsToDelete.add(oldReactionId);
                }
            }

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    try {
                        GitHubClient client =
                                new DefaultClient("application/vnd.github.squirrel-girl-preview");
                        client.setOAuth2Token(Gh4Application.get().getAuthToken());

                        for (String content : reactionsToAdd) {
                            mProvider.addReactionInBackground(client, mItem, content);
                        }
                        ReactionService service = new ReactionService(client);
                        for (int reactionId : reactionsToDelete) {
                            service.deleteReaction(reactionId);
                        }
                    } catch (IOException e) {
                        android.util.Log.d("foo", "save fail", e);
                    }
                    return null;
                }
            }.execute();
        }
    }
}
