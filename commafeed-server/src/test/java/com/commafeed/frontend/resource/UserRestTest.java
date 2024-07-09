package com.commafeed.frontend.resource;

import java.util.Collections;
import java.util.Optional;

import com.commafeed.CommaFeedConfiguration;
import com.commafeed.backend.dao.*;
import com.commafeed.backend.service.MailService;
import com.commafeed.backend.service.PasswordEncryptionService;
import com.commafeed.backend.service.internal.PostLoginActivities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserRole.Role;
import com.commafeed.backend.service.UserService;
import com.commafeed.frontend.model.request.LoginRequest;
import com.commafeed.frontend.model.request.RegistrationRequest;
import com.commafeed.frontend.session.SessionHelper;

import static org.mockito.Mockito.mock;

class UserRestTest {
	UserService service;
	RegistrationRequest req;
	UserDAO userDAO;
	UserRoleDAO userRoleDAO;
	UserSettingsDAO userSettingsDAO;
	PasswordEncryptionService passwordEncryptionService;
	MailService mailService;
	CommaFeedConfiguration config;
	@BeforeEach
	void setup() {
		// Mock the dependencies of UserService
		FeedCategoryDAO feedCategoryDAO = Mockito.mock(FeedCategoryDAO.class);
		FeedSubscriptionDAO feedSubscriptionDAO = Mockito.mock(FeedSubscriptionDAO.class);
		userDAO = Mockito.mock(UserDAO.class);
		userRoleDAO = Mockito.mock(UserRoleDAO.class);
		userSettingsDAO = Mockito.mock(UserSettingsDAO.class);
		passwordEncryptionService = Mockito.mock(PasswordEncryptionService.class);
		config = Mockito.mock(CommaFeedConfiguration.class);
		PostLoginActivities postLoginActivities = Mockito.mock(PostLoginActivities.class);
		mailService = Mockito.mock(MailService.class);

		// Create a spy for UserService with mocked dependencies
		service = Mockito.spy(new UserService(
				feedCategoryDAO,
				feedSubscriptionDAO,
				userDAO,
				userRoleDAO,
				userSettingsDAO,
				passwordEncryptionService,
				config,
				postLoginActivities
		));

		req = new RegistrationRequest();
		req.setName("user");
		req.setPassword("password");
		req.setEmail("test@test.com");
	}

	@Test
	void loginShouldNotPopulateHttpSessionIfUnsuccessfull() {
		// Absent user
		Optional<User> absentUser = Optional.empty();

		// Create UserService partial mock
		UserService service = mock(UserService.class);
		Mockito.when(service.login("user", "password")).thenReturn(absentUser);

		UserREST userREST = new UserREST(null, null, null, service, null, null, null);
		SessionHelper sessionHelper = mock(SessionHelper.class);

		LoginRequest req = new LoginRequest();
		req.setName("user");
		req.setPassword("password");

		userREST.login(req, sessionHelper);

		Mockito.verify(sessionHelper, Mockito.never()).setLoggedInUser(Mockito.any(User.class));
	}

	@Test
	void loginShouldPopulateHttpSessionIfSuccessfull() {
		// Create a user
		User user = new User();


		Mockito.when(service.login("user", "password")).thenReturn(Optional.of(user));

		LoginRequest loginRequest = new LoginRequest();
		loginRequest.setName("user");
		loginRequest.setPassword("password");

		UserREST userREST = new UserREST(null, null, null, service, null, null, null);
		SessionHelper sessionHelper = mock(SessionHelper.class);

		userREST.login(loginRequest, sessionHelper);

		Mockito.verify(sessionHelper).setLoggedInUser(user);
	}

	@Test
	void registerShouldRegisterAndThenLogin() {


		InOrder inOrder = Mockito.inOrder(service);

		SessionHelper sessionHelper = mock(SessionHelper.class);
		UserREST userREST = new UserREST(null, null, null, service, null, null, null);

		// Mock the register and login methods within the spy
		User user = new User();
		user.setName("user");
		Mockito.doReturn(user).when(service).register("user", "password", "test@test.com", Collections.singletonList(Role.USER));
		Mockito.doReturn(Optional.of(user)).when(service).login("user", "password");

		// Call the method under test
		userREST.registerUser(req, sessionHelper);

		// Verify the interactions
		inOrder.verify(service).register("user", "password", "test@test.com", Collections.singletonList(Role.USER));
		inOrder.verify(service).login("user", "password");

		// Verify that sessionHelper.setLoggedInUser was called
		Mockito.verify(sessionHelper).setLoggedInUser(user);
	}

	@Test
	void registerShouldPopulateHttpSession() {
		User user = new User();
		user.setName("user");

		// Mock the register and login methods within the spy
		Mockito.doReturn(user).when(service).register(Mockito.any(String.class), Mockito.any(String.class), Mockito.any(String.class),
				ArgumentMatchers.anyList());
		Mockito.doReturn(Optional.of(user)).when(service).login(Mockito.any(String.class), Mockito.any(String.class));

		SessionHelper sessionHelper = mock(SessionHelper.class);
		UserREST userREST = new UserREST(userDAO, userRoleDAO, userSettingsDAO, service, passwordEncryptionService, mailService, config);

		userREST.registerUser(req, sessionHelper);

		Mockito.verify(sessionHelper).setLoggedInUser(user);
	}

}
