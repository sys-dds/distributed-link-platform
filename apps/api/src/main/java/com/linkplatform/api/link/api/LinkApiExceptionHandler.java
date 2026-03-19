package com.linkplatform.api.link.api;

import com.linkplatform.api.link.application.DuplicateLinkSlugException;
import com.linkplatform.api.link.application.LinkNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LinkApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(DuplicateLinkSlugException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSlug(DuplicateLinkSlugException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingSlug(LinkNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }
}
