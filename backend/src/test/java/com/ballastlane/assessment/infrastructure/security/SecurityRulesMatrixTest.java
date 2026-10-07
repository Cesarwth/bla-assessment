package com.ballastlane.assessment.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.ballastlane.assessment.application.port.in.AuthenticateUserUseCase;
import com.ballastlane.assessment.application.port.in.ChangePasswordUseCase;
import com.ballastlane.assessment.application.port.in.GetProfileUseCase;
import com.ballastlane.assessment.application.port.in.RequestPasswordResetUseCase;
import com.ballastlane.assessment.application.port.in.ResetPasswordUseCase;
import com.ballastlane.assessment.application.port.in.UpdateProfileUseCase;
import com.ballastlane.assessment.application.port.in.DeleteStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.GetPokemonDetailUseCase;
import com.ballastlane.assessment.application.port.in.GetStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListPokemonUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonIdsUseCase;
import com.ballastlane.assessment.application.port.in.ListStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.in.RegisterUserUseCase;
import com.ballastlane.assessment.application.port.in.SyncSelectedPokemonUseCase;
import com.ballastlane.assessment.application.port.in.SyncPokemonUseCase;
import com.ballastlane.assessment.application.port.in.UpdateStoredPokemonUseCase;
import com.ballastlane.assessment.application.port.out.TokenIssuer;
import com.ballastlane.assessment.domain.Role;
import com.ballastlane.assessment.domain.User;
import com.ballastlane.assessment.infrastructure.web.AuthController;
import com.ballastlane.assessment.infrastructure.web.CollectionController;
import com.ballastlane.assessment.infrastructure.web.GlobalExceptionHandler;
import com.ballastlane.assessment.infrastructure.web.PokemonController;
import com.ballastlane.assessment.infrastructure.web.UserController;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The route rules of the whole API in one table: every (verb, path) against every kind of caller. It checks the
 * security layer only, so "REACHES_APP" means "not stopped by security" (the mocked app may answer 200, 400, 404...).
 * A route added without a security decision, or a rule that quietly opens a write path, fails here.
 */
@WebMvcTest({AuthController.class, PokemonController.class, CollectionController.class, UserController.class})
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtConfig.class, GlobalExceptionHandler.class})
class SecurityRulesMatrixTest {

    enum Caller { ANONYMOUS, USER, ADMIN }

    enum Outcome { UNAUTHORIZED_401, FORBIDDEN_403, REACHES_APP }

    @Autowired MockMvc mockMvc;
    @Autowired TokenIssuer tokens;
    @MockitoBean RegisterUserUseCase registerUser;
    @MockitoBean AuthenticateUserUseCase authenticateUser;
    @MockitoBean RequestPasswordResetUseCase requestPasswordReset;
    @MockitoBean ResetPasswordUseCase resetPassword;
    @MockitoBean GetProfileUseCase getProfile;
    @MockitoBean UpdateProfileUseCase updateProfile;
    @MockitoBean ChangePasswordUseCase changePassword;
    @MockitoBean ListPokemonUseCase listPokemon;
    @MockitoBean GetPokemonDetailUseCase getPokemonDetail;
    @MockitoBean ListStoredPokemonUseCase listStored;
    @MockitoBean GetStoredPokemonUseCase getStored;
    @MockitoBean SyncPokemonUseCase syncOne;
    @MockitoBean ListStoredPokemonIdsUseCase listIds;
    @MockitoBean SyncSelectedPokemonUseCase syncSelected;
    @MockitoBean UpdateStoredPokemonUseCase updateStored;
    @MockitoBean DeleteStoredPokemonUseCase deleteStored;

    private static void everyone(List<Arguments> rows, HttpMethod method, String path, Outcome anonymous, Outcome user,
            Outcome admin) {
        rows.add(Arguments.of(method, path, Caller.ANONYMOUS, anonymous));
        rows.add(Arguments.of(method, path, Caller.USER, user));
        rows.add(Arguments.of(method, path, Caller.ADMIN, admin));
    }

