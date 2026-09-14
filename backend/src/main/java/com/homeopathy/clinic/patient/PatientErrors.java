package com.homeopathy.clinic.patient;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = PatientController.class)
public class PatientErrors {
    @ExceptionHandler(PossibleDuplicateException.class)
    public ResponseEntity<ProblemDetail> duplicate(PossibleDuplicateException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
        problem.setProperty("code", "POSSIBLE_DUPLICATE");
        problem.setProperty("matches", error.getMatches());
        return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(problem);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Check the patient fields.");
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        problem.setProperty("errors", fields);
        return ResponseEntity.badRequest().body(problem);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> malformed() {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Invalid request. Dates must use YYYY-MM-DD."));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason()));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "The patient record conflicts with saved data. Reload and check for an existing patient before retrying."));
    }
}
