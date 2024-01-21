package org.evento.demo.web.domain.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("CallToPrintStackTrace")
@ControllerAdvice
public class RestResponseEntityExceptionHandler 
  extends ResponseEntityExceptionHandler {

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
        } else if (ex.getMessage().startsWith("com.gualaclosures.iris.error.UnauthorizedException")){
            return handleExceptionInternal(ex, Map.of("message", last),
                    new HttpHeaders(), HttpStatus.FORBIDDEN, request);
        }else{
            return handleExceptionInternal(ex, ex,
                    new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
    }
}