    private static void open(List<Arguments> rows, HttpMethod method, String path) {
        everyone(rows, method, path, Outcome.REACHES_APP, Outcome.REACHES_APP, Outcome.REACHES_APP);
    }

    private static void needsLogin(List<Arguments> rows, HttpMethod method, String path) {
        everyone(rows, method, path, Outcome.UNAUTHORIZED_401, Outcome.REACHES_APP, Outcome.REACHES_APP);
    }

    static Stream<Arguments> matrix() {
        List<Arguments> rows = new java.util.ArrayList<>();
        // public: auth and catalog reads
        open(rows, HttpMethod.POST, "/api/v1/auth/login");
        open(rows, HttpMethod.POST, "/api/v1/auth/register");
        open(rows, HttpMethod.POST, "/api/v1/auth/forgot-password");
        open(rows, HttpMethod.POST, "/api/v1/auth/reset-password");
        open(rows, HttpMethod.GET, "/api/v1/pokemon");
        open(rows, HttpMethod.GET, "/api/v1/pokemon?q=saur");
        open(rows, HttpMethod.GET, "/api/v1/pokemon/25");
        // the account is personal: every verb on it needs a token, for any role
        needsLogin(rows, HttpMethod.GET, "/api/v1/users/me");
        needsLogin(rows, HttpMethod.PATCH, "/api/v1/users/me");
        needsLogin(rows, HttpMethod.POST, "/api/v1/users/me/password");
        needsLogin(rows, HttpMethod.GET, "/api/v1/users/42"); // no other user's account is reachable by id
        // the collection is personal: every verb on it needs a token, for any role
        needsLogin(rows, HttpMethod.GET, "/api/v1/collection");
        needsLogin(rows, HttpMethod.GET, "/api/v1/collection/ids");
        needsLogin(rows, HttpMethod.GET, "/api/v1/collection/25");
        needsLogin(rows, HttpMethod.POST, "/api/v1/collection/pikachu/sync");
        needsLogin(rows, HttpMethod.POST, "/api/v1/collection/sync");
        needsLogin(rows, HttpMethod.PATCH, "/api/v1/collection/25");
        for (String deletePath : List.of("/api/v1/collection/25", "/api/v1/collection/25/")) {
            needsLogin(rows, HttpMethod.DELETE, deletePath);
        }
        // the public catalog is read-only: any write verb on it needs a token (and then simply is not supported)
        for (HttpMethod method : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            needsLogin(rows, method, "/api/v1/pokemon/25");
        }
        // default deny: unknown routes and case variants are not public by accident
        needsLogin(rows, HttpMethod.GET, "/api/v1/secret");
        needsLogin(rows, HttpMethod.DELETE, "/api/v1/Collection/25");
        needsLogin(rows, HttpMethod.GET, "/api/v1/authx"); // a lookalike prefix must not inherit the public auth rule
        return rows.stream();
    }

    private String authorizationFor(Caller caller) {
        if (caller == Caller.ANONYMOUS) {
            return null;
        }
        Role role = caller == Caller.ADMIN ? Role.ADMIN : Role.USER;
        return "Bearer " + tokens.issue(new User(1L, "someone", "someone@pallet.town", "hash", role)).jwt();
    }

    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @MethodSource("matrix")
    void routeRulesHold(HttpMethod method, String path, Caller caller, Outcome expected) throws Exception {
        var builder = request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}");
        String authorization = authorizationFor(caller);
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }

        int status = mockMvc.perform(builder).andReturn().getResponse().getStatus();

        switch (expected) {
            case UNAUTHORIZED_401 -> assertThat(status).isEqualTo(401);
            case FORBIDDEN_403 -> assertThat(status).isEqualTo(403);
            case REACHES_APP -> assertThat(status).isNotIn(401, 403);
        }
    }
}
