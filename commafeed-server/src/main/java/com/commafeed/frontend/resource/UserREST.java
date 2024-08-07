package com.commafeed.frontend.resource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import jakarta.ws.rs.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.net.URIBuilder;

import com.codahale.metrics.annotation.Timed;
import com.commafeed.CommaFeedApplication;
import com.commafeed.CommaFeedConfiguration;
import com.commafeed.backend.Digests;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.dao.UserRoleDAO;
import com.commafeed.backend.dao.UserSettingsDAO;
import com.commafeed.backend.feed.FeedUtils;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserRole;
import com.commafeed.backend.model.UserRole.Role;
import com.commafeed.backend.model.UserSettings;
import com.commafeed.backend.model.UserSettings.IconDisplayMode;
import com.commafeed.backend.model.UserSettings.ReadingMode;
import com.commafeed.backend.model.UserSettings.ReadingOrder;
import com.commafeed.backend.model.UserSettings.ScrollMode;
import com.commafeed.backend.service.MailService;
import com.commafeed.backend.service.PasswordEncryptionService;
import com.commafeed.backend.service.UserService;
import com.commafeed.frontend.auth.SecurityCheck;
import com.commafeed.frontend.model.Settings;
import com.commafeed.frontend.model.UserModel;
import com.commafeed.frontend.model.request.LoginRequest;
import com.commafeed.frontend.model.request.PasswordResetRequest;
import com.commafeed.frontend.model.request.ProfileModificationRequest;
import com.commafeed.frontend.model.request.RegistrationRequest;
import com.commafeed.frontend.session.SessionHelper;
import com.google.common.base.Preconditions;

import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Tag(name = "Users")
public class UserREST {

	private final UserDAO userDAO;
	private final UserRoleDAO userRoleDAO;
	private final UserSettingsDAO userSettingsDAO;
	private final UserService userService;
	private final PasswordEncryptionService encryptionService;
	private final MailService mailService;
	private final CommaFeedConfiguration config;

	@Path("/settings")
	@GET
	@UnitOfWork
	@Operation(
			summary = "Retrieve user settings",
			description = "Retrieve user settings",
			responses = { @ApiResponse(content = @Content(schema = @Schema(implementation = Settings.class))) })
	@Timed
	public Response getUserSettings(@Parameter(hidden = true) @SecurityCheck User user) {
		Settings settings = userService.getUserSettings(user);
		return Response.ok(settings).build();
	}

	@Path("/settings")
	@POST
	@UnitOfWork
	@Operation(summary = "Save user settings", description = "Save user settings")
	@Timed
	public Response saveUserSettings(@Parameter(hidden = true) @SecurityCheck User user, @Parameter(required = true) Settings settings) {
		Preconditions.checkNotNull(settings);
		userService.saveUserSettings(user, settings);
		return Response.ok().build();
	}

	@Path("/profile")
	@GET
	@UnitOfWork
	@Operation(
			summary = "Retrieve user's profile",
			responses = { @ApiResponse(content = @Content(schema = @Schema(implementation = UserModel.class))) })
	@Timed
	public Response getUserProfile(@Parameter(hidden = true) @SecurityCheck User user) {
		UserModel userModel = new UserModel();
		userModel.setId(user.getId());
		userModel.setName(user.getName());
		userModel.setEmail(user.getEmail());
		userModel.setEnabled(!user.isDisabled());
		userModel.setApiKey(user.getApiKey());
		for (UserRole role : userRoleDAO.findAll(user)) {
			if (role.getRole() == Role.ADMIN) {
				userModel.setAdmin(true);
			}
		}
		return Response.ok(userModel).build();
	}

	@Path("/profile")
	@POST
	@UnitOfWork
	@Operation(summary = "Save user's profile")
	@Timed
	public Response saveUserProfile(@Parameter(hidden = true) @SecurityCheck User user,
			@Valid @Parameter(required = true) ProfileModificationRequest request) {
		try {
			userService.saveUserProfile(user, request);
			return Response.ok().build();
		} catch (ForbiddenException e) {
			return Response.status(Status.FORBIDDEN).build();
		} catch (BadRequestException e) {
			return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
		}
	}

	@Path("/register")
	@POST
	@UnitOfWork
	@Operation(summary = "Register a new account")
	@Timed
	public Response registerUser(@Valid @Parameter(required = true) RegistrationRequest req,
			@Context @Parameter(hidden = true) SessionHelper sessionHelper) {
		try {
			userService.registerUser(req, sessionHelper);
			return Response.ok().build();
		} catch (IllegalArgumentException e) {
			throw new BadRequestException(e.getMessage());
		}
	}

