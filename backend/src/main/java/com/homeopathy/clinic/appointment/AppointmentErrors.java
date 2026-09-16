package com.homeopathy.clinic.appointment;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@RestControllerAdvice(assignableTypes = AppointmentController.class)
public class AppointmentErrors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore()).body(ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> invalid() {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Check doctor, patient, date (YYYY-MM-DD), time (HH:mm), status and version."));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "The appointment changed or another booking is being saved. Reload and try again."));
    }
}
