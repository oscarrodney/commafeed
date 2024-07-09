package com.commafeed.backend.dao;

import com.commafeed.backend.feed.FeedEntryKeyword;
import com.commafeed.backend.model.FeedEntryStatus;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserSettings;

import java.time.Instant;
import java.util.List;

public class QueryParams {
    private User user;
    private FeedSubscription subscription;
    private boolean unreadOnly;
    private List<FeedEntryKeyword> keywords;
    private Instant newerThan;
    private int offset;
    private int limit;
    private UserSettings.ReadingOrder order;
    private FeedEntryStatus last;
    private String tag;
    private Long minEntryId;
    private Long maxEntryId;

    public QueryParams(User user, FeedSubscription sub, boolean unreadOnly, List<FeedEntryKeyword> keywords, Instant newerThan, int offset, int limit, UserSettings.ReadingOrder order, FeedEntryStatus last, String tag, Long minEntryId, Long maxEntryId) {
        this.user = user;
        this.subscription = sub;
        this.unreadOnly = unreadOnly;
        this.keywords = keywords;
        this.newerThan = newerThan;
        this.offset = offset;
        this.limit = limit;
        this.order = order;
        this.last = last;
        this.tag = tag;
        this.minEntryId = minEntryId;
        this.maxEntryId = maxEntryId;
        
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public FeedSubscription getSubscription() {
        return subscription;
    }

    public void setSubscription(FeedSubscription subscription) {
        this.subscription = subscription;
    }

    public boolean isUnreadOnly() {
        return unreadOnly;
    }

    public void setUnreadOnly(boolean unreadOnly) {
        this.unreadOnly = unreadOnly;
    }

    public List<FeedEntryKeyword> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<FeedEntryKeyword> keywords) {
        this.keywords = keywords;
    }

    public Instant getNewerThan() {
        return newerThan;
    }

    public void setNewerThan(Instant newerThan) {
        this.newerThan = newerThan;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public UserSettings.ReadingOrder getOrder() {
        return order;
    }

    public void setOrder(UserSettings.ReadingOrder order) {
        this.order = order;
    }

    public FeedEntryStatus getLast() {
        return last;
    }

    public void setLast(FeedEntryStatus last) {
        this.last = last;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Long getMinEntryId() {
        return minEntryId;
    }

    public void setMinEntryId(Long minEntryId) {
        this.minEntryId = minEntryId;
    }

    public Long getMaxEntryId() {
        return maxEntryId;
    }

    public void setMaxEntryId(Long maxEntryId) {
        this.maxEntryId = maxEntryId;
    }
}
