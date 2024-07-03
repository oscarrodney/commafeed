package com.commafeed.backend.opml;

import java.io.StringReader;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.commafeed.backend.cache.CacheService;
import com.commafeed.backend.dao.FeedCategoryDAO;
import com.commafeed.backend.feed.FeedUtils;
import com.commafeed.backend.model.FeedCategory;
import com.commafeed.backend.model.User;
import com.commafeed.backend.service.FeedSubscriptionService;
import com.rometools.opml.feed.opml.Opml;
import com.rometools.opml.feed.opml.Outline;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.WireFeedInput;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
public class OPMLImporter {

	private final FeedCategoryDAO feedCategoryDAO;
	private final FeedSubscriptionService feedSubscriptionService;
	private final CacheService cache;

	public void importOpml(User user, String xml) throws IllegalArgumentException, FeedException {
		xml = xml.substring(xml.indexOf('<'));
		WireFeedInput input = new WireFeedInput();
		Opml feed = (Opml) input.build(new StringReader(xml));
		List<Outline> outlines = feed.getOutlines();
		for (int i = 0; i < outlines.size(); i++) {
			handleOutline(user, outlines.get(i), null, i);
		}
	}

	private void handleOutline(User user, Outline outline, FeedCategory parent, int position) {
		if (CollectionUtils.isNotEmpty(outline.getChildren())) {
			handleCategory(user, outline, parent, position);
		} else {
			handleSubscription(user, outline, parent, position);
		}
		cache.invalidateUserRootCategory(user);
	}

	private void handleCategory(User user, Outline outline, FeedCategory parent, int position) {
		String name = getValidName(outline);
		FeedCategory category = feedCategoryDAO.findByName(user, name, parent);

		if (category == null) {
			category = createCategory(user, name, parent, position);
			feedCategoryDAO.saveOrUpdate(category);
		}

		List<Outline> children = outline.getChildren();
		for (int i = 0; i < children.size(); i++) {
			handleOutline(user, children.get(i), category, i);
		}
	}

	private void handleSubscription(User user, Outline outline, FeedCategory parent, int position) {
		String name = getValidName(outline);
		if (StringUtils.isBlank(name)) {
			name = "Unnamed subscription";
		}

		try {
			feedSubscriptionService.subscribe(user, outline.getXmlUrl(), name, parent, position);
		} catch (Exception e) {
			log.error("Error while importing {}: {}", outline.getXmlUrl(), e.getMessage());
		}
	}

	private String getValidName(Outline outline) {
		String name = FeedUtils.truncate(outline.getText(), 128);
		if (name == null) {
			name = FeedUtils.truncate(outline.getTitle(), 128);
		}
		if (StringUtils.isBlank(name)) {
			name = "Unnamed category";
		}
		return name;
	}

	private FeedCategory createCategory(User user, String name, FeedCategory parent, int position) {
		FeedCategory category = new FeedCategory();
		category.setName(name);
		category.setParent(parent);
		category.setUser(user);
		category.setPosition(position);
		return category;
	}
}
