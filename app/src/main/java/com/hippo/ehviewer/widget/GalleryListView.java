/*
 * Copyright (C) 2014 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.hippo.ehviewer.AppHandler;
import com.hippo.ehviewer.ImageLoader;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.cache.ImageCache;
import com.hippo.ehviewer.cardview.CardViewSalon;
import com.hippo.ehviewer.data.GalleryInfo;
import com.hippo.ehviewer.ehclient.ListParser;
import com.hippo.ehviewer.util.Config;
import com.hippo.ehviewer.util.Ui;
import com.hippo.ehviewer.util.ViewUtils;
import com.hippo.ehviewer.widget.recyclerview.EasyRecyclerView;
import com.hippo.ehviewer.widget.recyclerview.MarginItemDecoration;
import com.hippo.ehviewer.widget.refreshlayout.RefreshLayout;

import java.util.ArrayList;
import java.util.List;

public class GalleryListView extends FrameLayout implements RefreshLayout.OnHeaderRefreshListener,
        RefreshLayout.OnFooterRefreshListener {

    private static final String TAG = GalleryListView.class.getSimpleName();

    public static final int LIST_MODE_DETAIL = 0;
    public static final int LIST_MODE_THUMB = 1;

    private static final int MODE_REFRESH = 0;
    private static final int MODE_NEXT_PAGE = 1;
    private static final int MODE_PRE_PAGE = 2;
    private static final int MODE_SOMEWHERE = 3;

    private Context mContext;
    private GalleryListViewHelper mHelper;

    private List<GalleryInfo> mGiList;

    private RefreshLayout mRefreshLayout;
    private EasyRecyclerView mEasyRecyclerView;
    private RefreshTextView mRefreshTextView;

    private int mListMode = LIST_MODE_DETAIL;

    private GalleryAdapter mAdapter;
    private StaggeredGridLayoutManager mLayoutManager;
    private MarginItemDecoration mItemDecoration;
    private final int[] mFirstPositionTemp = new int[50]; // TODO

    private long mTaskStamp;

    /**
     * First index of current page
     */
    private int mFirstIndex;
    /**
     * Last index of current page
     */
    private int mLastIndex;
    private int mCurPage;
    private int mFirstPage = 0;
    private int mLastPage = 0;
    /**
     * The number of page in sum
     */
    private int mPageNum;
    private int mItemPerPage;
    private int mGetMode;

    private OnGetListListener mListener;

    /**
     * If true, list will make showed item not changed after get
     */
    private boolean mIsKeepPosition;

    private int mTargetPage;
    private String mTargetUrl;
    private boolean isFootRefresh = false;

    private LayoutInflater mInflater;

    private int mListDetailThumbWidth;
    private int mListDetailThumbHeight;

    private int mOldPaddingBottom;

    public GalleryListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public GalleryListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public GalleryListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        mGiList = new ArrayList<>();
        mListener = new OnGetGalleryListListener();

        LayoutInflater.from(mContext).inflate(R.layout.gallery_list_view, this);

        mRefreshLayout = (RefreshLayout) findViewById(R.id.refresh_layout);
        mEasyRecyclerView = (EasyRecyclerView) mRefreshLayout.findViewById(R.id.easy_recycler_view);
        mRefreshTextView = (RefreshTextView) findViewById(R.id.refresh_text);

        mAdapter = new GalleryAdapter(mContext, mGiList);
        mLayoutManager = new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL);
        mItemDecoration = new MarginItemDecoration();
        mEasyRecyclerView.setOnScrollListener(new OnScrollListener());
        mEasyRecyclerView.setClipToPadding(false);
        mEasyRecyclerView.setAdapter(mAdapter);
        mEasyRecyclerView.setLayoutManager(mLayoutManager);
        mEasyRecyclerView.addItemDecoration(mItemDecoration);
        mEasyRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mEasyRecyclerView.setHasFixedSize(true);
        setItemMargin(Ui.dp2pix(8)); // TODO for tablet margin should be greater

        mRefreshTextView.setDefaultRefresh(R.string.click_retry, new RefreshTextView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                retry();
            }
        });

        mRefreshLayout.setHeaderEnable(true);
        mRefreshLayout.setFooterEnable(true);
        mRefreshLayout.setHeaderColorSchemeResources(
                R.color.refresh_color_1,
                R.color.refresh_color_2,
                R.color.refresh_color_3,
                R.color.refresh_color_4
        );
        mRefreshLayout.setFooterColorSchemeResources(
                R.color.refresh_color_1,
                R.color.refresh_color_2,
                R.color.refresh_color_3,
                R.color.refresh_color_4
        );
        mRefreshLayout.setOnHeaderRefreshListener(this);
        mRefreshLayout.setOnFooterRefreshListener(this);

        mOldPaddingBottom = mEasyRecyclerView.getPaddingBottom();

        // Caculate gallery detail height
        mInflater = LayoutInflater.from(mContext);
        View v = mInflater.inflate(R.layout.test_gallery_list_detail_thumb, null);
        ((TextView) v.findViewById(R.id.title)).setText("haha\nhaha");
        ((TextView) v.findViewById(R.id.uploader)).setText("haha");
        ((RatingView) v.findViewById(R.id.rate)).setRating(2.3f);
        ((TextView) v.findViewById(R.id.category)).setText("haha");
        ViewUtils.measureView(v, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mListDetailThumbHeight = Math.max(v.getMeasuredHeight(), Ui.dp2pix(120));
        mListDetailThumbWidth = mListDetailThumbHeight * 2 / 3;
    }

    public int getListMode() {
        return mListMode;
    }

    public void setListMode(int listMode) {
        if (mListMode != listMode) {
            mListMode = listMode;
            // Update span
            updateSpanCount();
            mAdapter.notifyDataSetChanged();
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static final String[] KEY_SPAN_COUNT_ARRAY = {
        "list_detail_columns_portrait", "list_detail_columns_landscape",
        "list_thumb_columns_portrait", "list_thumb_columns_landscape"
    };

    private static final int[] DEFAULT_SPAN_COUNT_ARRAY = {
        1, 2, 3, 5
    };

    public void updateSpanCount() {
        int index = mListMode * 2 + (isLandscape() ? 1 : 0);
        try {
            mLayoutManager.setSpanCount(Config.getInt(KEY_SPAN_COUNT_ARRAY[index],
                    DEFAULT_SPAN_COUNT_ARRAY[index]));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public EasyRecyclerView getEasyRecyclerView() {
        return mEasyRecyclerView;
    }

    public GalleryAdapter getGalleryAdapter() {
        return mAdapter;
    }

    public void setGalleryListViewHelper(GalleryListViewHelper h) {
        mHelper = h;
    }

    public void setPaddingTopAndBottom(int top, int bottom) {
        setPadding(getPaddingLeft(), top, getPaddingRight(), getPaddingBottom());
        mEasyRecyclerView.setPadding(mEasyRecyclerView.getPaddingLeft(), mEasyRecyclerView.getPaddingTop(),
                mEasyRecyclerView.getPaddingRight(), mOldPaddingBottom + bottom);
    }

    private void setItemMargin(int margin) {
        int halfMargin = margin / 2;
        mItemDecoration.setMargin(margin);
        mEasyRecyclerView.setPadding(halfMargin, halfMargin,
                halfMargin, halfMargin);
    }

    public void setEnabledHeader(boolean enabled) {
        mRefreshLayout.setHeaderEnable(enabled);
    }

    public void setEnabledFooter(boolean enabled) {
        mRefreshLayout.setFooterEnable(enabled);
    }

    public void setHeaderRefreshing(boolean refreshing) {
        mRefreshLayout.setHeaderRefreshing(refreshing);
    }

    public void setOnItemClickListener(EasyRecyclerView.OnItemClickListener l) {
        mEasyRecyclerView.setOnItemClickListener(l);
    }

    public void setOnItemLongClickListener(EasyRecyclerView.OnItemLongClickListener l) {
        mEasyRecyclerView.setOnItemLongClickListener(l);
    }

    public void setChoiceMode(int choiceMode) {
        mEasyRecyclerView.setChoiceMode(choiceMode);
    }

    public void setMultiChoiceModeListener(EasyRecyclerView.MultiChoiceModeListener listener) {
        mEasyRecyclerView.setMultiChoiceModeListener(listener);
    }

    public void addOnItemTouchListener(RecyclerView.OnItemTouchListener listener) {
        mEasyRecyclerView.addOnItemTouchListener(listener);
    }

    public void notifyDataSetChanged() {
        mAdapter.notifyDataSetChanged();
    }


    public void setNoneText(CharSequence text) {
        mRefreshTextView.setVisibility(View.VISIBLE);
        mRefreshTextView.setEmesg(text, false);
    }

    public void onlyShowList() {
        mRefreshLayout.setVisibility(View.VISIBLE);
        mRefreshTextView.setRefreshing(false);
        mRefreshTextView.setVisibility(View.GONE);
    }

    public void onlyShowNone() {
        mRefreshLayout.setVisibility(View.GONE);
        mRefreshTextView.setVisibility(View.VISIBLE);
        mRefreshTextView.setEmesg(R.string.none, false);
    }

    private void getGallerys() {
        setGallerysLayout();

        mTaskStamp = System.currentTimeMillis();
        mHelper.doGetGallerys(mTargetUrl, mTaskStamp, mListener);
    }

    @Override
    public void onHeaderRefresh() {
        // It is invokened by user pull, so no need to
        if (mFirstPage > 0) {
            getPrePage(true);
        } else {
            refresh();
        }
    }

    @Override
    public boolean onFooterRefresh() {
        if (!isRefreshing() && mLastPage < mPageNum - 1) {
            isFootRefresh = true;
            getNextPage(true);
            isFootRefresh = false;
            return true;
        } else {
            return false;
        }
    }

    public void firstTimeRefresh() {
        // set mPullViewGroup gone, make wait view show
        mRefreshLayout.setVisibility(View.GONE);
        mRefreshTextView.setVisibility(View.VISIBLE);
        mRefreshTextView.setRefreshing(true);

        refresh();
    }

    private void setGallerysLayout() {
        if (mRefreshLayout.getVisibility() == View.VISIBLE) {
            if (!isFootRefresh)
                mRefreshLayout.setHeaderRefreshing(true);
            mRefreshTextView.setVisibility(View.GONE);
        } else {
            mRefreshLayout.setVisibility(View.GONE);
            mRefreshTextView.setVisibility(View.VISIBLE);
            mRefreshTextView.setRefreshing(true);
        }
    }

    protected boolean retry() {
        // Need to update url, because mode may be changed
        mTargetUrl = mHelper.getTargetUrl(mTargetPage);
        getGallerys();
        return true;
    }

    /**
     * Go to page 0.<br>
     * You should know you can refresh or not.
     */
    public void refresh() {
        mGetMode = MODE_REFRESH;
        mTargetPage = 0;
        mTargetUrl = mHelper.getTargetUrl(mTargetPage);
        mIsKeepPosition = false;
        getGallerys();
    }

    /**
     * Get previous page.<br>
     * You should know you can get previous page or not
     *
     * @param isKeepPosition
     */
    protected void getPrePage(boolean isKeepPosition) {
        mGetMode = MODE_PRE_PAGE;
        mTargetPage = mFirstPage - 1;
        mTargetUrl = mHelper.getTargetUrl(mTargetPage);
        mIsKeepPosition = isKeepPosition;
        getGallerys();
    }

    /**
     * Get next page.<br>
     * You should know you can get next page or not
     *
     * @param isKeepPosition
     */
    protected void getNextPage(boolean isKeepPosition) {
        mGetMode = MODE_NEXT_PAGE;
        mTargetPage = mLastPage + 1;
        mTargetUrl = mHelper.getTargetUrl(mTargetPage);
        mIsKeepPosition = isKeepPosition;
        getGallerys();
    }

    /**
     * Get some page.<br>
     * You should know you can get that page or not
     *
     * @param page
     */
    protected void getSomewhere(int page) {
        mGetMode = MODE_SOMEWHERE;
        mTargetPage = page;
        mTargetUrl = mHelper.getTargetUrl(mTargetPage);
        mIsKeepPosition = false;
        getGallerys();
    }

    public void jumpTo(int page) {
        if (page >= mFirstPage && page <= mLastPage) {
            int position = (page - mFirstPage) * mItemPerPage;
            mLayoutManager.scrollToPosition(position);
        } else if (page == mFirstPage - 1) {
            getPrePage(false);
        } else if (page == mLastPage + 1) {
            getNextPage(false);
        } else {
            getSomewhere(page);
        }
    }

    /**
     * @return True if actionbar or footer is refreshing
     */
    public boolean isRefreshing() {
        return mRefreshLayout.isRefreshing();
    }

    public boolean isGetGalleryOk() {
        return mRefreshLayout.getVisibility() == View.VISIBLE;
    }

    public int getPageNum() {
        return mPageNum;
    }

    public int getCurPage() {
        return mCurPage;
    }

    public List<GalleryInfo> getGalleryList() {
        return mGiList;
    }

    public GalleryInfo getGalleryInfo(int position) {
        return mGiList.get(position);
    }

    public GalleryInfo removeGalleryInfo(int position) {
        return mGiList.remove(position);
    }

    public static interface OnGetListListener {
        public void onSuccess(long taskStamp, List<GalleryInfo> gis, int maxPage);
        public void onFailure(long taskStamp, String eMsg);
    }

    private class OnGetGalleryListListener implements OnGetListListener, Runnable {

        @Override
        public void run() {
            int preIndex = mLayoutManager.findFirstVisibleItemPositions(mFirstPositionTemp)[0] - 1;
            if (preIndex >= 0)
                mEasyRecyclerView.smoothScrollToPosition(preIndex);
        }

        public void smoothScrollToPrePosition() {
            AppHandler.getInstance().post(this);
        }

        @Override
        public void onSuccess(long taskStamp, List<GalleryInfo> gis, int pageNum) {
            if (mTaskStamp != taskStamp)
                return;

            if (pageNum == ListParser.TARGET_PAGE_IS_LAST)
                mPageNum = mTargetPage + 1;
            else
                mPageNum = pageNum;

            int itemPerPage = gis == null ? 0 : gis.size();
            if (mItemPerPage < itemPerPage)
                mItemPerPage = itemPerPage;

            if (mPageNum == 0) { // Get none
                onlyShowNone();

                mFirstPage = 0;
                mLastPage = 0;
                mGiList.clear();
                mAdapter.notifyDataSetChanged();
            } else {
                onlyShowList();

                int start;
                switch (mGetMode) {
                case MODE_REFRESH:
                    mFirstPage = 0;
                    mLastPage = 0;
                    mGiList.clear();
                    mGiList.addAll(gis);
                    mAdapter.notifyDataSetChanged();
                    // For current page
                    mFirstIndex = 0;
                    mLastIndex = gis.size() - 1;
                    mCurPage = 0;

                    mLayoutManager.scrollToPosition(0);
                    break;

                case MODE_PRE_PAGE:
                    mFirstPage--;
                    mGiList.addAll(0, gis);
                    mAdapter.notifyItemRangeInserted(0, gis.size());

                    if (mIsKeepPosition) {
                        mFirstIndex += gis.size();
                        mLastIndex += gis.size();
                        smoothScrollToPrePosition();
                    } else {
                        mFirstIndex = 0;
                        mLastIndex = gis.size()-1;
                        mCurPage = mTargetPage;
                        mLayoutManager.scrollToPosition(0);
                    }
                    break;

                case MODE_NEXT_PAGE:
                    mLastPage++;
                    start = mGiList.size();
                    mGiList.addAll(gis);
                    mAdapter.notifyItemRangeInserted(start, gis.size());

                    if (!mIsKeepPosition) {
                        mFirstIndex = mGiList.size() - gis.size();
                        mLastIndex = mGiList.size() - 1;
                        mCurPage = mTargetPage;
                        mLayoutManager.scrollToPosition(0);
                    }
                    break;

                case MODE_SOMEWHERE:
                    mFirstPage = mTargetPage;
                    mLastPage = mTargetPage;
                    mGiList.clear();
                    mGiList.addAll(gis);
                    mAdapter.notifyDataSetChanged();
                    // For current page
                    mFirstIndex = 0;
                    mLastIndex = gis.size() - 1;
                    mCurPage = mTargetPage;

                    mLayoutManager.scrollToPosition(0);
                }
            }

            mRefreshLayout.setHeaderRefreshing(false);
            mRefreshLayout.setFooterRefreshing(false);
        }

        @Override
        public void onFailure(long taskStamp, String eMsg) {
            if (mTaskStamp != taskStamp)
                return;

            switch (mGetMode) {
            case MODE_REFRESH:
            case MODE_SOMEWHERE:
                mRefreshLayout.setVisibility(View.GONE);
                mRefreshTextView.setVisibility(View.VISIBLE);
                if (eMsg.equals(mContext.getString(R.string.em_index_error))) {
                    mRefreshTextView.setEmesg(eMsg, mContext.getString(R.string.click_first_page),
                            new RefreshTextView.OnRefreshListener() {
                        @Override
                        public void onRefresh() {
                            mRefreshTextView.setRefreshing(true);
                            refresh();
                        }
                    });
                } else {
                    mRefreshTextView.setEmesg(eMsg, true);
                }
                break;
            default:
                mRefreshTextView.setRefreshing(false);
                mRefreshTextView.setVisibility(View.GONE);
                MaterialToast.showToast(eMsg);
            }
            mRefreshLayout.setHeaderRefreshing(false);
            mRefreshLayout.setFooterRefreshing(false);
        }
    }

    public static interface GalleryListViewHelper {
        /**
         * Get url string for very page
         *
         * @param targetPage
         * @return
         */
        public String getTargetUrl(int targetPage);

        /**
         * Do get gallarys here, you shuold invoke the onSuccess or onFailure of
         * listener when over
         *
         * @param url
         * @param taskStamp
         * @param listener
         */
        public void doGetGallerys(String url, long taskStamp, OnGetListListener listener);
    }

    public class OnScrollListener extends RecyclerView.OnScrollListener {

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            /*
            switch (newState) {
            case RecyclerView.SCROLL_STATE_DRAGGING:
                ImageCache.getImageCache(mContext).setPauseDiskCache(true);
                break;
            case RecyclerView.SCROLL_STATE_IDLE:
                ImageCache.getImageCache(mContext).setPauseDiskCache(false);
                break;
            }
            */
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            // mRefreshLayout.onScrolled(recyclerView, dx, dy);

            int itemCount = mLayoutManager.getChildCount();
            int firstVisibleItem = mLayoutManager.findFirstVisibleItemPositions(mFirstPositionTemp)[0];

            // itemCount might include some view can't be seen
            // Just check top line child and bottom line child
            int visibleItemCount = itemCount;
            int height = mEasyRecyclerView.getHeight();
            int span = mLayoutManager.getSpanCount();
            // Top line
            int start = 0;
            int end = Math.min(span, itemCount);
            for (int i = start; i < end; i++) {
                View view = mLayoutManager.getChildAt(i);
                if (view.getBottom() <= 0 || view.getTop() > height)
                    visibleItemCount--;
            }
            // Bottom line
            start = Math.max(itemCount - 1 - span, 0);
            end = itemCount;
            for (int i = start; i < end; i++) {
                View view = mLayoutManager.getChildAt(i);
                if (view.getBottom() <= 0 || view.getTop() > height)
                    visibleItemCount--;
            }

            if (mItemPerPage == 0)
                return;
            if (mLastIndex == 0)
                mLastIndex = mItemPerPage - 1;
            int pageChanged = (firstVisibleItem - mFirstIndex) / mItemPerPage;
            if (pageChanged == 0)
                pageChanged = (firstVisibleItem + visibleItemCount - mLastIndex - 1) / mItemPerPage;

            if (pageChanged != 0) {
                mCurPage = mCurPage + pageChanged;
                mFirstIndex += pageChanged * mItemPerPage;
                mLastIndex += pageChanged * mItemPerPage;
            }
        }
    }

    private class GalleryViewHolder extends RecyclerView.ViewHolder {

        public int viewType;
        public LoadImageView thumb;
        public TextView title;
        public TextView uploader;
        public TextView category;
        public RatingView rate;
        public TextView posted;
        public TextView simpleLanguage;

        public GalleryViewHolder(View itemView, int viewType) {
            super(itemView);

            this.viewType = viewType;
            switch (viewType) {
            case LIST_MODE_DETAIL:
                thumb = (LoadImageView) itemView.findViewById(R.id.thumb);
                title = (TextView) itemView.findViewById(R.id.title);
                category = (TextView) itemView.findViewById(R.id.category);
                uploader = (TextView) itemView.findViewById(R.id.uploader);
                rate = (RatingView) itemView.findViewById(R.id.rate);
                posted = (TextView) itemView.findViewById(R.id.posted);
                simpleLanguage = (TextView) itemView.findViewById(R.id.simple_language);

                ViewGroup.LayoutParams lp = thumb.getLayoutParams();
                lp.width = mListDetailThumbWidth;
                lp.height = mListDetailThumbHeight;
                break;
            case LIST_MODE_THUMB:
                thumb = (LoadImageView) itemView.findViewById(R.id.thumb);
                category = (TextView) itemView.findViewById(R.id.category);
                simpleLanguage = (TextView) itemView.findViewById(R.id.simple_language);
            }
        }
    }

    public class GalleryAdapter extends RecyclerView.Adapter<GalleryViewHolder> {

        private final Context mContext;
        private final List<GalleryInfo> mGiList;
        private final ImageLoader mImageLoader;


        public GalleryAdapter(Context context, List<GalleryInfo> gilist) {
            mContext = context;
            mGiList = gilist;
            mImageLoader = ImageLoader.getInstance(mContext);
        }

        @Override
        public GalleryViewHolder onCreateViewHolder(ViewGroup parent,
                int viewType) {
            int resId;
            if (viewType == LIST_MODE_DETAIL)
                resId = R.layout.gallery_list_detail_item;
            else
                resId = R.layout.gallery_list_thumb_item;
            View view = mInflater.inflate(resId, parent, false);
            CardViewSalon.reformWithShadow(view, new int[][]{
                    new int[]{android.R.attr.state_pressed},
                    new int[]{android.R.attr.state_activated},
                    new int[]{}},
                    new int[]{0xff84cae4, 0xff33b5e5, 0xFFFAFAFA}, null, false);
            return new GalleryViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(GalleryViewHolder holder,
                int position) {
            mEasyRecyclerView.checkItemCheckedState(holder.itemView, position);

            GalleryInfo gi = mGiList.get(position);
            final LoadImageView thumb = holder.thumb;
            final String key = String.valueOf(gi.gid);
            if (!key.equals(thumb.getKey())) {
                // Set new thumb
                thumb.setImageDrawable(null);
                thumb.setLoadInfo(gi.thumb, key);
                Bitmap b = ImageCache.getImageCache(getContext()).getFromMemory(key);
                if (b != null) {
                    thumb.setContextImage(b, true, false);
                } else {
                    mImageLoader.add(gi.thumb, String.valueOf(gi.gid),
                            new LoadImageView.SimpleImageGetListener(thumb).setFixScaleType(true));
                }
            }
            // Set simple language
            TextView simpleLanguage = holder.simpleLanguage;
            if (gi.simpleLanguage == null) {
                simpleLanguage.setVisibility(View.GONE);
            } else {
                simpleLanguage.setVisibility(View.VISIBLE);
                simpleLanguage.setText(gi.simpleLanguage);
            }

            // For detail mode
            if (holder.viewType == LIST_MODE_DETAIL) {
                // Set category
                TextView category = holder.category;
                String newText = Ui.getCategoryText(gi.category);
                if (!newText.equals(category.getText())) {
                    category.setText(newText);
                    category.setBackgroundColor(Ui.getCategoryColor(gi.category));
                }
                // Set manga title
                TextView title = holder.title;
                title.setText(gi.title);
                // Set uploder
                TextView uploader = holder.uploader;
                uploader.setText(gi.uploader);
                // Set star
                RatingView rate = holder.rate;
                rate.setRating(gi.rating);
                // set posted
                TextView posted = holder.posted;
                posted.setText(gi.posted);
            } else {
                // Set category
                TextView category = holder.category;
                String newText = Ui.getCategoryText(gi.category);
                if (!newText.equals(category.getText())) {
                    category.setText(newText);
                    category.setBackgroundColor(Ui.getCategoryColor(gi.category)
                            & 0x00ffffff | 0xca000000);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return mListMode;
        }

        @Override
        public int getItemCount() {
            return mGiList.size();
        }
    }
}
