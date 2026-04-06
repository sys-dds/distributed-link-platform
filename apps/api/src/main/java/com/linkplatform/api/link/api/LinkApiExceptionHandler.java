package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.DuplicateLinkSlugException;
import com.linkplatform.api.link.application.LinkMutationConflictException;
import com.linkplatform.api.link.application.LinkNotFoundException;
import com.linkplatform.api.link.application.LinkPreconditionRequiredException;
import com.linkplatform.api.link.application.RedirectLookupUnavailableException;
import com.linkplatform.api.link.application.RedirectRateLimitExceededException;
import com.linkplatform.api.link.application.ReservedLinkSlugException;
import com.linkplatform.api.link.application.SelfTargetLinkException;
import com.linkplatform.api.owner.application.ApiKeyAuthenticationException;
import com.linkplatform.api.owner.application.ControlPlaneRateLimitExceededException;
import com.linkplatform.api.owner.application.OwnerQuotaExceededException;
import java.net.URI;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class LinkApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problemDetail.setProperty("category", "bad-request");
        return problemDetail;
    }

    @ExceptionHandler(DuplicateLinkSlugException.class)
    public ProblemDetail handleDuplicateSlug(DuplicateLinkSlugException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(LinkMutationConflictException.class)
    public ProblemDetail handleMutationConflict(LinkMutationConflictException exception) {
        ProblemDetail problemDetail = problemDetail(HttpStatus.CONFLICT, exception.getMessage());
        String detail = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        problemDetail.setProperty("category", detail.contains("version conflict") ? "stale-if-match" : "conflict");
        return problemDetail;
    }

    @ExceptionHandler(ApiKeyAuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleApiKeyAuthentication(ApiKeyAuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"link-platform\"")
                .body(problemDetail(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(ControlPlaneRateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(ControlPlaneRateLimitExceededException exception) {
        return problemDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    }

    @ExceptionHandler(OwnerQuotaExceededException.class)
    public ProblemDetail handleOwnerQuotaExceeded(OwnerQuotaExceededException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ReservedLinkSlugException.class)
    public ProblemDetail handleReservedSlug(ReservedLinkSlugException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(SelfTargetLinkException.class)
    public ProblemDetail handleSelfTarget(SelfTargetLinkException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ProblemDetail handleMissingSlug(LinkNotFoundException exception) {
        ProblemDetail problemDetail = problemDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problemDetail.setProperty("category", "not-found");
        return problemDetail;
    }

    @ExceptionHandler(LinkPreconditionRequiredException.class)
    public ProblemDetail handleMissingPrecondition(LinkPreconditionRequiredException exception) {
        ProblemDetail problemDetail = problemDetail(HttpStatus.PRECONDITION_REQUIRED, exception.getMessage());
        problemDetail.setProperty("category", "missing-if-match");
        return problemDetail;
    }

    @ExceptionHandler(RedirectLookupUnavailableException.class)
    public ProblemDetail handleRedirectLookupUnavailable(RedirectLookupUnavailableException exception) {
        return problemDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(RedirectRateLimitExceededException.class)
    public ProblemDetail handleRedirectRateLimitExceeded(RedirectRateLimitExceededException exception) {
        ProblemDetail problemDetail = problemDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        problemDetail.setProperty("category", "redirect-rate-limit");
        return problemDetail;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String detail = exception.getReason() == null || exception.getReason().isBlank()
                ? status.getReasonPhrase()
                : exception.getReason();
        return problemDetail(status, detail);
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleDataAccess(DataAccessException exception) {
        return problemDetail(HttpStatus.SERVICE_UNAVAILABLE, "Backend datastore temporarily unavailable");
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntimeException(RuntimeException exception) {
        return problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected runtime failure");
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create("about:blank"));
        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setProperty("code", status.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        return problemDetail;
    }
}
