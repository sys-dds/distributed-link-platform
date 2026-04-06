package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.DuplicateLinkSlugException;
import com.linkplatform.api.link.application.LinkMutationConflictException;
import com.linkplatform.api.link.application.LinkNotFoundException;
import com.linkplatform.api.link.application.LinkPreconditionRequiredException;
import com.linkplatform.api.link.application.RedirectLookupUnavailableException;
import com.linkplatform.api.link.application.ReservedLinkSlugException;
import com.linkplatform.api.link.application.SelfTargetLinkException;
import com.linkplatform.api.owner.application.ApiKeyAuthenticationException;
import com.linkplatform.api.owner.application.ControlPlaneRateLimitExceededException;
import com.linkplatform.api.owner.application.OwnerQuotaExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LinkApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DuplicateLinkSlugException.class)
    public ProblemDetail handleDuplicateSlug(DuplicateLinkSlugException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(LinkMutationConflictException.class)
    public ProblemDetail handleMutationConflict(LinkMutationConflictException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
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
        return problemDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(LinkPreconditionRequiredException.class)
    public ProblemDetail handleMissingPrecondition(LinkPreconditionRequiredException exception) {
        return problemDetail(HttpStatus.PRECONDITION_REQUIRED, exception.getMessage());
    }

    @ExceptionHandler(RedirectLookupUnavailableException.class)
    public ProblemDetail handleRedirectLookupUnavailable(RedirectLookupUnavailableException exception) {
        return problemDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        return problemDetail;
    }
}
