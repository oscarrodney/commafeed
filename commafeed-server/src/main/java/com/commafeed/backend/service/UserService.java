package com.commafeed.backend.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.commafeed.backend.model.UserSettings;
import com.commafeed.frontend.model.Settings;
import com.commafeed.frontend.model.request.LoginRequest;
import com.commafeed.frontend.model.request.ProfileModificationRequest;
import com.commafeed.frontend.model.request.RegistrationRequest;
import com.commafeed.frontend.session.SessionHelper;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import org.apache.commons.lang3.StringUtils;

import com.commafeed.CommaFeedApplication;
import com.commafeed.CommaFeedConfiguration;
import com.commafeed.backend.Digests;
import com.commafeed.backend.dao.FeedCategoryDAO;
import com.commafeed.backend.dao.FeedSubscriptionDAO;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.dao.UserRoleDAO;
import com.commafeed.backend.dao.UserSettingsDAO;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserRole;
import com.commafeed.backend.model.UserRole.Role;
import com.commafeed.backend.service.internal.PostLoginActivities;
import com.google.common.base.Preconditions;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
public class UserService {

	private final FeedCategoryDAO feedCategoryDAO;
	private final FeedSubscriptionDAO feedSubscriptionDAO;
	private final UserDAO userDAO;
	private final UserRoleDAO userRoleDAO;
	private final UserSettingsDAO userSettingsDAO;

	private final PasswordEncryptionService encryptionService;
	private final CommaFeedConfiguration config;

	private final PostLoginActivities postLoginActivities;

	/**
	 * try to log in with given credentials
	 */
	public Optional<User> login(String nameOrEmail, String password) {
		if (nameOrEmail == null || password == null) {
			return Optional.empty();
		}

		User user = userDAO.findByName(nameOrEmail);
		if (user == null) {
			user = userDAO.findByEmail(nameOrEmail);
		}
		if (user != null && !user.isDisabled()) {
			boolean authenticated = encryptionService.authenticate(password, user.getPassword(), user.getSalt());
			if (authenticated) {
				performPostLoginActivities(user);
				return Optional.of(user);
			}
		}
		return Optional.empty();
	}

	/**
	 * try to log in with given api key
	 */
	public Optional<User> login(String apiKey) {
		if (apiKey == null) {
			return Optional.empty();
		}

		User user = userDAO.findByApiKey(apiKey);
		if (user != null && !user.isDisabled()) {
			performPostLoginActivities(user);
			return Optional.of(user);
		}
		return Optional.empty();
	}

	/**
	 * try to log in with given fever api key
	 */
	public Optional<User> login(long userId, String feverApiKey) {
		if (feverApiKey == null) {
			return Optional.empty();
		}

		User user = userDAO.findById(userId);
		if (user == null || user.isDisabled() || user.getApiKey() == null) {
			return Optional.empty();
		}

		String computedFeverApiKey = Digests.md5Hex(user.getName() + ":" + user.getApiKey());
		if (!computedFeverApiKey.equals(feverApiKey)) {
			return Optional.empty();
		}

		performPostLoginActivities(user);
		return Optional.of(user);
	}

	/**
	 * should triggers after successful login
	 */
	public void performPostLoginActivities(User user) {
		postLoginActivities.executeFor(user);
	}

	public User register(String name, String password, String email, Collection<Role> roles) {
		return register(name, password, email, roles, false);
	}

	public User register(String name, String password, String email, Collection<Role> roles, boolean forceRegistration) {

		if (!forceRegistration) {
			Preconditions.checkState(config.getApplicationSettings().getAllowRegistrations(),
					"Registrations are closed on this CommaFeed instance");
		}

		Preconditions.checkArgument(userDAO.findByName(name) == null, "Name already taken");
		if (StringUtils.isNotBlank(email)) {
			Preconditions.checkArgument(userDAO.findByEmail(email) == null, "Email already taken");
		}

		User user = new User();
		byte[] salt = encryptionService.generateSalt();
		user.setName(name);
		user.setEmail(email);
		user.setCreated(Instant.now());
		user.setSalt(salt);
		user.setPassword(encryptionService.getEncryptedPassword(password, salt));
		userDAO.saveOrUpdate(user);
		for (Role role : roles) {
			userRoleDAO.saveOrUpdate(new UserRole(user, role));
		}
		return user;
	}

	public void createAdminUser() {
		register(CommaFeedApplication.USERNAME_ADMIN, "admin", "admin@commafeed.com", Arrays.asList(Role.ADMIN, Role.USER), true);
	}

	public void createDemoUser() {
		register(CommaFeedApplication.USERNAME_DEMO, "demo", "demo@commafeed.com", Collections.singletonList(Role.USER), true);
	}

