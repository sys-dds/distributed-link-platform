package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.DuplicateLinkSlugException;
import com.linkplatform.api.link.application.LinkNotFoundException;
import com.linkplatform.api.link.application.ReservedLinkSlugException;
import com.linkplatform.api.link.application.SelfTargetLinkException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        return problemDetail;
    }
}
