package com.mjukvare.publicids.sample.web;

import com.mjukvare.publicids.InvalidPublicIdException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// The library leaves the status code to the application. Without this handler, a bad id would be a 500.
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidPublicIdException.class)
    ProblemDetail invalidPublicId(InvalidPublicIdException exception) {
        // The rejected text isn't echoed back: the client sent it, and it may be anything
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Not a valid " + exception.type().getSimpleName().toLowerCase() + " id");
    }
}