	@Path("/login")
	@POST
	@UnitOfWork
	@Operation(summary = "Login and create a session")
	@Timed
	public Response login(@Valid @Parameter(required = true) LoginRequest req,
			@Parameter(hidden = true) @Context SessionHelper sessionHelper) {
		Optional<User> user = userService.loginUser(req, sessionHelper);
		if (user.isPresent()) {
			return Response.ok().build();
		} else {
			return Response.status(Response.Status.UNAUTHORIZED).entity("wrong username or password").type(MediaType.TEXT_PLAIN).build();
		}
	}

	@Path("/passwordReset")
	@POST
	@UnitOfWork
	@Operation(summary = "send a password reset email")
	@Timed
	public Response sendPasswordReset(@Valid @Parameter(required = true) PasswordResetRequest req) {
		User user = userDAO.findByEmail(req.getEmail());
		if (user == null) {
			return Response.ok().build();
		}

		try {
			user.setRecoverPasswordToken(Digests.sha1Hex(UUID.randomUUID().toString()));
			user.setRecoverPasswordTokenDate(Instant.now());
			userDAO.saveOrUpdate(user);
			mailService.sendMail(user, "Password recovery", buildEmailContent(user));
			return Response.ok().build();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("could not send email").type(MediaType.TEXT_PLAIN).build();
		}
	}

	private String buildEmailContent(User user) throws Exception {
		String publicUrl = FeedUtils.removeTrailingSlash(config.getApplicationSettings().getPublicUrl());
		publicUrl += "/rest/user/passwordResetCallback";
		return String.format(
				"You asked for password recovery for account '%s', <a href='%s'>follow this link</a> to change your password. Ignore this if you didn't request a password recovery.",
				user.getName(), callbackUrl(user, publicUrl));
	}

	private String callbackUrl(User user, String publicUrl) throws Exception {
		return new URIBuilder(publicUrl).addParameter("email", user.getEmail())
				.addParameter("token", user.getRecoverPasswordToken())
				.build()
				.toURL()
				.toString();
	}

	@Path("/passwordResetCallback")
	@GET
	@UnitOfWork
	@Produces(MediaType.TEXT_HTML)
	@Timed
	public Response passwordRecoveryCallback(@Parameter(required = true) @QueryParam("email") String email,
			@Parameter(required = true) @QueryParam("token") String token) {
		Preconditions.checkNotNull(email);
		Preconditions.checkNotNull(token);

		User user = userDAO.findByEmail(email);
		if (user == null) {
			return Response.status(Status.UNAUTHORIZED).entity("Email not found.").build();
		}
		if (user.getRecoverPasswordToken() == null || !user.getRecoverPasswordToken().equals(token)) {
			return Response.status(Status.UNAUTHORIZED).entity("Invalid token.").build();
		}
		if (ChronoUnit.DAYS.between(user.getRecoverPasswordTokenDate(), Instant.now()) >= 2) {
			return Response.status(Status.UNAUTHORIZED).entity("token expired.").build();
		}

		String passwd = RandomStringUtils.randomAlphanumeric(10);
		byte[] encryptedPassword = encryptionService.getEncryptedPassword(passwd, user.getSalt());
		user.setPassword(encryptedPassword);
		if (StringUtils.isNotBlank(user.getApiKey())) {
			user.setApiKey(userService.generateApiKey(user));
		}
		user.setRecoverPasswordToken(null);
		user.setRecoverPasswordTokenDate(null);
		userDAO.saveOrUpdate(user);

		String message = "Your new password is: " + passwd;
		message += "<br />";
		message += String.format("<a href=\"%s\">Back to Homepage</a>", config.getApplicationSettings().getPublicUrl());
		return Response.ok(message).build();
	}

	@Path("/profile/deleteAccount")
	@POST
	@UnitOfWork
	@Operation(summary = "Delete the user account")
	@Timed
	public Response deleteUser(@Parameter(hidden = true) @SecurityCheck User user) {
		if (CommaFeedApplication.USERNAME_ADMIN.equals(user.getName()) || CommaFeedApplication.USERNAME_DEMO.equals(user.getName())) {
			return Response.status(Status.FORBIDDEN).build();
		}
		userService.unregister(userDAO.findById(user.getId()));
		return Response.ok().build();
	}

}