	public void unregister(User user) {
		userSettingsDAO.delete(userSettingsDAO.findByUser(user));
		userRoleDAO.delete(userRoleDAO.findAll(user));
		feedSubscriptionDAO.delete(feedSubscriptionDAO.findAll(user));
		feedCategoryDAO.delete(feedCategoryDAO.findAll(user));
		userDAO.delete(user);
	}

	public String generateApiKey(User user) {
		byte[] key = encryptionService.getEncryptedPassword(UUID.randomUUID().toString(), user.getSalt());
		return Digests.sha1Hex(key);
	}

	public Set<Role> getRoles(User user) {
		return userRoleDAO.findRoles(user);
	}

	public Settings getUserSettings(User user) {
		UserSettings settings = userSettingsDAO.findByUser(user);
		Settings s = new Settings();

		if (settings != null) {
			// Populate settings from UserSettings
			s.setReadingMode(settings.getReadingMode().name());
			s.setReadingOrder(settings.getReadingOrder().name());
			s.setShowRead(settings.isShowRead());
			s.getSharingSettings().setEmail(settings.isEmail());
			// other fields...
		} else {
			// Set default settings
			s.setReadingMode(UserSettings.ReadingMode.unread.name());
			s.setReadingOrder(UserSettings.ReadingOrder.desc.name());
			s.setShowRead(true);
			// other default fields...
		}

		return s;
	}

	public void saveUserSettings(User user, Settings settings) {
		UserSettings s = userSettingsDAO.findByUser(user);
		if (s == null) {
			s = new UserSettings();
			s.setUser(user);
		}
		s.setReadingMode(UserSettings.ReadingMode.valueOf(settings.getReadingMode()));
		s.setReadingOrder(UserSettings.ReadingOrder.valueOf(settings.getReadingOrder()));
		s.setShowRead(settings.isShowRead());
		s.setScrollMarks(settings.isScrollMarks());
		s.setCustomCss(settings.getCustomCss());
		s.setCustomJs(settings.getCustomJs());
		s.setLanguage(settings.getLanguage());
		s.setScrollSpeed(settings.getScrollSpeed());
		s.setScrollMode(UserSettings.ScrollMode.valueOf(settings.getScrollMode()));
		s.setStarIconDisplayMode(UserSettings.IconDisplayMode.valueOf(settings.getStarIconDisplayMode()));
		s.setExternalLinkIconDisplayMode(UserSettings.IconDisplayMode.valueOf(settings.getExternalLinkIconDisplayMode()));
		s.setMarkAllAsReadConfirmation(settings.isMarkAllAsReadConfirmation());
		s.setCustomContextMenu(settings.isCustomContextMenu());
		s.setMobileFooter(settings.isMobileFooter());

		s.setEmail(settings.getSharingSettings().isEmail());
		s.setGmail(settings.getSharingSettings().isGmail());
		s.setFacebook(settings.getSharingSettings().isFacebook());
		s.setTwitter(settings.getSharingSettings().isTwitter());
		s.setTumblr(settings.getSharingSettings().isTumblr());
		s.setPocket(settings.getSharingSettings().isPocket());
		s.setInstapaper(settings.getSharingSettings().isInstapaper());
		s.setBuffer(settings.getSharingSettings().isBuffer());

		userSettingsDAO.saveOrUpdate(s);
	}

	public void saveUserProfile(User user, ProfileModificationRequest request) {
		if (CommaFeedApplication.USERNAME_DEMO.equals(user.getName())) {
			throw new ForbiddenException("Cannot modify demo user");
		}

		Optional<User> login = login(user.getName(), request.getCurrentPassword());
		if (login.isEmpty()) {
			throw new BadRequestException("Invalid password");
		}

		String email = StringUtils.trimToNull(request.getEmail());
		if (StringUtils.isNotBlank(email)) {
			User u = userDAO.findByEmail(email);
			if (u != null && !user.getId().equals(u.getId())) {
				throw new BadRequestException("Email already taken");
			}
			user.setEmail(email);
		}

		if (StringUtils.isNotBlank(request.getNewPassword())) {
			byte[] password = encryptionService.getEncryptedPassword(request.getNewPassword(), user.getSalt());
			user.setPassword(password);
			user.setApiKey(generateApiKey(user));
		}

		if (request.isNewApiKey()) {
			user.setApiKey(generateApiKey(user));
		}

		userDAO.update(user);
	}

	public User registerUser(RegistrationRequest req, SessionHelper sessionHelper) {
		User registeredUser = register(req.getName(), req.getPassword(), req.getEmail(), Collections.singletonList(Role.USER));
		login(req.getName(), req.getPassword());
		sessionHelper.setLoggedInUser(registeredUser);
		return registeredUser;
	}

	public Optional<User> loginUser(LoginRequest req, SessionHelper sessionHelper) {
		Optional<User> user = login(req.getName(), req.getPassword());
		user.ifPresent(sessionHelper::setLoggedInUser);
		return user;
	}

}
