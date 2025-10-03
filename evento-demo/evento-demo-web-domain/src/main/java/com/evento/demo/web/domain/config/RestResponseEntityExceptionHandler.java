package com.evento.demo.web.domain.config;

import com.evento.demo.api.error.InvalidCommandException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("CallToPrintStackTrace")
@ControllerAdvice
public class RestResponseEntityExceptionHandler 
  extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidCommandException.class)
    public ResponseEntity<Object> handleIllegalArgument(InvalidCommandException ex) {
        ex.printStackTrace();
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "InvalidCommandException");
        body.put("message", ex.getMessage());
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler()
    protected ResponseEntity<Object> handleConflict(
      Exception ex, WebRequest request) {
        var parts = ex.getMessage().split(": ");
        var last = parts[parts.length-1];
        if(ex instanceof CompletionException){
            ex.getCause().printStackTrace();
        }else if(ex instanceof ExecutionException){
            ex.getCause().printStackTrace();
        }else{
            ex.printStackTrace();
        }

        if(last.startsWith("error.")){
            return handleExceptionInternal(ex, Map.of("message", last),
                    new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
        } else if(ex instanceof NoSuchElementException || ex.getMessage().contains("No value present")){
            return handleExceptionInternal(ex, Map.of("message", "error.not.found"),
                    new HttpHeaders(), HttpStatus.NOT_FOUND, request);
        }else{
            return handleExceptionInternal(ex, ex,
                    new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
    }
}