package com.ngockhanh.clinic.accesscontrol.api.controller;

import com.ngockhanh.clinic.accesscontrol.api.http.SessionCookieFactory;
import com.ngockhanh.clinic.accesscontrol.api.request.LoginRequest;
import com.ngockhanh.clinic.accesscontrol.application.command.LoginCommand;
import com.ngockhanh.clinic.accesscontrol.application.command.LogoutCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.LoginResult;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LoginUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.LogoutUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
  private final LoginUseCase loginUseCase;
  private final LogoutUseCase logoutUseCase;
  private final SessionCookieFactory sessionCookies;

  /**
   * Signs in with username and password. On success the session ID is set only in the HttpOnly
   * session cookie and any previous session of this browser ends. All rejected credentials return
   * the same 401 response.
   *
   * @param request username and password exactly as entered
   * @param httpRequest request carrying the previous session cookie, if any
   * @return the authenticated principal
   */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<UserPrincipal>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    LoginResult result =
        loginUseCase.execute(
            LoginCommand.builder()
                .username(request.username())
                .password(request.password())
                .previousSessionId(sessionCookies.read(httpRequest))
                .correlationId(UUID.randomUUID())
                .build());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, sessionCookies.create(result.sessionId()).toString())
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.OK.value(), "Signed in", result.principal()));
  }

  /**
   * Returns the principal of the current session, for staff and patient accounts.
   *
   * @param principal authenticated principal of the session
   * @return the authenticated principal
   */
  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserPrincipal>> me(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.OK.value(), principal));
  }

  /**
   * Ends the current session. The session cookie is always cleared, even when the session has
   * already expired.
   *
   * @param httpRequest request carrying the session cookie, if any
   * @param httpResponse response that receives the clearing cookie
   * @return no content
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    // Clear the cookie before ending the session so it is cleared even if the store fails.
    httpResponse.addHeader(HttpHeaders.SET_COOKIE, sessionCookies.clear().toString());
    logoutUseCase.execute(
        LogoutCommand.builder()
            .sessionId(sessionCookies.read(httpRequest))
            .correlationId(UUID.randomUUID())
            .build());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
